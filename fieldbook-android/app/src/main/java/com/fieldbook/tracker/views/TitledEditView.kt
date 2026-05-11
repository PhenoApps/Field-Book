package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.fieldbook.tracker.R
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * A simple view that contains a text view to display a title, and a text input layout for
 * entering data.
 */
class TitledEditView: ConstraintLayout {

    //ui
    var titleTextView: TextView? = null
    var textInputLayout: TextInputLayout? = null
    var textInputEditText: TextInputEditText? = null

    //attributes
    var title: String? = null
    var hint: String? = null
    var inputType: Int? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {

        val typedArray = context.theme?.obtainStyledAttributes(
            attrs,
            R.styleable.TitledEditView,
            0,
            0
        )

        try {
            title = typedArray?.getString(R.styleable.TitledEditView_title)
            hint = typedArray?.getString(R.styleable.TitledEditView_android_hint)
            inputType = typedArray?.getInt(R.styleable.TitledEditView_android_inputType, 0)
        } finally {
            typedArray?.recycle()
        }

        initUi()
    }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private fun initUi() {

        val view = inflate(context, R.layout.view_titled_edit, this)

        titleTextView = view.findViewById(R.id.title_tv)
        textInputLayout = view.findViewById(R.id.text_input_layout)
        textInputEditText = view.findViewById(R.id.text_input_et)

        title?.let {
            titleTextView?.text = it
        }

        hint?.let {
            textInputLayout?.hint = it
        }

        inputType?.let {
            textInputEditText?.inputType = it
        }
    }
}