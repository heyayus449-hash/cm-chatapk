package org.cmchat.app.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** Lightweight app-wide toggles (RAM; persisted into the vault later). */
object AppSettings {
    /** Strip EXIF/GPS from every photo before sending. Default ON. */
    val metadataScrub = MutableStateFlow(true)

    /** Share my per-chat last-seen with contacts. Default ON. */
    val shareLastSeen = MutableStateFlow(true)

    /**
     * Invisible mode: refuse all incoming connections so any probe (message,
     * retry, buzz) sees me as OFFLINE. I can still start outbound conversations.
     * Default OFF.
     */
    val invisibleMode = MutableStateFlow(false)

    /**
     * Keep a minimal "buzz-listener" alive after the app is swiped away, so a
     * Buzz can still reach me while the full app is closed. Default ON. Off =
     * closing the app goes fully dark (a buzz only reaches a minimised app).
     */
    val buzzListenerWhenClosed = MutableStateFlow(true)

    /**
     * Show the sender's nickname on Buzz/message notifications. Default OFF:
     * the lock screen shows only "Activity"/"Notification", never a name.
     */
    val showBuzzSenderName = MutableStateFlow(false)

    /**
     * App context for posting notifications from background (buzz listener).
     * Application context only — never an Activity — so it cannot leak a window.
     */
    @Volatile
    var appContext: Context? = null
}
