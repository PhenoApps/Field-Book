package com.fieldbook.tracker.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatImageView
import com.fieldbook.tracker.R

/**
 * An ImageView wrapper class that draws a rectangle around an image, leaving the outside semi-transparent.
 */
class OverlayImageView: AppCompatImageView {

    private var topX: Float = 0f
    private var topY: Float = 0f
    private var bottomX: Float = 0f
    private var bottomY: Float = 0f
    private var bitmap: Bitmap? = null
    private var parentWidth: Int = 0
    private var parentHeight: Int = 0
    private var parentRect: Rect? = null

    private val rectPaint = Paint().also { paint ->
        val typedValue = TypedValue()
        val accent = if (context.theme.resolveAttribute(R.attr.fb_color_accent, typedValue, true)) {
            typedValue.data
        } else {
            context.getColor(R.color.main_primary)
        }
        paint.color = accent
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 15f
    }

    private val dimPaint = Paint().also { paint ->
        val typedValue = TypedValue()
        val dimColor = if (context.theme.resolveAttribute(R.attr.fb_inverse_crop_region_color, typedValue, true)) {
            typedValue.data
        } else {
            context.getColor(R.color.main_inverse_crop_region_color)
        }
        paint.color = dimColor
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
    }

    private val imagePaint = Paint()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    //draw rect relative to this image view
    fun drawRectangle(bitmap: Bitmap, parentX: Float, parentY: Float, parentWidth: Int, parentHeight: Int,
                      topX: Float, topY: Float, bottomX: Float, bottomY: Float) {
        this.topX = topX - x
        this.topY = topY - y
        this.bottomX = bottomX - x
        this.bottomY = bottomY - y
        this.bitmap = bitmap
        this.parentWidth = parentWidth
        this.parentHeight = parentHeight
        this.parentRect = Rect(0, 0, parentWidth, parentHeight)

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        bitmap?.let { bmp ->
            parentRect?.let { parentRect ->
                imagePaint.alpha = 255
                canvas.drawBitmap(bmp, null, parentRect, imagePaint)

                val left = topX.coerceAtMost(bottomX)
                val top = topY.coerceAtMost(bottomY)
                val right = topX.coerceAtLeast(bottomX)
                val bottom = topY.coerceAtLeast(bottomY)
                val crop = RectF(left, top, right, bottom)
                val bounds = RectF(0f, 0f, parentWidth.toFloat(), parentHeight.toFloat())

                // Dim everything outside the crop window; keep the crop area fully visible.
                canvas.drawRect(bounds.left, bounds.top, bounds.right, crop.top, dimPaint)
                canvas.drawRect(bounds.left, crop.top, crop.left, crop.bottom, dimPaint)
                canvas.drawRect(crop.right, crop.top, bounds.right, crop.bottom, dimPaint)
                canvas.drawRect(bounds.left, crop.bottom, bounds.right, bounds.bottom, dimPaint)

                canvas.drawRect(crop, rectPaint)
            }
        }
    }
}
