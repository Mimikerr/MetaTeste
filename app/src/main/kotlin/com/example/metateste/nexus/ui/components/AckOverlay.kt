package com.example.metateste.nexus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.metateste.nexus.ui.theme.NexusStatusColors
import com.example.metateste.shared.CommandStatus
import kotlinx.coroutines.delay

@Composable
fun AckOverlay(
    ackId: String?,
    status: CommandStatus?,
    detail: String?,
    modifier: Modifier = Modifier,
    onTimeout: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(ackId) {
        if (ackId != null) {
            visible = true
            delay(2500)
            visible = false
            onTimeout()
        }
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val isSuccess = status == CommandStatus.SUCCESS
        Surface(
            color = if (isSuccess) NexusStatusColors.success else NexusStatusColors.error,
            shape = MaterialTheme.shapes.large,
        ) {
            Text(
                text = if (isSuccess) "Comando executado" else "Falha ao executar${detail?.let { ": $it" } ?: ""}",
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
