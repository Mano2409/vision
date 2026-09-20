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

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            viewModelRef?.startLocationUpdates()
        } else {
            Toast.makeText(this, "Location permission is required for live GPS navigation. Simulator mode is active.", Toast.LENGTH_LONG).show()
        }

        if (audioGranted) {
            viewModelRef?.startListening()
        }

        if (cameraGranted) {
            viewModelRef?.let { vm ->
                vm.cameraManager.startCamera(this, vm.frameAnalyzer)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VisionNavTheme {
                val viewModel: VisionNavViewModel = viewModel()
                viewModelRef = viewModel

                MainNavScreen(
                    viewModel = viewModel,
                    onRequestAudioPermission = {
                        checkAndRequestPermissions {
                            viewModel.startListening()
                        }
                    }
                )
            }
        }

        // Check and request core location, voice, and camera permissions
        checkAndRequestPermissions {
            viewModelRef?.startLocationUpdates()
            viewModelRef?.let { vm ->
                if (vm.cameraManager.hasCameraPermission()) {
                    vm.cameraManager.startCamera(this, vm.frameAnalyzer)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModelRef?.startLocationUpdates()
        viewModelRef?.let { vm ->
            if (vm.cameraManager.hasCameraPermission()) {
                vm.cameraManager.startCamera(this, vm.frameAnalyzer)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModelRef?.stopLocationUpdates()
        viewModelRef?.cameraManager?.stopCamera()
    }

    private fun checkAndRequestPermissions(onGranted: () -> Unit) {
        val fineLocation = Manifest.permission.ACCESS_FINE_LOCATION
        val coarseLocation = Manifest.permission.ACCESS_COARSE_LOCATION
        val recordAudio = Manifest.permission.RECORD_AUDIO
        val cameraPermission = Manifest.permission.CAMERA

        val fineGranted = ContextCompat.checkSelfPermission(this, fineLocation) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, coarseLocation) == PackageManager.PERMISSION_GRANTED
        val audioGranted = ContextCompat.checkSelfPermission(this, recordAudio) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = ContextCompat.checkSelfPermission(this, cameraPermission) == PackageManager.PERMISSION_GRANTED

        val permissionsToRequest = mutableListOf<String>()
        if (!fineGranted) permissionsToRequest.add(fineLocation)
        if (!coarseGranted) permissionsToRequest.add(coarseLocation)
        if (!audioGranted) permissionsToRequest.add(recordAudio)
        if (!cameraGranted) permissionsToRequest.add(cameraPermission)

        if (permissionsToRequest.isEmpty()) {
            onGranted()
        } else {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
