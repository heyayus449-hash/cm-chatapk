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
                val reply = if (existingOnionKey != null) {
                    control.addOnion(existingOnionKey, ports)
                } else {
                    control.addOnion("NEW:ED25519-V3", ports)
                }
                val serviceId = reply["ServiceID"]
                    ?: error("no ServiceID in ADD_ONION reply")
                currentServiceId = serviceId
                acceptLoop(server)
                OnionPublish("$serviceId.onion", reply["PrivateKey"])
            }
            result.onSuccess { pub ->
                onPublished(pub)
                _status.value = ServerStatus.Online(pub.onion, faceName, System.currentTimeMillis())
            }.onFailure { e ->
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
        }.getOrDefault(false)
        ok to (System.currentTimeMillis() - start)
    }

    private fun acceptLoop(server: ServerSocket) {
        scope.launch {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                // Transport/frame handling is wired in R2; for now close politely.
                runCatching { socket.close() }
            }
        }
    }
}
