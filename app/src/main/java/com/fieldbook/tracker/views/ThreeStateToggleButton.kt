package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatToggleButton
import com.fieldbook.tracker.enums.ThreeState

/**
 * A custom button that extends ToggleButton and supports three states: true, false, and unset
 */
class ThreeStateToggleButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.buttonStyleToggle
) : AppCompatToggleButton(context, attrs, defStyleAttr) {

    private var currentState = ThreeState.NEUTRAL

    init {
        updateButtonAppearance()

        setOnClickListener {
            cycleState()
        }
    }

    private fun cycleState() {
        currentState = when(currentState) {
            ThreeState.NEUTRAL -> ThreeState.ON
            ThreeState.ON -> ThreeState.OFF
            ThreeState.OFF -> ThreeState.NEUTRAL
        }
        updateButtonAppearance()
    }

    fun getState(): String = currentState.state

    fun setState(stateString: String?) {
        currentState = ThreeState.fromString(stateString)
        updateButtonAppearance()
    }

    /**
     * ToggleButton has only ON/OFF states
     * When currentState is set to NEUTRAL, we edit the textOff as a workaround
     */
    private fun updateButtonAppearance() {
        textOn = ThreeState.ON.state
        textOff = if (currentState == ThreeState.NEUTRAL) ThreeState.NEUTRAL.state else ThreeState.OFF.state
        isChecked = currentState == ThreeState.ON
    }
}