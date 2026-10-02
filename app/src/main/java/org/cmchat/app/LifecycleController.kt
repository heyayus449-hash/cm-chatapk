package org.cmchat.app

import android.content.Context
import org.cmchat.app.buzz.BuzzPolicy
import org.cmchat.app.chat.ChatStore
import org.cmchat.app.diag.Diag
import org.cmchat.app.notify.Notifier
import org.cmchat.app.settings.AppSettings
import org.cmchat.app.tools.ToolsState
import org.cmchat.app.tor.BuzzListenerService
import org.cmchat.app.tor.ServerController
import org.cmchat.app.tor.TorService
import org.cmchat.app.transport.MessageService

/**
 * App open/close lifecycle policy (item 6 of the batch):
 *
 *  - Swiped from recents (onTaskRemoved) = CLOSED: stop messaging, go OFFLINE,
 *    clear ALL RAM state (messages, notes, statuses). The vault stays, so the
 *    next open needs the PIN. EXCEPTION: if "Let a Buzz reach me when closed"
 *    is ON (and not in Invisible mode), a minimal buzz-listener stays alive so a
 *    BUZZ can still post an "Activity" notification; everything else is dropped.
 *  - Minimised (still in recents): stays ONLINE and keeps RAM (handled by not
 *    calling this); Cerberus keeps counting because minimising is NOT "touching".
 *  - Returning to the foreground resumes normal messaging.
 */
object LifecycleController {

    @Volatile
    var listening = false
        private set

    /** The user swiped the app away. */
    fun onAppClosed(context: Context) {
        val ctx = context.applicationContext
        // RAM is dropped either way.
        ChatStore.clearAll()
        ToolsState.clear()
        BuzzPolicy.clear()
        Notifier.clearAll(ctx)

        val keepListening = AppSettings.buzzListenerWhenClosed.value &&
            !AppSettings.invisibleMode.value
        if (keepListening) {
            // Buzz-only: Tor + onion stay up; only a BUZZ does anything now.
            MessageService.buzzOnlyMode = true
            MessageService.activeChatCmId = null
            BuzzListenerService.start(ctx)
            listening = true
            Diag.i("life", "closed -> buzz-listener alive")
        } else {
            fullClose(ctx)
        }
    }

    /** The user came back to the foreground. */
    fun onAppForeground() {
        if (listening) {
            MessageService.buzzOnlyMode = false
            AppSettings.appContext?.let { BuzzListenerService.stop(it) }
            listening = false
            Diag.i("life", "foreground -> normal")
        }
    }

    /** Fully go dark: stop the server and Tor, and drop the listener. */
    private fun fullClose(ctx: Context) {
        MessageService.buzzOnlyMode = false
        MessageService.activeChatCmId = null
        listening = false
        ServerController.stop()
        BuzzListenerService.stop(ctx)
        TorService.stop(ctx)
        Diag.i("life", "closed -> fully offline")
    }
}
