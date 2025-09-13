package com.fieldbook.tracker.traits

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.views.TraitBoxView
import org.phenoapps.utils.SoftKeyboardUtil.Companion.closeKeyboard
import org.phenoapps.utils.SoftKeyboardUtil.Companion.showKeyboard

class TextTraitLayout : BaseTraitLayout {

    companion object {
        const val type = "text"

        /**
         * included this delay to ensure barcode readers scan the entire string correctly
         */
        const val DELAY_PER_UPDATE = 250L
    }

    private var isBlocked = false

    private var inputEditText: EditText? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    // Validates the text entered for text format
    private val textWatcher = object : TextWatcher {

        override fun afterTextChanged(en: Editable) {

            val value = en.toString()

            val bytes = value.toByteArray()

            //check that the string is not all zero-byte which usb barcode readers send at end
            if (bytes.isEmpty() || !value.toByteArray().all { it.toInt() == 0 }) {

                triggerTts(value)

                collectInputView.text = value

                updateObservation(currentTrait, value)

                observationEditedStyle()
            }
        }

        override fun beforeTextChanged(
            arg0: CharSequence, arg1: Int, arg2: Int, arg3: Int
        ) {
        }

        override fun onTextChanged(
            arg0: CharSequence, arg1: Int, arg2: Int, arg3: Int
        ) {
        }
    }

    override fun setNaTraitsText() {
        inputEditText?.setText("NA")
        observationEditedStyle()
    }

    override fun type(): String {
        return "text"
    }

    override fun layoutId(): Int {
        return R.layout.trait_text
    }

    private var scan = ""
    override fun init(act: Activity) {

        inputEditText = act.findViewById(R.id.trait_text_edit_text)

        inputEditText?.setOnLongClickListener{
            (context as CollectActivity).showObservationMetadataDialog()
            true
        }

        inputEditText?.setOnKeyListener { _, code, event ->

            if (inputEditText?.text?.toString() != scan) {
                scan = inputEditText?.text?.toString() ?: ""
            }

            if (event.keyCode == KeyEvent.KEYCODE_BACK) {

                (context as CollectActivity).onBackPressed()

                return@setOnKeyListener true
            }

            if (event.action == KeyEvent.ACTION_DOWN) {

                val cursor = inputEditText?.selectionStart ?: 0

                var deletePressed = false

                scan = if (code != KeyEvent.KEYCODE_ENTER && event.unicodeChar != 10) {

                    val newScan = if (code == KeyEvent.KEYCODE_DEL) {

                        deletePressed = true

                        //delete character at cursor
                        if (scan.isNotEmpty() && cursor > 0) scan.removeRange(cursor - 1, cursor)
                        else scan


                    } else {

                        "$scan${event.unicodeChar.toChar()}"

                    }

                    //set text for current trait/plot
                    inputEditText?.setText(newScan)

                    //set selection
                    if (deletePressed) {
                        inputEditText?.text?.toString()?.let { x ->
                            val newCursor = if (cursor > 0) cursor - 1 else 0
                            inputEditText?.setSelection(newCursor)
                        }
                    }
                    else {
                        inputEditText?.text?.toString()?.let { x ->

                            inputEditText?.setSelection(x.length)

                        }
                    }

                    newScan

                } else {

                    //check system setting to navigate to next plot/trait
                    val actionOnScanLineFeed =
                        prefs.getString(PreferenceKeys.RETURN_CHARACTER, "0") ?: "0"

                    if (actionOnScanLineFeed == "1") {
                        controller.getRangeBox().moveEntryRight()
                    }

                    if (actionOnScanLineFeed == "2") {
                        controller.getTraitBox().moveTrait(TraitBoxView.MoveDirection.RIGHT)
                    }

                    "" //reset the scan

                }
            } else if (event.action == KeyEvent.ACTION_UP
                && code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_TAB) {

                inputEditText?.requestFocus()
            }

            true
        }

        inputEditText?.requestFocus()
    }

    override fun loadLayout() {
        inputEditText?.removeTextChangedListener(textWatcher)
        super.loadLayout()
        inputEditText?.isEnabled = !isLocked
        inputEditText?.inputType = if (isLocked) EditorInfo.TYPE_NULL else EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        if (!isLocked) inputEditText?.requestFocus()
    }

    override fun afterLoadExists(act: CollectActivity, value: String?) {
        super.afterLoadExists(act, value)

        inputEditText?.let { input ->
            input.setText(value ?: "")
            input.setSelection(value?.length ?: 0)
            input.setTextColor(Color.parseColor(displayColor))
            scan = input.text.toString()
        }

        selectEditText()
    }

    override fun afterLoadNotExists(act: CollectActivity) {
        super.afterLoadNotExists(act)

        inputEditText?.let { input ->
            input.setText("")
            input.setSelection(0)
            input.setTextColor(Color.BLACK)
            scan = ""
        }

        selectEditText()
    }

    override fun afterLoadDefault(act: CollectActivity) {
        super.afterLoadDefault(act)

        inputEditText?.let { input ->
            input.setSelection(input.text.toString().length)
        }

        selectEditText()

    }

    override fun deleteTraitListener() {
        (context as CollectActivity).removeTrait()
        super.deleteTraitListener()
        inputEditText?.removeTextChangedListener(textWatcher)
        inputEditText?.text?.clear()
        inputEditText?.addTextChangedListener(textWatcher)
    }

    override fun refreshLayout(onNew: Boolean) {
        super.refreshLayout(false)
        inputEditText?.removeTextChangedListener(textWatcher)
        inputEditText?.text?.clear()
        if (currentObservation != null) {
            inputEditText?.setText(currentObservation.value)
            inputEditText?.setSelection(currentObservation.value.length)
        }
        selectEditText()
    }

    override fun block(): Boolean {
        return isBlocked
    }

    private fun selectEditText() {
        inputEditText?.let { input ->
            if (currentTrait.closeKeyboardOnOpen) {
                closeKeyboard(context, input, 300L)
            } else {
                showKeyboard(context, input, 300L)
            }
            input.removeTextChangedListener(textWatcher)
            input.addTextChangedListener(textWatcher)
            input.requestFocus()
        }
    }

    override fun refreshLock() {
        super.refreshLock()
        loadLayout()
    }

    private fun observationEditedStyle() {
        inputEditText?.setTypeface(null, Typeface.ITALIC)

        inputEditText?.setTextColor(textColor)
    }
}