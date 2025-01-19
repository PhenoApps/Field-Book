package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.ImageView
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
    }
}