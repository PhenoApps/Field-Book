package com.fieldbook.tracker.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.RequiresApi
import com.fieldbook.tracker.R
import androidx.appcompat.widget.AppCompatImageView

/**
 * An ImageView wrapper class that draws a rectangle around an image, leaving the outside semi-transparent.
 */
class OverlayImageView: AppCompatImageView {

    private var topX: Float = 0f
    private var topY: Float = 0f
    private var bottomX: Float = 0f
    private var bottomY: Float = 0f
    private var bitmap: Bitmap? = null
    private var parentX: Float = 0f
    private var parentY: Float = 0f
    private var parentWidth: Int = 0
    private var parentHeight: Int = 0
    private var parentRect: Rect? = null

    private val rectPaint = Paint().also { paint ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            paint.color = context.getColor(R.color.main_primary)
        } else {
            paint.color = Color.BLACK
        }
        //create a dashed path
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 15f
    }

    private val paint = Paint()
    private val porterDuffXfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)

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
        this.parentX = parentX
        this.parentY = parentY
        this.parentWidth = parentWidth
        this.parentHeight = parentHeight
        this.parentRect = Rect(0, 0, parentWidth, parentHeight)

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        bitmap?.let { bmp ->

            parentRect?.let { parentRect ->

                //set alpha to half and draw the full bitmap scaled to the parent
                paint.alpha = 128
                canvas.drawBitmap(bmp, null, parentRect, paint)

                //enable porter duff overlay, draw rect with full alpha and then disable
                paint.alpha = 255
                paint.xfermode = porterDuffXfermode
                canvas.drawRect(topX, topY, bottomX, bottomY, paint)
                paint.xfermode = null

                //draw the crop border
                canvas.drawRect(topX, topY, bottomX, bottomY, rectPaint)
            }
        }
    }
}