package org.phenoapps.labelprint.zpl

import android.graphics.Canvas

/**
 * Interface for rendering a [ZplLabel] onto an Android [Canvas].
 */
interface ZplRenderer {

    /**
     * Renders a [ZplLabel] onto the provided [Canvas] at the given scale.
     */
    fun render(
        canvas: Canvas,
        label: ZplLabel,
        scaleFactor: Float
    )

    /**
     * Calculates the scale factor to fit a label within the available canvas space.
     */
    fun calculateScaleFactor(
        labelWidthDots: Int,
        labelHeightDots: Int,
        canvasWidth: Float,
        canvasHeight: Float
    ): Float
}
