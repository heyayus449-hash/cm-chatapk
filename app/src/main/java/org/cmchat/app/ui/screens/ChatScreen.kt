package org.cmchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import org.cmchat.app.chat.ChatMessage
import org.cmchat.app.chat.ChatStore
import org.cmchat.app.chat.LastSeen
import org.cmchat.app.chat.MsgState
import org.cmchat.app.chat.SelfTimer
import org.cmchat.app.transport.MessageService
import org.cmchat.app.ui.components.CerberusMark
import org.cmchat.app.ui.theme.*

@Composable
fun ChatScreen(contactName: String, chatCmId: String?, onBack: () -> Unit) {
    val chatId = chatCmId ?: contactName
    val threads by ChatStore.threads.collectAsState()
    val thread = threads[chatId] ?: org.cmchat.app.chat.ChatThread()

    val cerberusOn by org.cmchat.app.guard.GuardController.cerberusArmed.collectAsState()
    var input by remember { mutableStateOf("") }
    var selfTimer by remember { mutableStateOf(SelfTimer.OFF) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val retryReadyAt = remember { mutableStateMapOf<String, Long>() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); ChatStore.purgeExpired(now); delay(1000) }
    }

    // Mark this chat as the one on screen (so a message here doesn't also notify).
    DisposableEffect(chatCmId) {
        MessageService.activeChatCmId = chatCmId
        onDispose { if (MessageService.activeChatCmId == chatCmId) MessageService.activeChatCmId = null }
    }

    // Screen-shake when a BUZZ for this chat arrives (optional vibration too).
    val shakeX = remember { Animatable(0f) }
    LaunchedEffect(chatCmId) {
        org.cmchat.app.buzz.BuzzPolicy.shakes.collect { s ->
            if (s.chatCmId == chatCmId) {
                runCatching {
                    val vib = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                        vib?.vibrate(android.os.VibrationEffect.createOneShot(120, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    else @Suppress("DEPRECATION") vib?.vibrate(120)
                }
                repeat(4) {
                    shakeX.animateTo(16f, androidx.compose.animation.core.tween(50))
                    shakeX.animateTo(-16f, androidx.compose.animation.core.tween(50))
                }
                shakeX.animateTo(0f, androidx.compose.animation.core.tween(50))
            }
        }
    }

    Column(Modifier.fillMaxSize().background(CmBackground)
        .offset { IntOffset(shakeX.value.roundToInt(), 0) }) {
        Box(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("‹ Circle", color = CmBlue, fontFamily = Nunito, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.CenterStart).clickable { onBack() })
            Text(contactName, color = CmText, fontFamily = Nunito, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            Text("Erase", color = CmRed, fontFamily = Nunito, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterEnd).clickable {
                    if (chatCmId != null) MessageService.sendErase(chatCmId) else ChatStore.erase(chatId)
                })
        }

        // Peer status + last seen (never your own).
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            val status = thread.peerStatus
            if (status != null) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(thread.peerStatusColor)))
                Spacer(Modifier.width(6.dp))
                Text(status, color = CmTextDim, fontFamily = Nunito, fontSize = 13.sp)
                Spacer(Modifier.width(10.dp))
            }
            LastSeen.bucket(thread.peerLastSeen, now)?.let {
                Text(it, color = CmTextFaint, fontFamily = Nunito, fontSize = 12.sp)
            }
        }

        // Cerberus / Kill Timer bar — DISPLAY ONLY. Both are changed in Settings,
        // never from the chat.
        val killDeadline by org.cmchat.app.guard.GuardController.killDeadline.collectAsState()
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(14.dp))
            .border(1.dp, CmTextFaint, RoundedCornerShape(14.dp)),
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                CerberusMark(on = cerberusOn, sizeDp = 40)
                Spacer(Modifier.width(10.dp))
                Text(if (cerberusOn) "Cerberus 90m" else "Cerberus off",
                    color = if (cerberusOn) CmBlue else CmRed, fontFamily = Nunito,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.width(1.dp).height(40.dp).background(CmTextFaint))
            Box(Modifier.weight(1f).padding(11.dp), contentAlignment = Alignment.Center) {
                val killLabel = killDeadline?.let {
                    val secs = ((it - now) / 1000).coerceAtLeast(0)
                    "Kill ${secs / 3600}h${(secs % 3600) / 60}m"
                } ?: "Timer off"
                Text(killLabel,
                    color = if (killDeadline != null) CmRed else CmTextDim,
                    fontFamily = Nunito, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Team Hour line.
        thread.teamHour?.let {
            Text("Team Hour: $it", color = CmBlue, fontFamily = Nunito, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp, start = 16.dp))
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (thread.messages.isEmpty()) {
                Text("No messages yet.", color = CmTextFaint, fontFamily = Nunito, fontSize = 13.sp)
            }
            for (m in thread.messages) {
                when {
                    m.system -> Text(m.text, color = CmTextFaint, fontFamily = Nunito, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    m.mine && m.state == MsgState.OFFLINE -> OfflineBubble(m, now, retryReadyAt[m.id]) {
                        val ready = retryReadyAt[m.id]?.let { now >= it } ?: true
                        if (ready && chatCmId != null) {
                            MessageService.retry(chatCmId, m.id, m.text, m.selfTimer)
                            retryReadyAt[m.id] = now + 30_000L
                        }
                    }
                    else -> Bubble(m)
                }
            }
        }

        // Self-timer chip + Buzz.
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Self-timer:", color = CmTextDim, fontFamily = Nunito, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            for (t in SelfTimer.entries) {
                val sel = t == selfTimer
                Box(Modifier.padding(end = 6.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (sel) CmBlue else CmCard).clickable { selfTimer = t }
                    .padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(t.label, color = if (sel) CmBackground else CmTextDim,
                        fontFamily = Nunito, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            val buzzLeft = chatCmId?.let { org.cmchat.app.buzz.BuzzPolicy.sendCooldownRemaining(it, now) } ?: 0L
            Box(Modifier.clip(RoundedCornerShape(10.dp))
                .background(if (buzzLeft > 0) CmCard else CmOrange)
                .clickable(enabled = buzzLeft <= 0 && chatCmId != null) {
                    if (chatCmId != null) MessageService.sendBuzz(chatCmId)
                }
                .padding(horizontal = 12.dp, vertical = 5.dp)) {
                Text(if (buzzLeft > 0) "Buzz ${buzzLeft}s" else "⚡ Buzz",
                    color = if (buzzLeft > 0) CmTextDim else CmBackground,
                    fontFamily = Nunito, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(CmCard)
                .padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (input.isEmpty()) Text("Message…", color = CmTextDim, fontFamily = Nunito, fontSize = 15.sp)
                BasicTextField(
                    value = input, onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(color = CmText, fontFamily = Nunito, fontSize = 15.sp),
                    cursorBrush = SolidColor(CmBlue),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(44.dp).clip(CircleShape).background(CmBlue).clickable {
                val text = input.trim()
                if (text.isNotEmpty()) {
                    if (chatCmId != null) MessageService.sendText(chatCmId, text, selfTimer)
                    else ChatStore.addMine(chatId, text, selfTimer)
                    input = ""
                }
            }, contentAlignment = Alignment.Center) {
                Text("➤", color = CmBackground, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start) {
            Box(Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(16.dp))
                .background(if (m.mine) CmBlue.copy(alpha = 0.85f) else CmCard.copy(alpha = 0.85f))
                .padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(m.text, color = if (m.mine) CmBackground else CmText, fontFamily = Nunito, fontSize = 15.sp)
            }
            if (m.mine) {
                val label = when (m.state) {
                    MsgState.SENDING -> "sending…"; MsgState.SENT -> "sent"
                    MsgState.DELIVERED -> "delivered"; MsgState.OFFLINE -> "offline"
                }
                Text(label, color = CmTextFaint, fontFamily = Nunito, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp))
            }
        }
    }
}

@Composable
private fun OfflineBubble(m: ChatMessage, now: Long, readyAt: Long?, onRetry: () -> Unit) {
    val remaining = readyAt?.let { ((it - now) / 1000).coerceAtLeast(0) } ?: 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(16.dp))
            .background(CmRed.copy(alpha = 0.10f)).border(1.5.dp, CmRed, RoundedCornerShape(16.dp))
            .clickable { onRetry() }.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                if (remaining > 0) "Offline. Retry in ${remaining}s" else "Offline. Retry?",
                color = CmRed, fontFamily = Nunito, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
