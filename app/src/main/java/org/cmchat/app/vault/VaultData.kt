package org.cmchat.app.vault

import kotlinx.serialization.Serializable

/** An identity ("Face"): display name + its X25519 keypair (hex). */
@Serializable
data class Face(
    val id: String,
    val name: String,
    val publicKey: String,
    val secretKey: String,
)

/** A contact in the Circle, belonging to one Face. */
@Serializable
data class ContactRec(
    val id: String,
    val name: String,
    val colorArgb: Long,
    val faceId: String,
)

@Serializable
data class VaultSettings(
    val cerberusMinutes: Int = 90,
    val defaultSelfTimer: String = "30s",
    val textSize: Int = 0,
)

/** Everything persisted in the encrypted vault. Messages are NOT here. */
@Serializable
data class VaultData(
    val faces: List<Face> = emptyList(),
    val contacts: List<ContactRec> = emptyList(),
    val settings: VaultSettings = VaultSettings(),
)
