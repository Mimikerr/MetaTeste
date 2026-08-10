package com.example.metateste.nexus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metateste.nexus.ui.HudLogEntry
import com.example.metateste.nexus.ui.theme.JarvisError
import com.example.metateste.nexus.ui.theme.JarvisOnBackground
import com.example.metateste.nexus.ui.theme.JarvisOnSurfaceMuted
import com.example.metateste.nexus.ui.theme.JarvisSuccess
import com.example.metateste.nexus.ui.theme.JarvisWarning
import com.example.metateste.shared.CommandStatus

/** Bottom-of-HUD conversation log — newest entry at the bottom, growing up. */
@Composable
fun HudLogPanel(entries: List<HudLogEntry>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(entries.asReversed(), key = { it.id }) { entry -> HudLogRow(entry) }
    }
}

@Composable
private fun HudLogRow(entry: HudLogEntry) {
    val (text, color) = when (entry) {
        is HudLogEntry.UserUtterance -> "> ${entry.text}" to JarvisOnBackground
        is HudLogEntry.AssistantResponse -> entry.text to when (entry.status) {
            CommandStatus.SUCCESS -> JarvisSuccess
            CommandStatus.FAILURE -> JarvisError
            null -> JarvisOnSurfaceMuted
        }
        is HudLogEntry.SystemNotice -> entry.text to JarvisWarning
    }
    Text(text = text, color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
}
