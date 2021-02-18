package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout
import com.fieldbook.tracker.R

/**
 * A custom view for controlling a paged dataset.
 */
class PageControllerView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {

    val nextButton: Button
    val prevButton: Button

    init {

        inflate(context, R.layout.page_controller_view, this)

        nextButton = findViewById(R.id.next)
        prevButton = findViewById(R.id.prev)
    }

    fun setNextClick(onClick: OnClickListener) {

        nextButton.setOnClickListener(onClick)

    }

    fun setPrevClick(onClick: OnClickListener) {

        prevButton.setOnClickListener(onClick)

    }
}