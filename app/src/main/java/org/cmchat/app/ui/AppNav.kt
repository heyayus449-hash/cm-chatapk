package org.cmchat.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import org.cmchat.app.tor.ServerController
import org.cmchat.app.tor.TorService
import org.cmchat.app.tor.TorStatus
import org.cmchat.app.ui.screens.ChatScreen
import org.cmchat.app.ui.screens.CircleScreen
import org.cmchat.app.ui.screens.Contact
import org.cmchat.app.ui.screens.LockScreen
import org.cmchat.app.ui.screens.MyServerScreen
import org.cmchat.app.ui.screens.SettingsScreen
import org.cmchat.app.ui.screens.sampleCircle
import org.cmchat.app.vault.SecurityFactory
import org.cmchat.app.vault.VaultData

private sealed class Nav {
    object Lock : Nav()
    object Circle : Nav()
    data class Chat(val name: String) : Nav()
    object Settings : Nav()
    object MyServer : Nav()
}

@Composable
fun AppNav() {
    val context = LocalContext.current
    val manager = remember { SecurityFactory.create(context.filesDir) }

    var nav by remember { mutableStateOf<Nav>(Nav.Lock) }
    var data by remember { mutableStateOf<VaultData?>(null) }
    var pin by remember { mutableStateOf<String?>(null) }

    val torStatus by TorService.status.collectAsState()

    // Once Tor is ONLINE, publish the active Face's onion service. If Tor
    // generated a fresh onion key, persist it back into the vault.
    LaunchedEffect(torStatus, data) {
        val d = data ?: return@LaunchedEffect
        val p = pin ?: return@LaunchedEffect
        val face = d.faces.firstOrNull() ?: return@LaunchedEffect
        if (torStatus is TorStatus.Online) {
            ServerController.start(face.name, face.onionKey) { pub ->
                if (pub.newPrivateKey != null && face.onionKey == null) {
                    val updated = d.copy(
                        faces = d.faces.map {
                            if (it.id == face.id)
                                it.copy(onionKey = pub.newPrivateKey, onionAddress = pub.onion)
                            else it
                        }
                    )
                    runCatching { manager.save(p, updated) }
                    data = updated
                }
            }
        }
    }

    when (val n = nav) {
        Nav.Lock -> LockScreen(manager) { enteredPin, unlocked ->
            pin = enteredPin
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
        Nav.Settings -> SettingsScreen(
            onBack = { nav = Nav.Circle },
            onOpenMyServer = { nav = Nav.MyServer },
        )
        Nav.MyServer -> {
            val face = data?.faces?.firstOrNull()
            MyServerScreen(
                onStart = {
                    if (face != null) ServerController.start(face.name, face.onionKey) {}
                },
                onStop = { ServerController.stop() },
                onRestart = {
                    if (face != null) ServerController.restart(face.name, face.onionKey) {}
                },
                onBack = { nav = Nav.Settings },
            )
        }
    }
}
