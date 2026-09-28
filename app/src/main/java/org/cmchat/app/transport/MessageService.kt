package org.cmchat.app.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cmchat.app.crypto.CmId
import org.cmchat.app.crypto.CryptoManager
import org.cmchat.app.tor.ServerController
import org.cmchat.app.tor.TorService
import java.net.Socket

/**
 * Ties the onion accept loop and Tor SOCKS sender to the frame crypto.
 *
 * KNOCK is an anonymous sealed box to the recipient's identity key (sender not
 * yet known). Everything else is crypto_box between two known identities.
 * Anything that won't open is silently dropped.
 *
 * Actual delivery runs only with Tor ONLINE on a device; CI verifies compile
 * + the crypto round-trips.
 */
object MessageService {

    data class KnockRequest(val displayName: String, val cmId: String)

    private val _incomingKnocks = MutableStateFlow<List<KnockRequest>>(emptyList())
    val incomingKnocks: StateFlow<List<KnockRequest>> = _incomingKnocks.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var crypto: CryptoManager? = null
    private var myPub: String? = null
    private var mySec: String? = null
    private var myName: String = ""
    private var myCmId: String? = null

    /** cmId -> decoded peer (onion + identity pubkey). */
    private val contacts = mutableMapOf<String, org.cmchat.app.crypto.CmIdData>()

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

    fun sendKnock(cmId: String, onResult: (Boolean) -> Unit) {
        val c = crypto
        val myId = myCmId
        val target = CmId.decode(cmId)
        if (c == null || myId == null || target == null) { onResult(false); return }
        scope.launch {
            val ok = runCatching {
                val inner = framed(FrameType.KNOCK,
                    Messages.json.encodeToString(KnockPayload.serializer(),
                        KnockPayload(myName, myId)).toByteArray())
                val sealed = c.sealedSeal(inner, target.identityPubKeyHex)
                Transport.connectThroughTor(TorService.socksPort(), target.onion.removeSuffix(".onion"), 80)
                    .use { s -> Transport.writeFrame(s.getOutputStream(), sealed) }
                true
            }.getOrDefault(false)
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    fun acceptKnock(req: KnockRequest) {
        CmId.decode(req.cmId)?.let { contacts[req.cmId] = it }
        _incomingKnocks.value = _incomingKnocks.value.filterNot { it.cmId == req.cmId }
        // KNOCK_ACCEPT delivery is wired with the rest of chat (R3).
    }

    fun declineKnock(req: KnockRequest) {
        _incomingKnocks.value = _incomingKnocks.value.filterNot { it.cmId == req.cmId }
    }

    private fun handleIncoming(socket: Socket) {
        scope.launch {
            socket.use { s ->
                val sealed = Transport.readFrame(s.getInputStream()) ?: return@use
                val c = crypto ?: return@use
                val pub = myPub ?: return@use
                val sec = mySec ?: return@use
                // KNOCK: anonymous sealed box openable with my identity key only.
                val inner = c.sealedOpen(sealed, pub, sec) ?: return@use
                if (inner.isEmpty()) return@use
                val type = FrameType.fromCode(inner[0].toInt() and 0xff) ?: return@use
                if (type == FrameType.KNOCK) {
                    val kp = runCatching {
                        Messages.json.decodeFromString(
                            KnockPayload.serializer(), String(inner, 1, inner.size - 1))
                    }.getOrNull() ?: return@use
                    _incomingKnocks.value = _incomingKnocks.value + KnockRequest(kp.displayName, kp.cmId)
                }
            }
        }
    }

    private fun framed(type: FrameType, payload: ByteArray): ByteArray {
        val out = ByteArray(1 + payload.size)
        out[0] = type.code.toByte()
        payload.copyInto(out, 1)
        return out
    }
}
