package org.cmchat.app.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cmchat.app.chat.ChatStore
import org.cmchat.app.chat.MsgState
import org.cmchat.app.chat.SelfTimer
import org.cmchat.app.crypto.CmId
import org.cmchat.app.crypto.CmIdData
import org.cmchat.app.crypto.CryptoManager
import org.cmchat.app.tor.ServerController
import org.cmchat.app.tor.TorService
import java.net.Socket

/**
 * Frames over Tor. KNOCK is an anonymous sealed box (sender not yet known);
 * MSG/ACK/STATUS/ERASE_CHAT are crypto_box between two known identities.
 * Anything that won't open is dropped. Chats live only in [ChatStore] (RAM).
 *
 * Real delivery needs Tor ONLINE on a device; CI verifies compile + crypto.
 */
object MessageService {

    data class KnockRequest(val displayName: String, val cmId: String)

    private val _incomingKnocks = MutableStateFlow<List<KnockRequest>>(emptyList())
    val incomingKnocks: StateFlow<List<KnockRequest>> = _incomingKnocks.asStateFlow()

    /** Set by AppNav to persist an accepted contact into the vault. */
    @Volatile
    var onContactAccepted: ((KnockRequest) -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var crypto: CryptoManager? = null
    private var myPub: String? = null
    private var mySec: String? = null
    private var myName: String = ""
    private var myCmId: String? = null

    /** cmId -> decoded peer (onion + identity pubkey). */
    private val contacts = mutableMapOf<String, CmIdData>()

    fun configure(
        crypto: CryptoManager,
        myDisplayName: String,
        myIdentityPubHex: String,
        myIdentitySecHex: String,
        myCmId: String?,
        knownContactCmIds: List<String>,
    ) {
        this.crypto = crypto
        this.myName = myDisplayName
        this.myPub = myIdentityPubHex
        this.mySec = myIdentitySecHex
        this.myCmId = myCmId
        contacts.clear()
        knownContactCmIds.forEach { id -> CmId.decode(id)?.let { contacts[id] = it } }
        ServerController.onIncoming = { socket -> handleIncoming(socket) }
    }

    // ---- outgoing ----------------------------------------------------------

    fun sendKnock(cmId: String, onResult: (Boolean) -> Unit) {
        val c = crypto; val myId = myCmId; val target = CmId.decode(cmId)
        if (c == null || myId == null || target == null) { onResult(false); return }
        scope.launch {
            val ok = runCatching {
                val inner = framed(FrameType.KNOCK,
                    Messages.json.encodeToString(KnockPayload.serializer(), KnockPayload(myName, myId)).toByteArray())
                sendRaw(target, c.sealedSeal(inner, target.identityPubKeyHex))
                true
            }.getOrDefault(false)
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    /** Send a text message; updates [ChatStore] state to SENT or OFFLINE. */
    fun sendText(chatCmId: String, text: String, timer: SelfTimer) {
        val msg = ChatStore.addMine(chatCmId, text, timer)
        val c = crypto; val sec = mySec; val peer = contacts[chatCmId]
        if (c == null || sec == null || peer == null) {
            ChatStore.setState(chatCmId, msg.id, MsgState.OFFLINE); return
        }
        scope.launch {
            val ok = runCatching {
                val payload = Messages.json.encodeToString(TextPayload.serializer(),
                    TextPayload(msg.id, text, timer.label)).toByteArray()
                sendBox(c, sec, peer, FrameType.MSG, payload)
                true
            }.getOrDefault(false)
            ChatStore.setState(chatCmId, msg.id, if (ok) MsgState.SENT else MsgState.OFFLINE)
        }
    }

    fun retry(chatCmId: String, msgId: String, text: String, timer: SelfTimer) {
        val c = crypto; val sec = mySec; val peer = contacts[chatCmId] ?: return
        if (c == null || sec == null) return
        ChatStore.setState(chatCmId, msgId, MsgState.SENDING)
        scope.launch {
            val ok = runCatching {
                val payload = Messages.json.encodeToString(TextPayload.serializer(),
                    TextPayload(msgId, text, timer.label)).toByteArray()
                sendBox(c, sec, peer, FrameType.MSG, payload); true
            }.getOrDefault(false)
            ChatStore.setState(chatCmId, msgId, if (ok) MsgState.SENT else MsgState.OFFLINE)
        }
    }

    fun sendErase(chatCmId: String) {
        ChatStore.erase(chatCmId)
        val c = crypto; val sec = mySec; val peer = contacts[chatCmId] ?: return
        if (c == null || sec == null) return
        scope.launch { runCatching { sendBox(c, sec, peer, FrameType.ERASE_CHAT, ByteArray(0)) } }
    }

    fun sendStatus(word: String, colorArgb: Long) {
        val c = crypto ?: return; val sec = mySec ?: return
        val payload = Messages.json.encodeToString(StatusPayload.serializer(),
            StatusPayload(word, colorArgb)).toByteArray()
        contacts.values.forEach { peer ->
            scope.launch { runCatching { sendBox(c, sec, peer, FrameType.STATUS, payload) } }
        }
    }

    fun acceptKnock(req: KnockRequest) {
        CmId.decode(req.cmId)?.let { contacts[req.cmId] = it }
        _incomingKnocks.value = _incomingKnocks.value.filterNot { it.cmId == req.cmId }
        onContactAccepted?.invoke(req)
        // Tell them we accepted (crypto_box, now that we know their key).
        val c = crypto; val sec = mySec; val peer = contacts[req.cmId]
        val myId = myCmId
        if (c != null && sec != null && peer != null && myId != null) {
            scope.launch {
                runCatching {
                    val payload = Messages.json.encodeToString(KnockPayload.serializer(),
                        KnockPayload(myName, myId)).toByteArray()
                    sendBox(c, sec, peer, FrameType.KNOCK_ACCEPT, payload)
                }
            }
        }
    }

    fun declineKnock(req: KnockRequest) {
        _incomingKnocks.value = _incomingKnocks.value.filterNot { it.cmId == req.cmId }
    }

    // ---- incoming ----------------------------------------------------------

    private fun handleIncoming(socket: Socket) {
        scope.launch {
            socket.use { s ->
                val sealed = Transport.readFrame(s.getInputStream()) ?: return@use
                val c = crypto ?: return@use
                val pub = myPub ?: return@use
                val sec = mySec ?: return@use

                // 1) KNOCK: anonymous sealed box, openable with my key alone.
                c.sealedOpen(sealed, pub, sec)?.let { inner ->
                    dispatchAnonymous(inner); return@use
                }
                // 2) crypto_box from a known contact: try each contact's key.
                for ((cmId, peer) in contacts) {
                    val inner = c.boxOpen(sealed, peer.identityPubKeyHex, sec) ?: continue
                    dispatchFromContact(cmId, peer, inner)
                    return@use
                }
                // else: silently drop.
            }
        }
    }

    private fun dispatchAnonymous(inner: ByteArray) {
        if (inner.isEmpty()) return
        val type = FrameType.fromCode(inner[0].toInt() and 0xff) ?: return
        if (type == FrameType.KNOCK) {
            val kp = decodeKnock(inner) ?: return
            _incomingKnocks.value = _incomingKnocks.value + KnockRequest(kp.displayName, kp.cmId)
        }
    }

    private fun dispatchFromContact(chatCmId: String, peer: CmIdData, inner: ByteArray) {
        if (inner.isEmpty()) return
        val type = FrameType.fromCode(inner[0].toInt() and 0xff) ?: return
        val body = inner.copyOfRange(1, inner.size)
        when (type) {
            FrameType.MSG -> {
                val t = runCatching {
                    Messages.json.decodeFromString(TextPayload.serializer(), String(body))
                }.getOrNull() ?: return
                ChatStore.addTheirs(chatCmId, t.id, t.text, SelfTimer.fromLabel(t.selfTimer))
                sendAck(peer, t.id)
            }
            FrameType.ACK -> ChatStore.setState(chatCmId, String(body), MsgState.DELIVERED)
            FrameType.STATUS -> {
                val st = runCatching {
                    Messages.json.decodeFromString(StatusPayload.serializer(), String(body))
                }.getOrNull() ?: return
                ChatStore.setPeerStatus(chatCmId, st.word, st.colorArgb)
            }
            FrameType.ERASE_CHAT -> ChatStore.erase(chatCmId)
            FrameType.KNOCK_ACCEPT -> ChatStore.touchPeer(chatCmId)
            else -> {}
        }
    }

    private fun sendAck(peer: CmIdData, msgId: String) {
        val c = crypto ?: return; val sec = mySec ?: return
        scope.launch { runCatching { sendBox(c, sec, peer, FrameType.ACK, msgId.toByteArray()) } }
    }

    // ---- wire helpers ------------------------------------------------------

    private fun sendBox(c: CryptoManager, mySecHex: String, peer: CmIdData, type: FrameType, payload: ByteArray) {
        val sealed = c.boxSeal(framed(type, payload), peer.identityPubKeyHex, mySecHex)
        sendRaw(peer, sealed)
    }

    private fun sendRaw(peer: CmIdData, sealed: ByteArray) {
        Transport.connectThroughTor(TorService.socksPort(), peer.onion.removeSuffix(".onion"), 80)
            .use { s -> Transport.writeFrame(s.getOutputStream(), sealed) }
    }

    private fun decodeKnock(inner: ByteArray): KnockPayload? = runCatching {
        Messages.json.decodeFromString(KnockPayload.serializer(), String(inner, 1, inner.size - 1))
    }.getOrNull()

    private fun framed(type: FrameType, payload: ByteArray): ByteArray {
        val out = ByteArray(1 + payload.size)
        out[0] = type.code.toByte()
        payload.copyInto(out, 1)
        return out
    }
}
