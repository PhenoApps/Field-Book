package com.fieldbook.tracker.traits

import android.content.Context
import android.graphics.Color
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
    }

    private var isBlocked = false

    private var inputEditText: EditText? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    // Validates the text entered for text format
    private val textWatcher = object : TextWatcher {

        override fun afterTextChanged(en: Editable) {

            val value = en.toString()

            triggerTts(value)

            collectInputView.text = value

            updateObservation(currentTrait.trait, currentTrait.format, value)
        }

        override fun beforeTextChanged(
            arg0: CharSequence, arg1: Int,
            arg2: Int, arg3: Int
        ) {
        }

        override fun onTextChanged(
            arg0: CharSequence, arg1: Int, arg2: Int,
            arg3: Int
        ) {
        }
    }

    override fun setNaTraitsText() {
        inputEditText?.setText("NA")
    }

    override fun type(): String {
        return "text"
    }

    override fun init() {

        inputEditText = findViewById(R.id.trait_text_edit_text)

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