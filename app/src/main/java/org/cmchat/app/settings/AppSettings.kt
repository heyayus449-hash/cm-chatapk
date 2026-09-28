package org.cmchat.app.settings

import kotlinx.coroutines.flow.MutableStateFlow

/** Lightweight app-wide toggles (RAM; persisted into the vault later). */
object AppSettings {
    /** Strip EXIF/GPS from every photo before sending. Default ON. */
    val metadataScrub = MutableStateFlow(true)

    /** Share my per-chat last-seen with contacts. Default ON. */
    val shareLastSeen = MutableStateFlow(true)
}
