package com.fieldbook.tracker.traits

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.composables.CircularTimer

class StopWatchTraitLayout : BaseTraitLayout {

    companion object {
        private const val TAG = "StopWatchTraitLayout"
    }

    private var composeView: ComposeView? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private val elapsedMillis = mutableLongStateOf(0L)
    private val isRunning = mutableStateOf(false)

    //stops the timer and resets the time
    override fun deleteTraitListener() {
        isRunning.value = false
        elapsedMillis.longValue = 0L
        super.deleteTraitListener()
    }

    override fun setNaTraitsText() {
        setupUi()
    }

    override fun type(): String = "stop_watch"

    override fun layoutId(): Int = R.layout.trait_stop_watch

    override fun init(act: Activity) {
        composeView = act.findViewById<ComposeView>(R.id.compose_view)
    }

    override fun loadLayout() {
        super.loadLayout()
        isRunning.value = false
        refreshLayout(onNew = false)
        setupUi()
    }

    override fun refreshLayout(onNew: Boolean?) {
        super.refreshLayout(onNew)
        if (currentObservation == null) {
            elapsedMillis.longValue = 0L
            return
        }
        val savedTime = currentObservation.value
        if (savedTime.isNotEmpty() && !onNew!!) {
            parseAndSetTime(savedTime)
        } else {
            elapsedMillis.longValue = 0L
        }
    }

    private fun parseAndSetTime(timeString: String) {
        try {
            val parts = timeString.split(":")
            if (parts.size == 3) {
                val hours = parts[0].toIntOrNull() ?: 0
                val minutes = parts[1].toIntOrNull() ?: 0

                val secAndMillis = parts[2].split(".")
                val seconds = secAndMillis.getOrNull(0)?.toIntOrNull() ?: 0
                val millis = secAndMillis.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toIntOrNull() ?: 0

                elapsedMillis.longValue = hours * 3600000L + minutes * 60000L + seconds * 1000 + millis
            } else {
                elapsedMillis.longValue = 0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing saved time: $timeString", e)
            elapsedMillis.longValue = 0L
        }
    }

    //starts the compose UI in the compose view
    //elapsed time and isrunning allow this trait layout to control the timer state
    private fun setupUi() {

        //get arc color from theme
        val arcColor = getThemedColor(R.attr.fb_color_primary)
        val iconBackgroundColor = getThemedColor(R.attr.fb_color_primary)

        composeView?.setContent {
            CircularTimer(
                elapsedMillis = elapsedMillis,
                isRunning = isRunning,
                progressArcColor = arcColor,
                iconBackgroundColor = iconBackgroundColor,
                resetLabel = context.getString(R.string.reset),
                playLabel = context.getString(R.string.play),
                pauseLabel = context.getString(R.string.pause),
                saveLabel = context.getString(R.string.save),
                onSaveCallback = { savedTime ->
                    saveObservation(savedTime)
                }
            )
        }
    }

    private fun getThemedColor(resId: Int): Color {

        val typedArray = context.theme.obtainStyledAttributes(
            intArrayOf(resId)
        )
        val colorInt = typedArray.getColor(0, 0xFF6200EE.toInt())
        typedArray.recycle()
        return Color(colorInt)

    }

    private fun saveObservation(savedTime: String) {

        collectInputView.text = savedTime

        collectActivity.updateObservation(currentTrait, savedTime, null)

    }
}