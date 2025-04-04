package com.fieldbook.tracker.views

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginBottom
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.utilities.VibrateUtil
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Custom view to crop an image using handles.
 * The view contains a bitmap image, and five handles to crop the image.
 * The handles are draggable, and the image is cropped to the rectangle defined by the handles.
 * The fifth handle is a middle handle that adjusts the location of the entire rect.
 * The view also contains buttons to save, reset, copy, and expand the crop rectangle.
 */
@AndroidEntryPoint
class CropImageView : ConstraintLayout {

    companion object {
        const val TAG = "CropImageView"
        const val MIN_DISTANCE_BETWEEN_HANDLES = 32
        const val DEFAULT_CROP_COORDINATES = "0.00, 0.00, 1.00, 1.00"
        fun parseRectCoordinates(rectCoordinates: String): RectF? {

            if (rectCoordinates.isNotBlank()) {
                val rect = RectF()
                val values = rectCoordinates.split(",")
                if (values.size == 4) {
                    try {
                        rect.left = values[0].toFloat()
                        rect.top = values[1].toFloat()
                        rect.right = values[2].toFloat()
                        rect.bottom = values[3].toFloat()

                        return rect

                    } catch (e: Exception) {

                        e.printStackTrace()

                    }
                }
            }

            return null
        }
    }

    interface CropImageHandler {
        fun onCropImageSaved(rectCoordinates: String)
        fun getImageUri(): String
        fun getCropCoordinates(): String
    }

    @Inject
    lateinit var vibrateUtil: VibrateUtil

    //interface reference
    var cropImageHandler: CropImageHandler? = null

    private var cropHandleTop: ImageView? = null
    private var cropHandleBottom: ImageView? = null
    private var cropHandleStart: ImageView? = null
    private var cropHandleEnd: ImageView? = null
    private var cropHandleMid: ImageView? = null

    //declare buttons
    private var saveButton: MaterialButton? = null
    private var resetButton: ImageButton? = null
    private var copyButton: ImageButton? = null
    private var expandButton: ImageButton? = null

    private var relativeLayout: RelativeLayout? = null

    //declare edit text
    private var editText: EditText? = null

    private var imageView: OverlayImageView? = null

    //size of the handle image view, set in dimens.xml
    private val handleSize: Int

    //global handle that tracks which handle was last clicked
    private var handle: ImageView? = null

    //bitmap that holds the image to crop
    private var bitmap: Bitmap? =
        null // = BitmapFactory.decodeStream(resources.openRawResource(R.raw.chip))

