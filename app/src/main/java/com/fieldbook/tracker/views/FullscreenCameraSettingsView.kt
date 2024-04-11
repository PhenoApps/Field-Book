package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.View

open class FullscreenCameraSettingsView: CameraTraitSettingsView {

    init {

        systemCameraRg.visibility = View.GONE
    }

    constructor(ctx: Context, supportedResolutions: List<Size>) : super(ctx, supportedResolutions)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyle,
        defStyleRes
    )
}