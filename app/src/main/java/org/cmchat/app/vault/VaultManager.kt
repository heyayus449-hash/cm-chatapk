package org.cmchat.app.vault

import org.cmchat.app.crypto.CryptoManager
import java.io.File

sealed interface UnlockResult {
    data class Success(val data: VaultData) : UnlockResult
    object WrongPin : UnlockResult
    /** The real PIN was entered in exact reverse (duress). Vault already wiped. */
    object Duress : UnlockResult
}

/**
 * Owns the vault lifecycle: first-run creation, unlock (incl. duress
 * detection), and wipe. The duress check needs no stored PIN: on a failed
 * unlock we try the reversed input; if THAT opens the vault, the user typed
 * their real PIN backwards, so we wipe and report Duress. Palindrome PINs are
 * rejected at creation precisely so reverse != forward.
 */
class VaultManager(val crypto: CryptoManager, dir: File) {

    private val vault = Vault(crypto, dir)

    fun firstRunNeeded(): Boolean = !vault.exists()

    fun createVault(pin: String, faceName: String): VaultData {
        val (pk, sk) = crypto.newIdentityKeypair()
        val face = Face(
            id = crypto.randomHex(8),
            name = faceName.ifBlank { "Wanderer" },
            publicKey = pk,
            secretKey = sk,
        )
        val data = VaultData(faces = listOf(face))
        vault.create(pin, data)
        return data
    }

    fun unlock(pin: String): UnlockResult {
        if (!vault.exists()) return UnlockResult.WrongPin
        vault.load(pin)?.let { return UnlockResult.Success(it) }
        val reversed = pin.reversed()
        if (reversed != pin && vault.load(reversed) != null) {
            vault.wipe()
            return UnlockResult.Duress
        }
        return UnlockResult.WrongPin
    }

    fun save(pin: String, data: VaultData) = vault.save(pin, data)

    fun wipe() = vault.wipe()

    companion object {
        fun isValidNewPin(pin: String): Boolean =
            pin.length == 6 && pin.all { it.isDigit() } && pin != pin.reversed()
    }
}
