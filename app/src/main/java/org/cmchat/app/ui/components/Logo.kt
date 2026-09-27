package org.cmchat.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import org.cmchat.app.ui.theme.CmBlueGlow
import org.cmchat.app.ui.theme.CmRedGlow
import org.cmchat.app.ui.theme.Nunito
import androidx.compose.ui.text.font.FontWeight

@Composable
fun CmChatLogo(modifier: Modifier = Modifier, size: Int = 26) {
    val cyanGlow = Shadow(color = CmBlueGlow.copy(alpha = 0.55f), offset = Offset(0f, 0f), blurRadius = 22f)
    val redGlow  = Shadow(color = CmRedGlow.copy(alpha = 0.35f),  offset = Offset(0f, 0f), blurRadius = 14f)
    Box(modifier) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = CmBlueGlow, shadow = cyanGlow, fontWeight = FontWeight.Bold)) { append("CM-C") }
                withStyle(SpanStyle(color = CmRedGlow,  shadow = redGlow,  fontWeight = FontWeight.Bold)) { append("hat") }
            },
            fontFamily = Nunito,
            fontSize = size.sp,
        )
    }
}