    init {

        //allows child views to run onDraw function on invalidation
        setWillNotDraw(false)

        //half the size of the handle, to center on lines
        handleSize = resources.getDimensionPixelSize(R.dimen.crop_handle_size)

        val view = inflate(context, R.layout.view_crop_image, this)

        //get the handles
        cropHandleTop = view.findViewById(R.id.crop_top_handle)
        cropHandleBottom = view.findViewById(R.id.crop_bottom_handle)
        cropHandleStart = view.findViewById(R.id.crop_start_handle)
        cropHandleEnd = view.findViewById(R.id.crop_end_handle)
        cropHandleMid = view.findViewById(R.id.crop_mid_handle)

        //initialize buttons
        saveButton = view.findViewById(R.id.crop_save_btn)
        resetButton = view.findViewById(R.id.crop_reset_btn)
        copyButton = view.findViewById(R.id.crop_copy_btn)
        expandButton = view.findViewById(R.id.crop_expand_btn)

        //initialize edit text
        editText = view.findViewById(R.id.crop_image_tv)

        //initialize image view
        imageView = view.findViewById(R.id.crop_image_iv)

        //initialize relative layout
        relativeLayout = view.findViewById(R.id.crop_image_rl)

        initExpandButton()
        initEditText()
        initResetButton()
        initCopyButton()
        initSaveButton()
        init()
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x2 - x1).toDouble() * (x2 - x1) + (y2 - y1).toDouble() * (y2 - y1)).toFloat()
    }

    fun initialize() {

        try {

            //set the crop coordinates from the handler
            val rectCoordinates = cropImageHandler?.getCropCoordinates()
            val imageUri = Uri.parse(cropImageHandler?.getImageUri())

            editText?.setText(rectCoordinates)

            //load, rotate and scale the image using a background coroutine
            findViewTreeLifecycleOwner()?.lifecycleScope?.launch {

                withContext(Dispatchers.IO) {

                    //set the image uri from the handler
                    BitmapFactory.decodeStream(context.contentResolver.openInputStream(imageUri))?.let { bmp ->

                        bitmap = bmp

                        //rotate bitmap if portrait
                        if (bmp.width > bmp.height) {
                            val matrix = android.graphics.Matrix()
                            matrix.postRotate(90f)
                            bitmap = Bitmap.createBitmap(
                                bmp,
                                0,
                                0,
                                bmp.width,
                                bmp.height,
                                matrix,
                                true
                            )
                        }

                        withContext(Dispatchers.Main) {

                            invalidate()

                            submitCoordinatesToUi()
                        }
                    }
                }
            }

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }

    /**
     * Expand rect coordinates to the size of the image
     */
    private fun initExpandButton() {

        expandButton?.setOnClickListener {

            VibrateUtil(context).vibrate(1L)
            VibrateUtil(context).vibrate(1L)

            imageView?.let { iv ->

                //reset handles to default positions
                cropHandleTop?.let { top ->
                    top.x = iv.x + iv.width / 2 - handleSize / 2
                    top.y = iv.y - handleSize / 2
                }

                cropHandleBottom?.let { bot ->
                    bot.x = iv.x + iv.width / 2 - handleSize / 2
                    bot.y = iv.y + iv.height - handleSize / 2
                }

                cropHandleStart?.let { start ->
                    start.x = iv.x - handleSize / 2
                    start.y = iv.y + iv.height / 2 - handleSize / 2
                }

                cropHandleEnd?.let { end ->
                    end.x = iv.x + iv.width - handleSize / 2
                    end.y = iv.y + iv.height / 2 - handleSize / 2
                }
            }

            invalidate()
        }
    }

    private fun submitCoordinatesToUi() {
        val input = editText!!.text.toString()
        if (input.isNotBlank()) {
            val values = input.split(",")
            if (values.size == 4) {
                try {
                    val topLeftX = values[0].toFloat()
                    val topLeftY = values[1].toFloat()
                    val bottomRightX = values[2].toFloat()
                    val bottomRightY = values[3].toFloat()

                    imageView?.let { iv ->
                        cropHandleTop?.y =
                            iv.y + topLeftY * iv.height - handleSize / 2
                        cropHandleBottom?.y =
                            iv.y + bottomRightY * iv.height - handleSize / 2
                        cropHandleStart?.x =
                            iv.x + topLeftX * iv.width - handleSize / 2
                        cropHandleEnd?.x =
                            iv.x + bottomRightX * iv.width - handleSize / 2
                    }

                    editText?.clearFocus()

                    invalidate()

                } catch (e: Exception) {

                    e.printStackTrace()

                }
            }
        }
    }

    private fun initEditText() {

        //detect action ime done on keyboard
        editText?.setOnEditorActionListener { _, actionId, _ ->
            //on action done/ok
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitCoordinatesToUi()
            }
            true
        }
    }

    /**
     * Save the coordinates to preferences.
     */
    private fun initSaveButton() {

        saveButton?.setOnClickListener {

            vibrateUtil.vibrate(1L)
            vibrateUtil.vibrate(1L)

            try {
                cropImageHandler?.onCropImageSaved(editText?.text.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Copies the coordinates to user clipboard.
     */
    private fun initCopyButton() {

        copyButton?.setOnClickListener {

            vibrateUtil.vibrate(1L)
            vibrateUtil.vibrate(1L)

            try {
                //start a text share chooser
                val text = editText?.text.toString()
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = ClipData.newPlainText(context.getString(R.string.crop_image_clip_data_label), text)
                clipboard.setPrimaryClip(clip)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Resets the rect coordinates to what is saved currently.
     */
    private fun initResetButton() {

        resetButton?.setOnClickListener {

            vibrateUtil.vibrate(1L)
            vibrateUtil.vibrate(1L)

            //reset handles to default positions in preferences
            editText?.setText(cropImageHandler?.getCropCoordinates())

            submitCoordinatesToUi()

            invalidate()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun init() {

        /**
         * pass touch events to parent, mid has slightly less elevation to give others priority
         */
        cropHandleTop?.setOnTouchListener { _, _ ->
            handle = cropHandleTop
            false
        }

        cropHandleBottom?.setOnTouchListener { _, _ ->
            handle = cropHandleBottom
            false
        }

        cropHandleStart?.setOnTouchListener { _, _ ->
            handle = cropHandleStart
            false
        }

        cropHandleEnd?.setOnTouchListener { _, _ ->
            handle = cropHandleEnd
            false
        }

        cropHandleMid?.setOnTouchListener { _, _ ->
            handle = cropHandleMid
            false
        }

        /**
         * Handle touch events over the entire view, ignore some touch events that are outside the image
         */
        //set touch area to the size of the crop image view
        setOnTouchListener { _, event ->

            imageView?.let { iv ->

                //return early if views are null
                if (cropHandleBottom == null
                    || cropHandleTop == null
                    || cropHandleStart == null
                    || cropHandleEnd == null
                    || cropHandleMid == null) return@setOnTouchListener true

                //don't allow touch events below the image
                if (event.y > iv.y + iv.height + iv.marginBottom / 2) {
                    return@setOnTouchListener true
                }

                //detect the closest handle to the touch event on action down
                if (event.action == MotionEvent.ACTION_DOWN) {

                    vibrateUtil.vibrate(1L)

                    //find handle that the event is closest to
                    val topDistance = distance(event.x, event.y, cropHandleTop!!.x, cropHandleTop!!.y)
                    val bottomDistance =
                        distance(event.x, event.y, cropHandleBottom!!.x, cropHandleBottom!!.y)
                    val startDistance =
                        distance(event.x, event.y, cropHandleStart!!.x, cropHandleStart!!.y)
                    val endDistance = distance(event.x, event.y, cropHandleEnd!!.x, cropHandleEnd!!.y)
                    val midDistance = distance(event.x, event.y, cropHandleMid!!.x, cropHandleMid!!.y)

                    if (topDistance <= bottomDistance && topDistance <= startDistance && topDistance <= endDistance) {
                        handle = cropHandleTop
                    }

                    if (bottomDistance <= topDistance && bottomDistance <= startDistance && bottomDistance <= endDistance) {
                        handle = cropHandleBottom
                    }

                    if (startDistance <= topDistance && startDistance <= bottomDistance && startDistance <= endDistance) {
                        handle = cropHandleStart
                    }

                    if (endDistance <= topDistance && endDistance <= bottomDistance && endDistance <= startDistance) {
                        handle = cropHandleEnd
                    }

                    if (midDistance <= topDistance && midDistance <= bottomDistance && midDistance <= startDistance && midDistance <= endDistance) {
                        handle = cropHandleMid
                    }
                }

                //when touch drags, drag the handle that was closest on touch, but keep handle within bounds of image
                //middle handle drags the entire crop rectangle
                if (event.action == MotionEvent.ACTION_MOVE) {

                    handle?.let { h ->

                        if (h == cropHandleTop) {
                            h.y = event.y
                            if (h.y + handleSize / 2 < iv.y) {
                                h.y = iv.y - handleSize / 2
                            }
                        } else if (h == cropHandleBottom) {
                            h.y = event.y
                            if (h.y + handleSize / 2 > iv.height + iv.y) {
                                h.y = iv.height + iv.y - handleSize / 2
                            }
                        } else if (h == cropHandleStart) {
                            h.x = event.x
                            if (h.x < iv.x) {
                                h.x = iv.x - handleSize / 2
                            }
                        } else if (h == cropHandleEnd) {
                            h.x = event.x
                            if (h.x + handleSize / 2 > iv.width + iv.x) {
                                h.x = iv.width + iv.x - handleSize / 2
                            }
                        } else {
                            //middle handle position is updated, other handles are updated in onDraw
                            val deltaX = event.x - h.x
                            val deltaY = event.y - h.y

                            for (hand in setOf(
                                cropHandleTop,
                                cropHandleBottom,
                                cropHandleStart,
                                cropHandleEnd,
                                cropHandleMid
                            )) {
                                hand?.x = hand!!.x + deltaX
                                hand?.y = hand!!.y + deltaY
                            }
                        }

                        invalidate()
                    }

                }
            }

            true
        }
    }

    private fun updateEditTextValue() {

        try {
            val topLeftX = (cropHandleStart!!.x - imageView!!.x + handleSize / 2) / imageView!!.width
            val topLeftY = (cropHandleTop!!.y - imageView!!.y + handleSize / 2) / imageView!!.height
            val bottomRightX = (cropHandleEnd!!.x + handleSize / 2 - imageView!!.x) / imageView!!.width
            val bottomRightY =
                (cropHandleBottom!!.y - imageView!!.y + handleSize / 2) / imageView!!.height

            editText?.setText(
                String.format(
                    Locale.getDefault(),
                    "%.2f, %.2f, %.2f, %.2f",
                    topLeftX,
                    topLeftY,
                    bottomRightX,
                    bottomRightY
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        try {

            if (bitmap == null) {
                return
            }

            //update handles to be within the bounds of the image
            cropHandleTop?.y = round(
                max(
                    imageView!!.y - handleSize / 2,
                    min(cropHandleTop!!.y, imageView!!.height.toFloat() + imageView!!.y)
                )
            )
            cropHandleBottom?.y = round(
                max(
                    imageView!!.y,
                    min(cropHandleBottom!!.y, imageView!!.height.toFloat() + imageView!!.y)
                )
            )
            cropHandleStart?.x = round(
                max(
                    imageView!!.x - handleSize / 2,
                    min(cropHandleStart!!.x, imageView!!.width.toFloat() + imageView!!.x)
                )
            )
            cropHandleEnd?.x = round(
                max(
                    imageView!!.x,
                    min(cropHandleEnd!!.x, imageView!!.width.toFloat() + imageView!!.x)
                )
            )
            cropHandleMid?.x = round(
                max(
                    imageView!!.x,
                    min(cropHandleMid!!.x, imageView!!.width.toFloat() + imageView!!.x)
                )
            )
            cropHandleMid?.y = round(
                max(
                    imageView!!.y,
                    min(cropHandleMid!!.y, imageView!!.height.toFloat() + imageView!!.y)
                )
            )

            //update handles to not cross each other
            if (cropHandleTop!!.y > cropHandleBottom!!.y - MIN_DISTANCE_BETWEEN_HANDLES) {
                cropHandleTop!!.y = round(cropHandleBottom!!.y - MIN_DISTANCE_BETWEEN_HANDLES)
            }

            if (cropHandleStart!!.x > cropHandleEnd!!.x - MIN_DISTANCE_BETWEEN_HANDLES) {
                cropHandleStart!!.x = round(cropHandleEnd!!.x - MIN_DISTANCE_BETWEEN_HANDLES)
            }

            if (cropHandleBottom!!.y < cropHandleTop!!.y + MIN_DISTANCE_BETWEEN_HANDLES) {
                cropHandleBottom!!.y = round(cropHandleTop!!.y + MIN_DISTANCE_BETWEEN_HANDLES)
            }

            if (cropHandleEnd!!.x < cropHandleStart!!.x + MIN_DISTANCE_BETWEEN_HANDLES) {
                cropHandleEnd!!.x = round(cropHandleStart!!.x + MIN_DISTANCE_BETWEEN_HANDLES)
            }

            //get midpoints
            val midX = round((cropHandleStart!!.x + cropHandleEnd!!.x) / 2)
            val midY = round((cropHandleTop!!.y + cropHandleBottom!!.y) / 2)

            //set points to be middle of rect lines
            cropHandleTop!!.x = midX
            cropHandleBottom!!.x = midX
            cropHandleStart!!.y = midY
            cropHandleEnd!!.y = midY

            //update mid handle to the mid point
            cropHandleMid!!.x = midX
            cropHandleMid!!.y = midY

            //draw a rectangle between the two handles, notice relative to the imageView using OverlayImageView
            //otherwise canvas draws underneath the image
            //also add offset so rectangle intersects middle of handles
            imageView?.drawRectangle(
                bitmap!!, imageView!!.x, imageView!!.y, imageView!!.width, imageView!!.height,
                cropHandleStart!!.x + handleSize / 2,
                cropHandleTop!!.y + handleSize / 2,
                cropHandleEnd!!.x + handleSize / 2,
                cropHandleBottom!!.y + handleSize / 2
            )

            updateEditTextValue()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}