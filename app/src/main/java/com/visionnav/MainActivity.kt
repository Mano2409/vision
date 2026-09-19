package com.visionnav

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.visionnav.ui.screens.MainNavScreen
import com.visionnav.ui.state.VisionNavViewModel
import com.visionnav.ui.theme.VisionNavTheme

class MainActivity : ComponentActivity() {

    private var viewModelRef: VisionNavViewModel? = null

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModelRef?.startListening()
        } else {
            Toast.makeText(this, "Microphone permission is required for voice navigation", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VisionNavTheme {
                val viewModel: VisionNavViewModel = viewModel()
                viewModelRef = viewModel

                // Request microphone permission when user triggers voice input
                MainNavScreen(
                    viewModel = viewModel,
                    onRequestAudioPermission = {
                        checkAndRequestMicrophonePermission {
                            viewModel.startListening()
                        }
                    }
                )
            }
        }
    }

    private fun checkAndRequestMicrophonePermission(onGranted: () -> Unit) {
        val permission = Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            requestAudioPermissionLauncher.launch(permission)
        }
    }
}
