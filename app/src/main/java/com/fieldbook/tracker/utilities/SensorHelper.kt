package com.fieldbook.tracker.utilities

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.view.Surface
import dagger.hilt.android.qualifiers.ActivityContext
import java.lang.Math.toDegrees
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.sqrt

class SensorHelper @Inject constructor(@ActivityContext private val context: Context) {

    companion object {
        private const val TAG = "SensorHelper"
    }

    data class RotationModel(val yaw: Float, val pitch: Float, val roll: Float)

    interface RelativeRotationListener {
        fun onRotationEvent(rotation: RotationModel)
    }

    interface GravityRotationListener {
        fun onGravityRotationChanged(rotation: RotationModel)
    }

    private val manager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private val relativeRotation by lazy {
        manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    }

    // use gravity sensor if available, fall back to accelerometer
    private val gravitySensor by lazy {
        manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private val listener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            when (event?.sensor?.type) {
                Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                    onRelativeRotationEvent(event)
                }
                Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                    onGravitySensorChanged(event)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun onRelativeRotationEvent(event: SensorEvent) {

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        val remapMatrix = FloatArray(9)
        SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Y, remapMatrix)

        val orientationVector = FloatArray(3)
        SensorManager.getOrientation(remapMatrix, orientationVector)

        //convert radians to degrees
        val yaw = (toDegrees(orientationVector[0].toDouble()) + 360) % 360
        val pitch = (toDegrees(orientationVector[1].toDouble()) + 360) % 360
        val roll = (toDegrees(orientationVector[2].toDouble()) + 360) % 360

        //log the angles
        //Log.d(TAG, "Yaw: $yaw Pitch: $pitch Roll: $roll")

        if (context is RelativeRotationListener) {
            context.onRotationEvent(RotationModel(yaw.toFloat(), pitch.toFloat(), roll.toFloat()))
        }
    }

    private fun onGravitySensorChanged(event: SensorEvent) {
        val gravityReading = FloatArray(3)

        // copy sensor data to array
        System.arraycopy(event.values, 0, gravityReading, 0, gravityReading.size)

        calculateGravityAngles(gravityReading)
    }

    private fun calculateGravityAngles(gravityReading: FloatArray) {
        val rotation = getDeviceRotation()

        // remap readings based on device rotation
        val remappedGravity = remapGravityByRotation(gravityReading, rotation)
        val gx = remappedGravity[0]
        val gy = remappedGravity[1]
        val gz = remappedGravity[2]

        // roll: x axis and plane perpendicular to gravity (horizontal plane) (side to side tilt)
        // (gx, sqrt(gy^2 + gz^2)
        val roll = toDegrees(atan2(gx.toDouble(), sqrt((gy * gy + gz * gz).toDouble()))).toFloat()

        // pitch: y axis and horizontal plane (forward/backward tilt)
        // (gy, sqrt(gx^2 + gz^2)
        val pitch = toDegrees(atan2(gy.toDouble(), sqrt((gx * gx + gz * gz).toDouble()))).toFloat()

        // yaw: angle between z axis and the gravity vector
        // this is not true yaw, rather it is tilt from the vertical plane
        // (gz, sqrt(gx^2 + gy^2)
        val tiltFromVertical = toDegrees(atan2(gz.toDouble(), sqrt((gx * gx + gy * gy).toDouble()))).toFloat()

        if (context is GravityRotationListener) {
            context.onGravityRotationChanged(RotationModel(tiltFromVertical, pitch, roll))
        }
    }

    private fun getDeviceRotation(): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display.rotation
        } else {
            windowManager.defaultDisplay.rotation
        }
    }

    /**
     * Remap axes based on screen rotation
     * Ensures consistent tilt calculations irrespective of device orientation
     */
    private fun remapGravityByRotation(gravity: FloatArray, rotation: Int): FloatArray {
        val remapped = FloatArray(3)

        // https://github.com/woheller69/Level/blob/master/app/src/main/java/org/woheller69/level/orientation/OrientationProvider.java
        when (rotation) {
            Surface.ROTATION_0 -> {
                remapped[0] = gravity[0]
                remapped[1] = gravity[1]
                remapped[2] = gravity[2]
            }
            Surface.ROTATION_90 -> {
                remapped[0] = -gravity[1]
                remapped[1] = gravity[0]
                remapped[2] = gravity[2]
            }
            Surface.ROTATION_180 -> {
                remapped[0] = -gravity[0]
                remapped[1] = -gravity[1]
                remapped[2] = gravity[2]
            }
            Surface.ROTATION_270 -> {
                remapped[0] = gravity[1]
                remapped[1] = -gravity[0]
                remapped[2] = gravity[2]
            }
        }

        return remapped
    }

    fun register() {

        manager.getSensorList(Sensor.TYPE_ALL).forEach {
            Log.d(TAG, "Sensor: ${it.name}")
        }

        manager.apply {
            registerListener(listener, relativeRotation, SensorManager.SENSOR_DELAY_NORMAL)
            registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun unregister() {
        manager.apply {
            unregisterListener(listener, relativeRotation)
            unregisterListener(listener, gravitySensor)
        }
    }
}