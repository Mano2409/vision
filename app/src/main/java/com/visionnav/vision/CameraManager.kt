package com.visionnav.vision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Camera operational status states.
 */
enum class CameraStatus {
    UNINITIALIZED,
    PERMISSION_REQUIRED,
    INITIALIZING,
    RUNNING,
    UNAVAILABLE,
    DISCONNECTED,
    ERROR
}

/**
 * Manages the Android CameraX lifecycle, permissions, and ImageAnalysis bindings.
 */
class CameraManager(
    private val context: Context
) {
    private val tag = "CameraManager"

    private val _cameraStatus = MutableStateFlow(CameraStatus.UNINITIALIZED)
    val cameraStatus: StateFlow<CameraStatus> = _cameraStatus.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var analysisExecutor: ExecutorService? = null

    /**
     * Checks if camera permission is granted.
     */
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Initializes and binds the CameraX ImageAnalysis pipeline.
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        analyzer: ImageAnalysis.Analyzer
    ) {
        if (!hasCameraPermission()) {
            _cameraStatus.value = CameraStatus.PERMISSION_REQUIRED
            Log.w(tag, "Camera permission not granted.")
            return
        }

        _cameraStatus.value = CameraStatus.INITIALIZING

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // Create analysis use case with KEEP_ONLY_LATEST strategy
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysisExecutor?.shutdown()
                val executor = Executors.newSingleThreadExecutor()
                analysisExecutor = executor

                imageAnalysis.setAnalyzer(executor, analyzer)

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Gracefully verify rear camera exists on the device
                if (cameraProvider?.hasCamera(cameraSelector) != true) {
                    _cameraStatus.value = CameraStatus.UNAVAILABLE
                    Log.w(tag, "Rear camera is not available on this device.")
                    return@addListener
                }

                // Unbind previous use cases before rebinding
                cameraProvider?.unbindAll()

                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis
                )

                _cameraStatus.value = CameraStatus.RUNNING
                Log.i(tag, "CameraX bound successfully to lifecycle.")
            } catch (e: Exception) {
                Log.e(tag, "Failed to bind CameraX use cases: ${e.message}", e)
                _cameraStatus.value = CameraStatus.ERROR
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Unbinds camera use cases and releases background thread pools.
     */
    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            analysisExecutor?.shutdown()
            analysisExecutor = null
            _cameraStatus.value = CameraStatus.UNINITIALIZED
            Log.i(tag, "Camera stopped and resources released.")
        } catch (e: Exception) {
            Log.e(tag, "Error stopping camera: ${e.message}", e)
        }
    }
}

