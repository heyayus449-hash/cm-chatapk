package org.cmchat.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.cmchat.app.guard.GuardController
import org.cmchat.app.ui.AppNav
import org.cmchat.app.ui.theme.CmChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No screenshots, blank in recents, no screen recording.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            CmChatTheme {
                AppNav()
            }
        }
    }

    // Reopening from recents / returning to the app counts as touching it,
    // which resets the Cerberus idle clock.
    override fun onResume() {
        super.onResume()
        GuardController.touch()
    }
}
