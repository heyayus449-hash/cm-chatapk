package org.cmchat.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import org.cmchat.app.ui.screens.ChatScreen
import org.cmchat.app.ui.screens.CircleScreen
import org.cmchat.app.ui.screens.Contact
import org.cmchat.app.ui.screens.LockScreen
import org.cmchat.app.ui.screens.SettingsScreen
import org.cmchat.app.ui.screens.sampleCircle
import org.cmchat.app.tor.TorService
import org.cmchat.app.vault.SecurityFactory
import org.cmchat.app.vault.VaultData

private sealed class Nav {
    object Lock : Nav()
    object Circle : Nav()
    data class Chat(val name: String) : Nav()
    object Settings : Nav()
}

@Composable
fun AppNav() {
    val context = LocalContext.current
    val manager = remember { SecurityFactory.create(context.filesDir) }

    var nav by remember { mutableStateOf<Nav>(Nav.Lock) }
    var data by remember { mutableStateOf<VaultData?>(null) }

    when (val n = nav) {
        Nav.Lock -> LockScreen(manager) { unlocked ->
            data = unlocked
            TorService.start(context)
            nav = Nav.Circle
        }
        Nav.Circle -> {
            val contacts = data?.contacts?.takeIf { it.isNotEmpty() }
                ?.map { Contact(it.name, Color(it.colorArgb), unread = false) }
                ?: sampleCircle
            CircleScreen(
                contacts = contacts,
                onOpenChat = { nav = Nav.Chat(it.name) },
                onOpenSettings = { nav = Nav.Settings },
            )
        }
        is Nav.Chat -> ChatScreen(n.name) { nav = Nav.Circle }
        Nav.Settings -> SettingsScreen { nav = Nav.Circle }
    }
}
