package com.visionnav.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Tracks device physical orientation (compass azimuth) using hardware sensors.
 *
 * Implements circular/angular smoothing to prevent jitter without spinning
 * backwards across the 359° <-> 0° North boundary.
 */
class HeadingTracker(
    context: Context,
    private val smoothingFactor: Float = 0.20f // Low-pass responsiveness (0.15 - 0.25 is optimal for walking)
) : IHeadingProvider, SensorEventListener {

    companion object {
        private const val TAG = "HeadingTracker"

        /**
         * Pure function to calculate circular angular smoothing across the 0°/360° boundary.
         * Handles shortest path rotation so 359° -> 1° does not interpolate through 180°.
         */
        fun smoothAngle(currentSmoothed: Float?, rawTarget: Float, alpha: Float): Float {
            if (currentSmoothed == null) return (rawTarget % 360f + 360f) % 360f

            // Calculate shortest angular distance (-180° to +180°)
            val diff = ((rawTarget - currentSmoothed + 540f) % 360f) - 180f
            val result = currentSmoothed + (alpha * diff)
            return (result % 360f + 360f) % 360f
        }
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val _headingFlow = MutableStateFlow<Float?>(null)
    override val headingFlow: StateFlow<Float?> = _headingFlow.asStateFlow()

    private var isListening = false
    private var usingRotationVector = false

    // Sensor buffers for fallback
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var gravityValues: FloatArray? = null
    private var geomagneticValues: FloatArray? = null

    override fun start() {
        if (isListening || sensorManager == null) return

        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVectorSensor != null) {
            val registered = sensorManager.registerListener(
                this,
                rotationVectorSensor,
                SensorManager.SENSOR_DELAY_UI
            )
            if (registered) {
                usingRotationVector = true
                isListening = true
                Log.i(TAG, "HeadingTracker registered with TYPE_ROTATION_VECTOR")
                return
            }
        }

        // Fallback: Accelerometer + Magnetic Field
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (accelSensor != null && magSensor != null) {
            val regAccel = sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI)
            val regMag = sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_UI)
            if (regAccel && regMag) {
                usingRotationVector = false
                isListening = true
                Log.i(TAG, "HeadingTracker registered with Accelerometer + Magnetometer fallback")
                return
            }
        }

        Log.w(TAG, "No suitable orientation sensors available on this device")
        _headingFlow.value = null
    }

    override fun stop() {
        if (!isListening || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isListening = false
        Log.i(TAG, "HeadingTracker stopped and unregistered")
    }

    override fun onSensorChanged(event: SensorEvent) {
        var rawAzimuthDegrees: Float? = null

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuthRad = orientationAngles[0]
            val degrees = Math.toDegrees(azimuthRad.toDouble()).toFloat()
            rawAzimuthDegrees = (degrees + 360f) % 360f
        } else {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                gravityValues = event.values.clone()
            } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                geomagneticValues = event.values.clone()
            }

            val gravity = gravityValues
            val geo = geomagneticValues
            if (gravity != null && geo != null) {
                val success = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geo)
                if (success) {
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    val azimuthRad = orientationAngles[0]
                    val degrees = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                    rawAzimuthDegrees = (degrees + 360f) % 360f
                }
            }
        }

        if (rawAzimuthDegrees != null) {
            val currentSmoothed = _headingFlow.value
            val smoothed = smoothAngle(currentSmoothed, rawAzimuthDegrees, smoothingFactor)
            // Round to 1 decimal place to prevent spamming StateFlow with micro-floats
            val rounded = (smoothed * 10f).roundToInt() / 10f
            _headingFlow.value = rounded
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            Log.w(TAG, "Sensor accuracy status unreliable: ${sensor?.name}")
        }
    }
}

