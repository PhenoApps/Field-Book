package com.fieldbook.tracker.traits

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.views.CompassView
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * A trait for measuring Yaw angle using device orientation.
 */
class AngleTraitLayout : BaseTraitLayout {

    companion object {
        private const val UPDATE_INTERVAL: Long = 100
    }

    private lateinit var compassView: CompassView
    private lateinit var captureButton: FloatingActionButton
    private var currentYaw: Float? = null

    private val updateHandler: Handler = Handler(Looper.getMainLooper())
    private var isUpdating: Boolean = false
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateFromSensor()
            if (isUpdating) {
                updateHandler.postDelayed(this, UPDATE_INTERVAL)
            }
        }
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun deleteTraitListener() {
        (context as CollectActivity).removeTrait()
        super.deleteTraitListener()
    }

    override fun setNaTraitsText() {}

    override fun type(): String = "angle"

    override fun layoutId(): Int = R.layout.trait_angle

    override fun init(act: Activity) {
        compassView = act.findViewById(R.id.compassView)
        captureButton = act.findViewById(R.id.captureButton)

        captureButton.setOnClickListener {
            controller.getRotationRelativeToDevice()?.let { rotation ->
                val angleValue = "%.2f".format(rotation.yaw)
                updateObservation(currentTrait, angleValue)
                collectInputView.text = angleValue
            }
        }

        startUpdates()
    }

    private fun startUpdates() {
        if (!isUpdating) {
            isUpdating = true
            updateHandler.post(updateRunnable)
        }
    }

    private fun updateCompass() {
        currentYaw?.apply {
            compassView.setYawAngle(this)
        }
    }

    private fun updateFromSensor() {
        controller.getRotationRelativeToDevice()?.let { rotation ->
            currentYaw = rotation.yaw
            updateCompass()
        }
    }
}