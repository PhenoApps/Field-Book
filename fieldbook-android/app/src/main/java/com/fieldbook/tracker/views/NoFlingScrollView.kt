package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView


class NoFlingScrollView : ScrollView {

    constructor(ctx: Context) : super(ctx)

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int): super(context, attrs, defStyle)

    override fun fling(velocityY: Int) {

    }

}