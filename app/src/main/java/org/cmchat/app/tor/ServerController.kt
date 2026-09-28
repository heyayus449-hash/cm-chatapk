package org.cmchat.app.tor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cmchat.app.transport.Transport
import java.net.ServerSocket

/** Onion service (my "server") state for the active Face. */
sealed interface ServerStatus {
    data object Off : ServerStatus
    data object Starting : ServerStatus
    data class Online(val onion: String, val faceName: String, val sinceMs: Long) : ServerStatus
    data class Failed(val reason: String) : ServerStatus
}

/** Result of publishing an onion, so the caller can persist a newly-made key. */
data class OnionPublish(val onion: String, val newPrivateKey: String?)

/**
 * Publishes a v3 onion service (virtual port 80 -> a random loopback
 * ServerSocket) for the active Face via the Tor control port, and tracks its
 * state. If the Face has no onion key yet, Tor generates one (ADD_ONION
 * NEW:ED25519-V3) and it is returned so the caller can store it in the vault.
 *
 * Runtime behaviour (actual onion publishing) requires Tor ONLINE on a device;
 * this is compile-verified only in CI.
 */
object ServerController {

    private val _status = MutableStateFlow<ServerStatus>(ServerStatus.Off)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var currentServiceId: String? = null

    /** Set by MessageService: handles each accepted incoming connection. */
    @Volatile
    var onIncoming: ((java.net.Socket) -> Unit)? = null

    /**
     * @param existingOnionKey the Face's stored "ED25519-V3:..." key, or null
     * @param onPublished called with the onion + any freshly-generated key to persist
     */
    fun start(
        faceName: String,
        existingOnionKey: String?,
        onPublished: (OnionPublish) -> Unit,
    ) {
        _status.value = ServerStatus.Starting
        scope.launch {
            val control = TorService.controlConnection()
            if (control == null) {
                _status.value = ServerStatus.Failed("Tor not connected")
                return@launch
            }
            val result = runCatching {
                val server = Transport.openServer(0)
                serverSocket = server
                val ports = mapOf(80 to "127.0.0.1:${server.localPort}")
                val keyArg = existingOnionKey ?: "NEW:ED25519-V3"
                org.cmchat.app.diag.Diag.i(
                    "onion",
                    "ADD_ONION ${if (existingOnionKey != null) "ED25519-V3:<stored>" else "NEW:ED25519-V3"} " +
                        "Port=80,127.0.0.1:${server.localPort}",
                )
                val reply = control.addOnion(keyArg, ports)
                // Log the full parsed reply (keys + values) so a 5xx / missing
                // ServiceID is visible on the Diagnostics screen. PrivateKey is
                // this device's own onion key (already in the vault), not a peer
                // secret, so logging that it was returned is safe; we log only
                // its presence, not the blob.
                org.cmchat.app.diag.Diag.i("onion", "ADD_ONION reply keys=${reply.keys}")
                val serviceId = reply.entries.firstOrNull { it.key.equals("ServiceID", true) }?.value
                    ?: throw IllegalStateException(
                        "ADD_ONION returned no ServiceID; reply=${reply.entries.joinToString { "${it.key}=${redact(it.key, it.value)}" }}"
                    )
                currentServiceId = serviceId
                val privateKey = reply.entries.firstOrNull { it.key.equals("PrivateKey", true) }?.value
                acceptLoop(server)
                OnionPublish("$serviceId.onion", privateKey)
            }
            result.onSuccess { pub ->
                onPublished(pub)
                org.cmchat.app.diag.Diag.i("onion", "published ${pub.onion}")
                _status.value = ServerStatus.Online(pub.onion, faceName, System.currentTimeMillis())
            }.onFailure { e ->
                org.cmchat.app.diag.Diag.e("onion", "publish failed", e)
                _status.value = ServerStatus.Failed(e.message ?: "publish failed")
            }
        }
    }

    fun stop() {
        scope.launch {
            runCatching {
                currentServiceId?.let { TorService.controlConnection()?.delOnion(it) }
            }
            currentServiceId = null
            runCatching { serverSocket?.close() }
            serverSocket = null
            _status.value = ServerStatus.Off
        }
    }

    fun restart(faceName: String, existingOnionKey: String?, onPublished: (OnionPublish) -> Unit) {
        stop()
        start(faceName, existingOnionKey, onPublished)
    }

    /** Connect to my own onion through Tor; report OK/FAIL and elapsed ms. */
    suspend fun selfTest(): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val onion = (status.value as? ServerStatus.Online)?.onion
            ?: return@withContext false to 0L
        val start = System.currentTimeMillis()
        val ok = runCatching {
            Transport.connectThroughTor(TorService.socksPort(), onion.removeSuffix(".onion"), 80)
                .use { it.isConnected }
        }.getOrElse { org.cmchat.app.diag.Diag.e("onion", "self-test failed", it); false }
        val ms = System.currentTimeMillis() - start
        org.cmchat.app.diag.Diag.i("onion", "self-test ${if (ok) "OK" else "FAIL"} ${ms}ms")
        ok to ms
    }

    /** Never log the onion PrivateKey blob; show presence only. */
    private fun redact(key: String, value: String): String =
        if (key.equals("PrivateKey", true)) "<${value.substringBefore(':')}:redacted>" else value

    private fun acceptLoop(server: ServerSocket) {
        scope.launch {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                val handler = onIncoming
                if (handler != null) runCatching { handler(socket) }
                else runCatching { socket.close() }
            }
        }
    }
}
