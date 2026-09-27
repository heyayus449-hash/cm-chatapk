package org.cmchat.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cmchat.app.ui.theme.*

@Composable
private fun CerberusEye(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width; val h = size.height
        drawOval(color = color, topLeft = Offset(0f, h * 0.22f),
            size = Size(w, h * 0.56f), style = Stroke(width = w * 0.09f))
        drawCircle(color = color, radius = w * 0.17f, center = Offset(w / 2, h / 2))
    }
}

@Composable
fun ChatScreen(name: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(CmBackground)) {
        Box(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("‹ Circle", color = CmBlue, fontFamily = Nunito, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.CenterStart).clickable { onBack() })
            Text(name, color = CmText, fontFamily = Nunito, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            Text("Erase", color = CmRed, fontFamily = Nunito, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterEnd))
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(CmGreen))
            Spacer(Modifier.width(6.dp))
            Text("steady", color = CmGreen, fontFamily = Nunito, fontSize = 14.sp)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, CmTextFaint, RoundedCornerShape(14.dp)),
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).padding(11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                CerberusEye(CmBlue)
                Spacer(Modifier.width(6.dp))
                Text("Cerberus 90m", color = CmBlue, fontFamily = Nunito, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.width(1.dp).height(22.dp).background(CmTextFaint))
            Box(Modifier.weight(1f).padding(11.dp), contentAlignment = Alignment.Center) {
                Text("Timer Off", color = CmTextDim, fontFamily = Nunito, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Bubble("hey, you there?", mine = false)
            Bubble("yeah — here", mine = true)
            OfflineBubble()
        }

        Row(Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(CmCard)
                .padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Message…", color = CmTextDim, fontFamily = Nunito, fontSize = 15.sp)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(44.dp).clip(CircleShape).background(CmBlue),
                contentAlignment = Alignment.Center) {
                Text("➤", color = CmBackground, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box(Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(16.dp))
            .background(if (mine) CmBlue.copy(alpha = 0.85f) else CmCard.copy(alpha = 0.85f))
            .padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text, color = if (mine) CmBackground else CmText,
                fontFamily = Nunito, fontSize = 15.sp)
        }
    }
}

@Composable
private fun OfflineBubble() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(Modifier.widthIn(max = 260.dp)
            .drawBehind {
                drawRoundRect(color = CmRed,
                    style = Stroke(width = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()))
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("Offline. Retry?", color = CmRed, fontFamily = Nunito, fontSize = 15.sp)
        }
    }
}
