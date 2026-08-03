package com.fieldbook.tracker.traits

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.utilities.GeodeticUtils.Companion.lowPassFilter
import com.fieldbook.tracker.views.CompassView
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * A trait for measuring the tilt angle using device orientation.
 * Uses the device tilt from gravity sensor (or accelerometer) for angle measurement.
 */
class AngleTraitLayout : BaseTraitLayout {

    companion object {
        private const val UPDATE_INTERVAL: Long = 100
    }

    private lateinit var compassView: CompassView
    private lateinit var captureButton: FloatingActionButton

    private var currentAngle: Float = 0f

    private val updateHandler: Handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateFromSensor()
            updateHandler.postDelayed(this, UPDATE_INTERVAL)
        }
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun deleteTraitListener() {
        clearObservationOrRemoveTrait()
        super.deleteTraitListener()
    }

    override fun setNaTraitsText() {}

    override fun type(): String = "angle"

    override fun layoutId(): Int = R.layout.trait_angle

    override fun init(act: Activity) {
        compassView = findTraitView(R.id.compassView)
        captureButton = findTraitView(R.id.captureButton)

        captureButton.setOnClickListener {
            val angleValue = "%.1f".format(currentAngle)
            updateObservation(currentTrait, angleValue)
            collectInputView.text = angleValue
        }

        // Sensor polling needs CollectController (device tilt). Skip outside Collect
        // (Constructor preview / node AndroidView hosts) — still show compass chrome.
        if (controller != null) {
            updateHandler.post(updateRunnable)
        }
    }

    private fun updateCompass() {
        compassView.setYawAngle(currentAngle)
    }

    private fun updateFromSensor() {
        val tilt = controller?.getDeviceTilt() ?: return
        currentAngle = lowPassFilter(floatArrayOf(tilt.roll), floatArrayOf(currentAngle))[0]
        updateCompass()
    }
}