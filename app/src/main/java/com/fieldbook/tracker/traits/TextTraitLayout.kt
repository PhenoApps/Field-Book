package com.fieldbook.tracker.traits

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.widget.EditText
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
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

            triggerTts(value)

            collectInputView.text = value

            Handler(Looper.getMainLooper()).postDelayed({

                updateObservation(currentTrait.trait, currentTrait.format, value)

            }, DELAY_PER_UPDATE)
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
    }

    override fun type(): String {
        return "text"
    }

    override fun layoutId(): Int {
        return R.layout.trait_text
    }

    override fun init(act: Activity) {

        inputEditText = act.findViewById(R.id.trait_text_edit_text)

    }

    override fun loadLayout() {
        inputEditText?.removeTextChangedListener(textWatcher)
        super.loadLayout()
        inputEditText?.isEnabled = !isLocked
        inputEditText?.selectAll()
    }

    override fun afterLoadExists(act: CollectActivity, value: String?) {
        super.afterLoadExists(act, value)

        inputEditText?.let { input ->
            input.setText(value ?: "")
            input.setSelection(value?.length ?: 0)
            input.setTextColor(Color.parseColor(displayColor))
        }

        selectEditText()
    }

    override fun afterLoadNotExists(act: CollectActivity) {
        super.afterLoadNotExists(act)

        inputEditText?.let { input ->
            input.setText("")
            input.setSelection(0)
            input.setTextColor(Color.BLACK)
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
            inputEditText?.selectAll()
        }
        selectEditText()
    }

    override fun block(): Boolean {
        return isBlocked
    }

    private fun selectEditText() {
        inputEditText?.let { input ->
            showKeyboard(context, input, 300L)
            input.removeTextChangedListener(textWatcher)
            input.addTextChangedListener(textWatcher)
            input.requestFocus()
        }
    }
}