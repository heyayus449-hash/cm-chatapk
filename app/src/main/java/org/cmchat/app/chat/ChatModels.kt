package org.cmchat.app.chat

/** Delivery state of an outgoing message. */
enum class MsgState { SENDING, SENT, DELIVERED, OFFLINE }

/** Self-destruct options for a message. */
enum class SelfTimer(val label: String, val millis: Long?) {
    OFF("off", null),
    S30("30s", 30_000L),
    M5("5m", 5 * 60_000L),
    H1("1h", 60 * 60_000L);

    companion object {
        fun fromLabel(l: String): SelfTimer = entries.firstOrNull { it.label == l } ?: OFF
    }
}

/** A chat message. RAM-only; never written to disk. */
data class ChatMessage(
    val id: String,
    val mine: Boolean,
    val text: String,
    val state: MsgState,
    val selfTimer: SelfTimer = SelfTimer.OFF,
    val createdAt: Long = System.currentTimeMillis(),
    val seenAt: Long? = null,
    val system: Boolean = false,
)

/**
 * Per-chat presence — deliberately coarse, never an exact time. Within 24h it
 * reads "last seen recently"; after 24h it shows nothing at all. The global
 * "Share my last-seen" toggle hides your own either way.
 */
object LastSeen {
    private const val DAY_MS = 24 * 60 * 60_000L

    fun bucket(lastSeenAtMs: Long?, nowMs: Long = System.currentTimeMillis()): String? {
        if (lastSeenAtMs == null || lastSeenAtMs > nowMs) return null
        return if (nowMs - lastSeenAtMs <= DAY_MS) "last seen recently" else null
    }
}

object SelfTimerRules {
    /** A message disappears this long after it is SEEN (not sent). Null = never. */
    fun expiresAt(seenAtMs: Long?, timer: SelfTimer): Long? {
        val seen = seenAtMs ?: return null
        val d = timer.millis ?: return null
        return seen + d
    }

    fun isExpired(seenAtMs: Long?, timer: SelfTimer, nowMs: Long = System.currentTimeMillis()): Boolean {
        val at = expiresAt(seenAtMs, timer) ?: return false
        return nowMs >= at
    }
}
