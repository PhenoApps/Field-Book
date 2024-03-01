package com.fieldbook.tracker.traits

import android.content.Context
import android.util.AttributeSet
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
open class CameraTrait : AbstractCameraTrait {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )
}

