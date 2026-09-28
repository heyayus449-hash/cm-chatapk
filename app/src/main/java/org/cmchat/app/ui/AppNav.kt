package org.cmchat.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import org.cmchat.app.crypto.CmId
import org.cmchat.app.tor.ServerController
import org.cmchat.app.tor.TorService
import org.cmchat.app.tor.TorStatus
import org.cmchat.app.transport.MessageService
import org.cmchat.app.ui.screens.ChatScreen
import org.cmchat.app.ui.screens.CircleScreen
import org.cmchat.app.ui.screens.Contact
import org.cmchat.app.ui.screens.KnockScreen
import org.cmchat.app.ui.screens.LockScreen
import org.cmchat.app.ui.screens.MyIdScreen
import org.cmchat.app.ui.screens.MyServerScreen
import org.cmchat.app.ui.screens.SettingsScreen
import org.cmchat.app.ui.screens.sampleCircle
import org.cmchat.app.vault.SecurityFactory
import org.cmchat.app.vault.VaultData

private sealed class Nav {
    object Lock : Nav()
    object Circle : Nav()
    data class Chat(val name: String, val cmId: String?) : Nav()
    object Settings : Nav()
    object MyServer : Nav()
    object MyId : Nav()
    object Knock : Nav()
}

private fun myCmId(data: VaultData?): String? {
    val face = data?.faces?.firstOrNull() ?: return null
    val onion = face.onionAddress ?: return null
    return CmId.encode(onion, face.publicKey)
}

@Composable
fun AppNav() {
    val context = LocalContext.current
    val manager = remember { SecurityFactory.create(context.filesDir) }

    var nav by remember { mutableStateOf<Nav>(Nav.Lock) }
    var data by remember { mutableStateOf<VaultData?>(null) }
    var pin by remember { mutableStateOf<String?>(null) }

    val torStatus by TorService.status.collectAsState()

    // Keep the message service configured with the active Face + contacts.
    LaunchedEffect(data) {
        val d = data ?: return@LaunchedEffect
        val face = d.faces.firstOrNull() ?: return@LaunchedEffect
        MessageService.configure(
            crypto = manager.crypto,
            myDisplayName = face.name,
            myIdentityPubHex = face.publicKey,
            myIdentitySecHex = face.secretKey,
            myCmId = myCmId(d),
            knownContactCmIds = d.contacts.mapNotNull { it.cmId },
        )
        // Persist an accepted knock as a contact in the vault.
        MessageService.onContactAccepted = accepted@{ req ->
            val p = pin ?: return@accepted
            val cur = data ?: return@accepted
            if (cur.contacts.none { it.cmId == req.cmId }) {
                val contact = org.cmchat.app.vault.ContactRec(
                    id = manager.crypto.randomHex(8),
                    name = req.displayName,
                    colorArgb = 0xFF6FB8D9,
                    faceId = face.id,
                    cmId = req.cmId,
                )
                val updated = cur.copy(contacts = cur.contacts + contact)
                runCatching { manager.save(p, updated) }
                data = updated
            }
        }
    }

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
                ?.map { Contact(it.name, Color(it.colorArgb), unread = false, cmId = it.cmId) }
                ?: sampleCircle
            CircleScreen(
                contacts = contacts,
                onOpenChat = { nav = Nav.Chat(it.name, it.cmId) },
                onOpenSettings = { nav = Nav.Settings },
                onKnock = { nav = Nav.Knock },
            )
        }
        is Nav.Chat -> ChatScreen(n.name, n.cmId) { nav = Nav.Circle }
        Nav.Settings -> SettingsScreen(
            onBack = { nav = Nav.Circle },
            onOpenMyServer = { nav = Nav.MyServer },
            onOpenMyId = { nav = Nav.MyId },
        )
        Nav.MyId -> MyIdScreen(cmId = myCmId(data), onBack = { nav = Nav.Settings })
        Nav.Knock -> KnockScreen(
            onSend = { cmId, _ ->
                MessageService.sendKnock(cmId) {}
                nav = Nav.Circle
            },
            onBack = { nav = Nav.Circle },
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
