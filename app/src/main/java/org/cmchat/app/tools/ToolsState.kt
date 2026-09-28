package org.cmchat.app.tools

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Which dock tools are enabled (off by default) and the Notes scratchpad.
 * All RAM-only: nothing here is written to disk, and Notes is wiped when the
 * app/process ends or on a guardian wipe.
 */
object ToolsState {
    val calcEnabled = MutableStateFlow(false)
    val notesEnabled = MutableStateFlow(false)
    val converterEnabled = MutableStateFlow(false)

    /** RAM-only scratchpad; exists only while the app is open. */
    val notes = MutableStateFlow("")

    fun anyEnabled(): Boolean =
        calcEnabled.value || notesEnabled.value || converterEnabled.value

    fun clear() { notes.value = "" }
}
