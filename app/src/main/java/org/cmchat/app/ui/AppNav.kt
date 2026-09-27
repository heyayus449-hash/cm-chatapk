package org.cmchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cmchat.app.ui.screens.CircleScreen
import org.cmchat.app.ui.screens.LockScreen
import org.cmchat.app.ui.theme.*

private sealed class Nav {
    object Lock : Nav()
    object Circle : Nav()
    data class Chat(val name: String) : Nav()
    object Settings : Nav()
}

@Composable
fun AppNav() {
    var nav by remember { mutableStateOf<Nav>(Nav.Lock) }
    when (val n = nav) {
        Nav.Lock -> LockScreen(onUnlock = { nav = Nav.Circle })
        Nav.Circle -> CircleScreen(
            onOpenChat = { nav = Nav.Chat(it.name) },
            onOpenSettings = { nav = Nav.Settings },
        )
        is Nav.Chat -> Placeholder("Chat: ${n.name}") { nav = Nav.Circle }
        Nav.Settings -> Placeholder("Settings") { nav = Nav.Circle }
    }
}

@Composable
private fun Placeholder(label: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(CmBackground).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("< Back", color = CmBlue, fontFamily = Nunito, fontSize = 16.sp,
            modifier = Modifier.align(Alignment.Start).clickable { onBack() })
        Spacer(Modifier.weight(1f))
        Text(label, color = CmText, fontFamily = Nunito, fontSize = 20.sp)
        Spacer(Modifier.weight(1f))
    }
}
