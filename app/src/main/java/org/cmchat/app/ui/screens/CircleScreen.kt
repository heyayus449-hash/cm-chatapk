package org.cmchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cmchat.app.ui.components.CmChatLogo
import org.cmchat.app.ui.theme.*

data class Contact(val name: String, val color: Color, val unread: Boolean)

/** Sample Circle shown only when the vault has no contacts yet. */
val sampleCircle = listOf(
    Contact("Nightingale", CmBlue, true),
    Contact("Quartz", CmOrange, false),
    Contact("Driftwood", CmGreen, false),
    Contact("Cipher", CmTextDim, false),
    Contact("Halcyon", CmRed, false),
)

@Composable
fun CircleScreen(
    contacts: List<Contact>,
    onOpenChat: (Contact) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(CmBackground)) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CmChatLogo(size = 24)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(CmOrange)
                    .clickable { }.padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text("+ Knock", color = Color.White, fontFamily = Nunito,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(contacts) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(CmCard).clickable { onOpenChat(c) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(c.color))
                    Spacer(Modifier.width(12.dp))
                    Text(c.name, color = CmText, fontFamily = Nunito, fontSize = 16.sp,
                        modifier = Modifier.weight(1f))
                    if (c.unread)
                        Box(Modifier.size(10.dp).clip(CircleShape).background(CmOrange))
                }
            }
        }

        Box(
            Modifier.fillMaxWidth().clickable { onOpenSettings() }.padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Settings", color = CmText, fontFamily = Nunito, fontSize = 15.sp)
        }
    }
}
