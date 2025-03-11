package com.fieldbook.tracker.utilities

import android.content.Context
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject

class SensorHelper @Inject constructor(@ActivityContext private val context: Context) {

    companion object {
        private const val TAG = "SensorHelper"
    }

    data class RotationModel(val yaw: Float, val pitch: Float, val roll: Float)

    interface RelativeRotationListener {
        fun onRotationEvent(rotation: RotationModel)
    }

    private val manager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private val relativeRotation by lazy {
        manager.getDefaultSensor(android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR)
    }

    private val listener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            when (event?.sensor?.type) {
                android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                    onRelativeRotationEvent(event)
                }
            }
        }

        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    private fun onRelativeRotationEvent(event: SensorEvent) {

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        val remapMatrix = FloatArray(9)
        SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Y, remapMatrix)

        val orientationVector = FloatArray(3)
        SensorManager.getOrientation(remapMatrix, orientationVector)

        //convert radians to degrees
        val yaw = (Math.toDegrees(orientationVector[0].toDouble()) + 360) % 360
        val pitch = (Math.toDegrees(orientationVector[1].toDouble()) + 360) % 360
        val roll = (Math.toDegrees(orientationVector[2].toDouble()) + 360) % 360

        //log the angles
        //Log.d(TAG, "Yaw: $yaw Pitch: $pitch Roll: $roll")

        if (context is RelativeRotationListener) {
            context.onRotationEvent(RotationModel(yaw.toFloat(), pitch.toFloat(), roll.toFloat()))
        }
    }

    fun register() {

        manager.getSensorList(android.hardware.Sensor.TYPE_ALL).forEach {
            Log.d(TAG, "Sensor: ${it.name}")
        }

        manager.registerListener(listener, relativeRotation, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun unregister() {
        manager.unregisterListener(listener, relativeRotation)
    }
}