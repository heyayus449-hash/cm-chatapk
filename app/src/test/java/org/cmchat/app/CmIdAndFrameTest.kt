package org.cmchat.app

import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import org.cmchat.app.crypto.CmId
import org.cmchat.app.crypto.CryptoManager
import org.cmchat.app.transport.Frame
import org.cmchat.app.transport.FrameCodec
import org.cmchat.app.transport.FrameType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CmIdAndFrameTest {

    private fun crypto() = CryptoManager(LazySodiumJava(SodiumJava()))

    private val sampleOnion = "cmchatexampleonionaddressv3base32abcdefghijklmnop234567ab"

    @Test
    fun cmid_round_trip() {
        val c = crypto()
        val (pub, _) = c.newIdentityKeypair()
        val id = CmId.encode(sampleOnion, pub)
        assertTrue(id.startsWith("cm1:"))
        val decoded = CmId.decode(id)!!
        assertEquals(sampleOnion, decoded.onion)
        assertEquals(pub.lowercase(), decoded.identityPubKeyHex.lowercase())
    }

    @Test
    fun cmid_malformed_rejected() {
        assertNull(CmId.decode("not-a-cm-id"))
        assertNull(CmId.decode("cm1:"))
        assertNull(CmId.decode("cm1:!!!!"))        // invalid base32
        assertNull(CmId.decode("cm1:AAAAAAAA"))    // decodes but too short for onion+key
    }

    @Test
    fun frame_seal_open_round_trip() {
        val c = crypto()
        val codec = FrameCodec(c)
        val (aPub, aSec) = c.newIdentityKeypair()
        val (bPub, bSec) = c.newIdentityKeypair()

        val payload = "hello over tor".toByteArray()
        val sealed = codec.seal(Frame(FrameType.KNOCK, payload), peerPubKeyHex = bPub, mySecretKeyHex = aSec)

        val opened = codec.open(sealed, peerPubKeyHex = aPub, mySecretKeyHex = bSec)!!
        assertEquals(FrameType.KNOCK, opened.type)
        assertArrayEquals(payload, opened.payload)
    }

    @Test
    fun tampered_frame_rejected() {
        val c = crypto()
        val codec = FrameCodec(c)
        val (aPub, aSec) = c.newIdentityKeypair()
        val (bPub, bSec) = c.newIdentityKeypair()

        val sealed = codec.seal(Frame(FrameType.MSG, "x".toByteArray()), bPub, aSec)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()
        assertNull(codec.open(sealed, aPub, bSec))
    }
}
