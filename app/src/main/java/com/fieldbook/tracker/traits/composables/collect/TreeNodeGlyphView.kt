package com.fieldbook.tracker.traits.composables.collect

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.traits.formats.tree.NodeFill
import com.fieldbook.tracker.traits.formats.tree.TreeNodeCompletion

/** Draws root square / stem circle / branch triangle with trait-completion sectors. */
class TreeNodeGlyphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var label: String = ""
    private var shape: TreeNodeCompletion.NodeShape = TreeNodeCompletion.NodeShape.CIRCLE
    private var fill: NodeFill = NodeFill(0, 0)
    private var current: Boolean = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3.5f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
        isFakeBoldText = true
    }
    private val scratch = RectF()
    private val triPath = Path()

    init {
        val saved = PreferenceManager.getDefaultSharedPreferences(context)
            .getInt(PreferenceKeys.SAVED_DATA_COLOR, resolveThemeColor(R.attr.fb_value_saved_color))
        fillPaint.color = saved or 0xFF000000.toInt()
        emptyPaint.color = resolveThemeColor(R.attr.fb_trait_button_background_tint)
        strokePaint.color = resolveThemeColor(R.attr.fb_color_text_dark)
        currentPaint.color = resolveThemeColor(R.attr.fb_color_primary)
        textPaint.color = resolveThemeColor(R.attr.fb_color_text_dark)
    }

    fun bind(
        label: String,
        shape: TreeNodeCompletion.NodeShape,
        fill: NodeFill,
        current: Boolean,
    ) {
        this.label = label
        this.shape = shape
        this.fill = fill
        this.current = current
        contentDescription = buildString {
            append(label)
            if (fill.total > 0) append(", ${fill.filled} of ${fill.total} traits filled")
            if (current) append(", current")
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val pad = dp(4f)
        scratch.set(pad, pad, width - pad, height - pad)
        rebuildTriangle()
        drawShapeBackground(canvas)
        drawSectors(canvas)
        drawOutline(canvas)
        if (current) drawCurrentRing(canvas)
        val cx = width / 2f
        val cy = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, cx, cy, textPaint)
    }

    private fun rebuildTriangle() {
        triPath.reset()
        triPath.moveTo(scratch.centerX(), scratch.top)
        triPath.lineTo(scratch.right, scratch.bottom)
        triPath.lineTo(scratch.left, scratch.bottom)
        triPath.close()
    }

    private fun drawShapeBackground(canvas: Canvas) {
        when (shape) {
            TreeNodeCompletion.NodeShape.SQUARE -> canvas.drawRect(scratch, emptyPaint)
            TreeNodeCompletion.NodeShape.CIRCLE -> canvas.drawOval(scratch, emptyPaint)
            TreeNodeCompletion.NodeShape.TRIANGLE -> canvas.drawPath(triPath, emptyPaint)
        }
    }

    private fun drawSectors(canvas: Canvas) {
        val total = fill.total.coerceAtLeast(0)
        if (total == 0) return
        val sweep = 360f / total
        var start = -90f
        for (i in 0 until total) {
            val paint = if (i < fill.filled) fillPaint else emptyPaint
            when (shape) {
                TreeNodeCompletion.NodeShape.CIRCLE -> canvas.drawArc(scratch, start, sweep, true, paint)
                TreeNodeCompletion.NodeShape.SQUARE -> {
                    canvas.save()
                    canvas.clipRect(scratch)
                    canvas.drawArc(scratch, start, sweep, true, paint)
                    canvas.restore()
                }
                TreeNodeCompletion.NodeShape.TRIANGLE -> {
                    canvas.save()
                    canvas.clipPath(triPath)
                    canvas.drawArc(scratch, start, sweep, true, paint)
                    canvas.restore()
                }
            }
            start += sweep
        }
    }

    private fun drawOutline(canvas: Canvas) {
        when (shape) {
            TreeNodeCompletion.NodeShape.SQUARE -> canvas.drawRect(scratch, strokePaint)
            TreeNodeCompletion.NodeShape.CIRCLE -> canvas.drawOval(scratch, strokePaint)
            TreeNodeCompletion.NodeShape.TRIANGLE -> canvas.drawPath(triPath, strokePaint)
        }
    }

    private fun drawCurrentRing(canvas: Canvas) {
        val inset = dp(2f)
        val ring = RectF(scratch.left - inset, scratch.top - inset, scratch.right + inset, scratch.bottom + inset)
        when (shape) {
            TreeNodeCompletion.NodeShape.SQUARE -> canvas.drawRect(ring, currentPaint)
            TreeNodeCompletion.NodeShape.CIRCLE -> canvas.drawOval(ring, currentPaint)
            TreeNodeCompletion.NodeShape.TRIANGLE -> {
                val p = Path()
                p.moveTo(ring.centerX(), ring.top)
                p.lineTo(ring.right, ring.bottom)
                p.lineTo(ring.left, ring.bottom)
                p.close()
                canvas.drawPath(p, currentPaint)
            }
        }
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private fun resolveThemeColor(attr: Int): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) ContextCompat.getColor(context, tv.resourceId) else tv.data
        } else {
            0xFF000000.toInt()
        }
    }
}
