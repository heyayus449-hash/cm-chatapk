package org.cmchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cmchat.app.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenMyServer: () -> Unit = {},
    onOpenMyId: () -> Unit = {},
    onWipeEverything: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    var textSize by remember { mutableStateOf(0f) }
    Column(Modifier.fillMaxSize().background(CmBackground)) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("‹ Back", color = CmBlue, fontFamily = Nunito, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.CenterStart).clickable { onBack() })
            Text("Settings", color = CmText, fontFamily = Nunito, fontSize = 17.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(CmCard).padding(14.dp)) {
                Text("Text Size", color = CmText, fontFamily = Nunito, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold)
                Slider(value = textSize, onValueChange = { textSize = it },
                    valueRange = -6f..6f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("−6", color = CmTextFaint, fontFamily = Nunito, fontSize = 11.sp)
                    Text("Default", color = CmTextFaint, fontFamily = Nunito, fontSize = 11.sp)
                    Text("+6", color = CmTextFaint, fontFamily = Nunito, fontSize = 11.sp)
                }
            }

            ToolToggle("Invisible mode (look offline)",
                org.cmchat.app.settings.AppSettings.invisibleMode)
            Setting("My Server", onClick = onOpenMyServer)
            Setting("Tag (Identity)")
            Setting("Circle")
            Setting("Cerberus · idle auto-wipe", "90 min")
            Setting("Kill Timer", "not armed")
            StayReachableRow()
            GeneralTimerRow()
            ToolToggle("Tool: Calculator", org.cmchat.app.tools.ToolsState.calcEnabled)
            ToolToggle("Tool: Notes", org.cmchat.app.tools.ToolsState.notesEnabled)
            ToolToggle("Tool: Flashlight", org.cmchat.app.tools.ToolsState.flashlightEnabled)
            Setting("My CMC-ID / QR", onClick = onOpenMyId)
            ToolToggle("Metadata scrub (strip EXIF/GPS)", org.cmchat.app.settings.AppSettings.metadataScrub)
            ToolToggle("Share my last-seen", org.cmchat.app.settings.AppSettings.shareLastSeen)
            BuzzFrequencyRow()
            ToolToggle("Let a Buzz reach me when closed",
                org.cmchat.app.settings.AppSettings.buzzListenerWhenClosed)
            ToolToggle("Show sender name on alerts",
                org.cmchat.app.settings.AppSettings.showBuzzSenderName)
            Setting("Panic PIN")
            Setting("Diagnostics", onClick = onOpenDiagnostics)
            Setting("Verify App Integrity")
            Setting("About / Version")
            Spacer(Modifier.height(4.dp))
        }

        Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .clip(RoundedCornerShape(14.dp)).background(CmCard).clickable { onExit() }.padding(14.dp),
            contentAlignment = Alignment.Center) {
            Text("Exit (stop server, clear RAM, log out)", color = CmText, fontFamily = Nunito,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(14.dp))
            .background(CmRed.copy(alpha = 0.15f)).clickable { onWipeEverything() }.padding(14.dp),
            contentAlignment = Alignment.Center) {
            Text("Wipe Everything Now", color = CmRed, fontFamily = Nunito,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StayReachableRow() {
    val on by org.cmchat.app.settings.AppSettings.stayReachable.collectAsState()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CmCard)
        .clickable {
            val now = !on
            org.cmchat.app.settings.AppSettings.stayReachable.value = now
            if (now) {
                // Staying reachable forces the auto-wipers off.
                org.cmchat.app.guard.GuardController.setCerberusArmed(false)
                org.cmchat.app.guard.GuardController.cancelKillTimer()
            }
        }
        .padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Stay reachable in background", color = CmText, fontFamily = Nunito, fontSize = 14.sp)
            Text("Keeps the server up after close (forces Cerberus + Kill off)",
                color = CmTextFaint, fontFamily = Nunito, fontSize = 11.sp)
        }
        Text(if (on) "On" else "Off", color = if (on) CmGreen else CmTextDim,
            fontFamily = Nunito, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GeneralTimerRow() {
    val t by org.cmchat.app.settings.AppSettings.generalTimer.collectAsState()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CmCard)
        .clickable {
            val all = org.cmchat.app.chat.SelfTimer.entries
            org.cmchat.app.settings.AppSettings.generalTimer.value = all[(t.ordinal + 1) % all.size]
        }
        .padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("General timer (all messages)", color = CmText, fontFamily = Nunito, fontSize = 14.sp,
            modifier = Modifier.weight(1f))
        Text(if (t == org.cmchat.app.chat.SelfTimer.OFF) "Off" else t.label,
            color = if (t == org.cmchat.app.chat.SelfTimer.OFF) CmTextDim else CmRed,
            fontFamily = Nunito, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BuzzFrequencyRow() {
    val freq by org.cmchat.app.buzz.BuzzPolicy.frequency.collectAsState()
    // Tap cycles through how often a person's buzzes are accepted.
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CmCard)
        .clickable {
            val all = org.cmchat.app.buzz.BuzzFrequency.entries
            org.cmchat.app.buzz.BuzzPolicy.frequency.value =
                all[(freq.ordinal + 1) % all.size]
        }
        .padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Accept Buzz", color = CmText, fontFamily = Nunito, fontSize = 14.sp,
            modifier = Modifier.weight(1f))
        Text(freq.label, color = CmBlue, fontFamily = Nunito, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ToolToggle(label: String, flow: kotlinx.coroutines.flow.MutableStateFlow<Boolean>) {
    val on by flow.collectAsState()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CmCard)
        .clickable { flow.value = !on }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = CmText, fontFamily = Nunito, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(if (on) "On" else "Off", color = if (on) CmGreen else CmTextDim,
            fontFamily = Nunito, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Setting(label: String, value: String = "", onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CmCard)
        .clickable { onClick() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = CmText, fontFamily = Nunito, fontSize = 14.sp,
            modifier = Modifier.weight(1f))
        if (value.isNotEmpty())
            Text(value, color = CmBlue, fontFamily = Nunito, fontSize = 13.sp)
    }
}
