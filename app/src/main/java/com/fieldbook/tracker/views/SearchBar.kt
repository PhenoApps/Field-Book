package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.widget.doAfterTextChanged
import com.fieldbook.tracker.R

class SearchBar : FrameLayout {

    lateinit var editText: AutoCompleteTextView
    lateinit var clearButton: ImageView

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    private fun init() {

        //inflate the view
        val view = LayoutInflater.from(context).inflate(R.layout.view_search_bar, this, true)

        editText = view.findViewById(R.id.search)
        clearButton = view.findViewById(R.id.clear)

        clearButton.setOnClickListener {
            editText.text.clear()
        }

        //only offer to clear when there's something to clear
        updateClearButton()
        editText.doAfterTextChanged { updateClearButton() }
    }

    private fun updateClearButton() {
        clearButton.visibility = if (editText.text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
}