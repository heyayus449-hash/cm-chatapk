package org.cmchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cmchat.app.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenMyServer: () -> Unit = {},
    onOpenMyId: () -> Unit = {},
    onWipeEverything: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
) {
    var textSize by remember { mutableStateOf(0f) }
    Column(Modifier.fillMaxSize().background(CmBackground)) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("‹ Back", color = CmBlue, fontFamily = Nunito, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.CenterStart).clickable { onBack() })
            Text("Settings", color = CmText, fontFamily = Nunito, fontSize = 17.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(CmCard).padding(14.dp)) {
                Text("Text Size", color = CmText, fontFamily = Nunito, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold)
                Slider(value = textSize, onValueChange = { textSize = it },
                    valueRange = -6f..6f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("−6", color = CmTextFaint, fontFamily = Nunito, fontSize = 11.sp)
                    Text("Default", color = CmTextFaint, fontFamily = Nunito, fontSize = 11.sp)
                    Text("+6", color = CmTextFaint, fontFamily = Nunito, fontSize = 11.sp)
                }
            }

            Setting("My Server", onClick = onOpenMyServer)
            Setting("Faces (Identities)")
            Setting("Circle")
            Setting("Cerberus · idle auto-wipe", "90 min")
            Setting("Kill Timer", "not armed")
            Setting("Self-Timer (per message)", "30s")
            ToolToggle("Tool: Calculator", org.cmchat.app.tools.ToolsState.calcEnabled)
            ToolToggle("Tool: Notes", org.cmchat.app.tools.ToolsState.notesEnabled)
            ToolToggle("Tool: Converter", org.cmchat.app.tools.ToolsState.converterEnabled)
            Setting("My CMC-ID / QR", onClick = onOpenMyId)
            ToolToggle("Metadata scrub (strip EXIF/GPS)", org.cmchat.app.settings.AppSettings.metadataScrub)
            ToolToggle("Share my last-seen", org.cmchat.app.settings.AppSettings.shareLastSeen)
            Setting("Panic PIN")
            Setting("Diagnostics", onClick = onOpenDiagnostics)
            Setting("Verify App Integrity")
            Setting("About / Version")
            Spacer(Modifier.height(4.dp))
        }

        Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(14.dp))
            .background(CmRed.copy(alpha = 0.15f)).clickable { onWipeEverything() }.padding(14.dp),
            contentAlignment = Alignment.Center) {
            Text("Wipe Everything Now", color = CmRed, fontFamily = Nunito,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ToolToggle(label: String, flow: kotlinx.coroutines.flow.MutableStateFlow<Boolean>) {
    val on by flow.collectAsState()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CmCard)
        .clickable { flow.value = !on }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = CmText, fontFamily = Nunito, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(if (on) "On" else "Off", color = if (on) CmGreen else CmTextDim,
            fontFamily = Nunito, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Setting(label: String, value: String = "", onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CmCard)
        .clickable { onClick() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = CmText, fontFamily = Nunito, fontSize = 14.sp,
            modifier = Modifier.weight(1f))
        if (value.isNotEmpty())
            Text(value, color = CmBlue, fontFamily = Nunito, fontSize = 13.sp)
    }
}
