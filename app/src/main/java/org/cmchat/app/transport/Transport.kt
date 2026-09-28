package org.cmchat.app.transport

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket

/**
 * Length-prefixed frame I/O and Tor-only socket helpers.
 *
 * Outgoing connections go through Tor's SOCKS5 proxy to <onion>:port, with the
 * hostname left UNRESOLVED so Tor performs the .onion lookup (never the local
 * resolver). Incoming connections are accepted on a local ServerSocket that
 * sits behind the Face's onion service. The app opens no direct socket.
 *
 * Wire format per frame: 4-byte big-endian length, then that many bytes of
 * crypto_box-sealed data (see FrameCodec).
 */
object Transport {

    const val MAX_FRAME_BYTES = 8 * 1024 * 1024

    fun writeFrame(out: OutputStream, sealed: ByteArray) {
        val d = DataOutputStream(out)
        d.writeInt(sealed.size)
        d.write(sealed)
        d.flush()
    }

    /** Reads one length-prefixed frame, or null on clean EOF / oversized frame. */
    fun readFrame(input: InputStream): ByteArray? {
        val d = DataInputStream(input)
        val len = try {
            d.readInt()
        } catch (_: Exception) {
            return null
        }
        if (len <= 0 || len > MAX_FRAME_BYTES) return null
        val buf = ByteArray(len)
        d.readFully(buf)
        return buf
    }

    /** SOCKS5 through Tor to <onion>:port; hostname stays unresolved for Tor. */
    fun connectThroughTor(socksPort: Int, onion: String, port: Int, timeoutMs: Int = 60_000): Socket {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
        val socket = Socket(proxy)
        socket.connect(InetSocketAddress.createUnresolved(onion, port), timeoutMs)
        return socket
    }

    /** Local server behind the onion service; bound to loopback only. */
    fun openServer(port: Int): ServerSocket =
        ServerSocket(port, 50, java.net.InetAddress.getByName("127.0.0.1"))
}
