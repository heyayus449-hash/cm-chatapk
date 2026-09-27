package org.cmchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cmchat.app.ui.components.CmChatLogo
import org.cmchat.app.ui.theme.*

@Composable
fun LockScreen(onUnlock: () -> Unit = {}) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(CmBackground).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(70.dp))
        CmChatLogo(size = 30)
        Spacer(Modifier.height(10.dp))
        Text("Face: Wanderer", color = CmTextDim, fontFamily = Nunito, fontSize = 14.sp)

        Spacer(Modifier.height(40.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(6) { i ->
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(if (i < pin.length) CmBlue else CmCard)
                )
            }
        }

        Spacer(Modifier.height(48.dp))
        val keys = listOf("1","2","3","4","5","6","7","8","9","","0","<")
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for (row in 0..3) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (col in 0..2) {
                        val k = keys[row * 3 + col]
                        Box(
                            Modifier.size(80.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (k == "") CmBackground else CmCard)
                                .then(
                                    if (k == "") Modifier
                                    else Modifier.clickable {
                                        when (k) {
                                            "<" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                            else -> if (pin.length < 6) pin += k
                                        }
                                        if (pin.length == 6) { onUnlock(); pin = "" }
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (k.isNotEmpty())
                                Text(k, color = CmText, fontFamily = Nunito, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text("Ghost mode ready", color = CmGreen, fontFamily = Nunito, fontSize = 15.sp)
        Spacer(Modifier.height(20.dp))
    }
}
