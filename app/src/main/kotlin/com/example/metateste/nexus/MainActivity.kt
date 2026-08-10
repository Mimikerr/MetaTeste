package com.example.metateste.nexus

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.metateste.nexus.ui.NexusViewModel
import com.example.metateste.nexus.ui.navigation.NexusNavHost
import com.example.metateste.nexus.ui.theme.NexusTheme
import com.example.metateste.nexus.voice.VoiceCaptureService

class MainActivity : ComponentActivity() {

    private val nexusViewModel: NexusViewModel by viewModels()

    /**
     * The Back button is the only Touch controller input Horizon OS reliably forwards to a 2D
     * panel app as a real KeyEvent — other buttons (grip, face buttons) are consumed by the
     * system shell or routed only as pointer/hover events. Repurposed here as push-to-talk,
     * unless automatic (hands-free) mode is on, in which case Back keeps its normal navigation role.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isPushToTalkBack = event.keyCode == KeyEvent.KEYCODE_BACK &&
            event.repeatCount == 0 &&
            !nexusViewModel.uiState.value.autoSpeakEnabled
        if (isPushToTalkBack) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> sendTalkAction(VoiceCaptureService.ACTION_START_TALK)
                KeyEvent.ACTION_UP -> sendTalkAction(VoiceCaptureService.ACTION_STOP_TALK)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NexusTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions(),
                    ) { results ->
                        if (results[Manifest.permission.RECORD_AUDIO] == true) {
                            startVoiceCaptureService()
                        }
                    }
                    LaunchedEffect(Unit) {
                        permissionLauncher.launch(requiredPermissions())
                    }
                    NexusNavHost(viewModel = nexusViewModel)
                }
            }
        }
    }

    private fun requiredPermissions(): Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private fun startVoiceCaptureService() {
        ContextCompat.startForegroundService(this, Intent(this, VoiceCaptureService::class.java))
    }

    private fun sendTalkAction(action: String) {
        startService(Intent(this, VoiceCaptureService::class.java).setAction(action))
    }
}
