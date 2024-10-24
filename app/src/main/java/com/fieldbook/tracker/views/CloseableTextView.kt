package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import com.fieldbook.tracker.R

class CloseableTextView: FrameLayout {

    lateinit var textView: TextView
    lateinit var closeButton: ImageButton

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {

        //inflate the view
        val view = LayoutInflater.from(context).inflate(R.layout.closeable_text_view, this, true)

        textView = view.findViewById(R.id.closeable_text_view_title_tv)
        closeButton = view.findViewById(R.id.closeable_text_view_close_button)
    }
}