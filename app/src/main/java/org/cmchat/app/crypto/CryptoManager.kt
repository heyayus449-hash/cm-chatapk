package org.cmchat.app.crypto

import com.goterl.lazysodium.LazySodium
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.interfaces.PwHash
import com.goterl.lazysodium.interfaces.SecretBox
import com.sun.jna.NativeLong

/**
 * Thin wrapper over libsodium (via lazysodium). All primitives are the
 * library's own; nothing here is hand-rolled.
 *
 *  - PIN -> 32-byte key: Argon2id (crypto_pwhash) with a per-vault random
 *    16-byte salt. Interactive ops/mem limits (2 / 64 MiB) so unlock stays
 *    usable on low-RAM phones.
 *  - Vault sealing: crypto_secretbox_easy (XSalsa20-Poly1305), the libsodium
 *    "secretbox" primitive, output framed as nonce || ciphertext.
 *  - Per-Face identity keys: crypto_box keypair (X25519).
 *
 * The same class runs on-device (LazySodiumAndroid) and in host unit tests
 * (LazySodiumJava), so the crypto is exercised in CI.
 */
class CryptoManager(private val ls: LazySodium) {

    companion object {
        const val SALT_BYTES = 16
        const val KEY_BYTES = 32
        const val NONCE_BYTES = 24
        const val MAC_BYTES = 16
        const val OPS_LIMIT = 2L
        const val MEM_LIMIT = 67108864 // 64 MiB
    }

    private val pwHash get() = ls as PwHash.Native
    private val secretBox get() = ls as SecretBox.Native
    private val box get() = ls as Box.Lazy

    fun randomSalt(): ByteArray = ls.randomBytesBuf(SALT_BYTES)

    fun randomHex(bytes: Int): String = toHex(ls.randomBytesBuf(bytes))

    fun deriveKey(pin: String, salt: ByteArray): ByteArray {
        require(salt.size == SALT_BYTES) { "bad salt length" }
        val key = ByteArray(KEY_BYTES)
        val pw = pin.toByteArray(Charsets.UTF_8)
        val ok = pwHash.cryptoPwHash(
            key, KEY_BYTES, pw, pw.size, salt,
            OPS_LIMIT, NativeLong(MEM_LIMIT.toLong()),
            PwHash.Alg.PWHASH_ALG_ARGON2ID13,
        )
        check(ok) { "Argon2id derivation failed" }
        return key
    }

    /** Returns nonce || ciphertext. */
    fun seal(plain: ByteArray, key: ByteArray): ByteArray {
        val nonce = ls.randomBytesBuf(NONCE_BYTES)
        val cipher = ByteArray(plain.size + MAC_BYTES)
        val ok = secretBox.cryptoSecretBoxEasy(cipher, plain, plain.size.toLong(), nonce, key)
        check(ok) { "seal failed" }
        return nonce + cipher
    }

    /** Input is nonce || ciphertext; returns null if authentication fails. */
    fun open(blob: ByteArray, key: ByteArray): ByteArray? {
        if (blob.size < NONCE_BYTES + MAC_BYTES) return null
        val nonce = blob.copyOfRange(0, NONCE_BYTES)
        val cipher = blob.copyOfRange(NONCE_BYTES, blob.size)
        val plain = ByteArray(cipher.size - MAC_BYTES)
        val ok = secretBox.cryptoSecretBoxOpenEasy(plain, cipher, cipher.size.toLong(), nonce, key)
        return if (ok) plain else null
    }

    /** X25519 identity keypair for a Face, as (publicHex, secretHex). */
    fun newIdentityKeypair(): Pair<String, String> {
        val kp = box.cryptoBoxKeypair()
        return kp.publicKey.asHexString to kp.secretKey.asHexString
    }

    private fun toHex(b: ByteArray): String =
        b.joinToString("") { "%02x".format(it) }
}
