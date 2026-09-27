package org.cmchat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.cmchat.app.ui.screens.LockScreen
import org.cmchat.app.ui.theme.CmChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CmChatTheme {
                LockScreen()
            }
        }
    }
}
