package com.fieldbook.tracker.dialogs

import android.database.sqlite.SQLiteAbortException
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.Group
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.ImportFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Extended Dialog class. Wizard for creating basic study/field details.
 * Each constraint group defined in field_creator_dialog.xml has a respective function which
 * starts by toggling its visibility.
 * Size group: User chooses a name which must not already exist, and row and column integer > 0
 * Start group: Four radio buttons to choose a starting point top left, top right, bottom left, bottom right
 * Pattern group: Two buttons are used to choose a linear or zigzag pattern
 * Review group: Summary of the chosen parameters, press OK to insert into database.
 *
 *                 FieldCreatorDialog dialog = new FieldCreatorDialog(this);
 */
class FieldCreatorDialog(private val activity: ThemedActivity) :
    BorderedDialog(activity, R.style.AppAlertDialog), CoroutineScope by MainScope() {

    private val helper by lazy { DataHelper(context) }

    private val scope by lazy { CoroutineScope(Dispatchers.IO) }

    private var titleTextView: TextView? = null

    var studyDbId: Int = -1

    var mCancelJobFlag = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.dialog_field_creator)

        titleTextView = findViewById(R.id.dialog_field_creator_title)

        //canceling the dialog might leave the insert job on the background thread
        //which might be preferable for large plot sizes
        //setCancelable(false)

        setCanceledOnTouchOutside(true)

        //for some reason this is required for the views to match the layout file
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        setupSizeGroup()

    }

    //the initial step that asks the user for row/column size of their field
    //row*column plots will be generated based on a chosen pattern
    private fun setupSizeGroup() {

        titleTextView?.setText(R.string.dialog_field_creator_ask_size)

        val sizeGroup = findViewById<Group>(R.id.dialog_field_creator_group_size)

        val submitSizeButton = findViewById<Button>(R.id.dialog_field_creator_size_button)

        sizeGroup.visibility = View.VISIBLE

        //when the size OK button is pressed...
        submitSizeButton.setOnClickListener {

            //check that the rows/columns aren't empty
            val rowsText = findViewById<EditText>(R.id.dialog_field_creator_field_row_edit_text).text
            val colsText = findViewById<EditText>(R.id.dialog_field_creator_field_column_edit_text).text
            val nameText = findViewById<EditText>(R.id.dialog_field_creator_field_name_edit_text).text
            if (nameText.isNotBlank() && rowsText.isNotBlank() && colsText.isNotBlank()) {

                //check that the text can be parsed as a whole number and is greater than 0
                try {

                    //use database to check if the field name is unique
                    if (helper.checkFieldName(nameText.toString()) == -1) {

                        val rows = rowsText.toString().toInt()

                        val cols = colsText.toString().toInt()

                        if (rows < 1 || cols < 1
                            || rows > Int.MAX_VALUE
                            || cols > Int.MAX_VALUE) throw java.lang.NumberFormatException()

                        //change current group visibility before setting up next group
                        sizeGroup.visibility = View.GONE

                        setupRadioGroup(nameText.toString(), rows, cols)

                    } else {

                        Toast.makeText(activity, R.string.dialog_field_creator_field_name_not_unique, Toast.LENGTH_LONG).show()

                        nameText.clear()
                    }


                } catch (e: NumberFormatException) {

                    e.printStackTrace()

                }

            } else Toast.makeText(activity, R.string.dialog_field_creator_size_group_error, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupRadioGroup(name: String, rows: Int, cols: Int) {

        titleTextView?.setText(R.string.dialog_field_creator_ask_start_point)

        val group = findViewById<Group>(R.id.dialog_field_creator_group_start_point)

        group.visibility = View.VISIBLE

        val backButton = findViewById<Button>(R.id.dialog_field_creator_start_back_button)

        backButton.setOnClickListener {

            group.visibility = View.GONE

            setupSizeGroup()
        }

        //set click listeners for the buttons, when one is pressed switch to the next group
        group.referencedIds.forEach { id ->

            (findViewById(id) as? RadioButton)?.apply {

                isChecked = false
            }
        }

        //set click listeners for the buttons, when one is pressed switch to the next group
        group.referencedIds.forEach { id ->

            (findViewById(id) as? RadioButton)?.apply {

                setOnClickListener {

                    group.visibility = View.GONE

                    setupPatternGroup(name, rows, cols, this.id)

                }
            }
        }
    }

    private fun setupPatternGroup(name: String, rows: Int, cols: Int, startId: Int) {

        titleTextView?.setText(R.string.dialog_field_creator_ask_pattern)

        val patternGroup = findViewById<Group>(R.id.dialog_field_creator_group_pattern)

        val linearButton = findViewById<ImageButton>(R.id.plot_linear_button)
        val zigButton = findViewById<ImageButton>(R.id.plot_zigzag_button)

        val backButton = findViewById<Button>(R.id.dialog_field_creator_pattern_back_button)

        backButton.setOnClickListener {

            patternGroup.visibility = View.GONE

            setupRadioGroup(name, rows, cols)
        }

        //rotate the image patterns based on the chosen starting point
        when (startId) {
            R.id.dialog_field_creator_top_right_radio_button,
            R.id.dialog_field_creator_bottom_right_radio_button -> {
                linearButton.scaleX = -1.0f
                linearButton.scaleY = -1.0f

                if (startId == R.id.dialog_field_creator_bottom_right_radio_button) {
                    zigButton.scaleY = -1.0f
                }
                zigButton.scaleX = -1.0f
            }
            R.id.dialog_field_creator_bottom_left_radio_button -> {
                zigButton.scaleY = -1.0f
            }
        }

        patternGroup.visibility = View.VISIBLE

        patternGroup.referencedIds.forEach { id ->

            (findViewById(id) as? ImageButton)?.setOnClickListener {

                patternGroup.visibility = View.GONE

                setupReviewGroup(name, rows, cols, startId, id)
            }
        }
    }

    private fun setupReviewGroup(name: String, rows: Int, cols: Int, startId: Int, pattern: Int) {

        titleTextView?.text = context.getString(R.string.dialog_field_creator_review_title)

        //set the group visibility
        val reviewGroup = findViewById<Group>(R.id.dialog_field_creator_group_review)
        reviewGroup.visibility = View.VISIBLE

        //initialize ui view objects
        val reviewTitleText = findViewById<TextView>(R.id.dialog_field_creator_review_text)
        //val reviewInsertText = findViewById<TextView>(R.id.field_creator_plot_insert_text_view)
        val largeTextView = findViewById<TextView>(R.id.field_creator_plot_large_field_text_view)
        val submitButton = findViewById<Button>(R.id.dialog_field_creator_submit_button)
        val cancelButton = findViewById<Button>(R.id.dialog_field_creator_cancel_button)
        val backButton = findViewById<Button>(R.id.dialog_field_creator_back_button)
        val imageView = findViewById<ImageView>(R.id.dialog_field_creator_review_image)

        //warning if numbers are greater than 50
        if (rows*cols > 50*50) {
            largeTextView.text = activity.getString(R.string.dialog_field_creator_large_field)
        }

        //set current plow(?) pattern: use rotation
        when (pattern) {
            R.id.plot_linear_button -> {

                imageView.setImageResource(R.drawable.ic_plot_pattern_linear)

                //mirror linear pattern when starting on the right
                when (startId) {
                    R.id.dialog_field_creator_top_right_radio_button,
                    R.id.dialog_field_creator_bottom_right_radio_button -> {
                        imageView.scaleX = -1.0f
                        imageView.scaleY = -1.0f
                    }
                }
            }
            R.id.plot_zigzag_button -> {

                imageView.setImageResource(R.drawable.ic_plot_pattern_zigzag)

                //mirror or flip zig zag pattern
                when (startId) {
                    R.id.dialog_field_creator_top_right_radio_button,
                    R.id.dialog_field_creator_bottom_right_radio_button -> {
                        if (startId == R.id.dialog_field_creator_bottom_right_radio_button) {
                            imageView.scaleY = -1.0f
                        }
                        imageView.scaleX = -1.0f
                    }
                    R.id.dialog_field_creator_bottom_left_radio_button -> {
                        imageView.scaleY = -1.0f
                    }
                }
            }
        }

        //set field format review, shows rows and columns
        reviewTitleText.text = activity.getString(R.string.dialog_field_creator_insert_field,
            rows.toString(), cols.toString())

        //dismiss the dialog if the user clicks the cancel button, otherwise insert the data
        cancelButton.setOnClickListener {
            dismiss()
        }

        backButton.setOnClickListener {

            reviewGroup.visibility = View.GONE

            setupPatternGroup(name, rows, cols, startId)

        }

        submitButton.setOnClickListener {

            it.isEnabled = false //no accidental double clicks...

            backButton.isEnabled = false //no going back now

            insertBasicField(name, rows, cols, pattern)
        }

    }

    //launch database IO coroutine that creates the field and its data
//    private fun insertBasicField(name: String, rows: Int, cols: Int, startId: Int, pattern: Int) {

    private fun insertBasicField(name: String, rows: Int, cols: Int, pattern: Int) {

        //insert job is cancelled when the cancel button is pressed
        val cancelButton = findViewById<Button>(R.id.dialog_field_creator_cancel_button)
        cancelButton.setOnClickListener { mCancelJobFlag = true }

        scope.launch {

            with(DataHelper.db) {

                try {

                    beginTransaction()

                    val field = FieldObject().apply {
                        unique_id = "plot_id"
                        primary_id = "Row"
                        secondary_id = "Column"
                        exp_sort = "Plot"
                        exp_name = name
                        exp_alias = name
                        exp_source = activity.getString(R.string.field_book)
                        import_format = ImportFormat.INTERNAL
                        count = (rows * cols).toString()
                    }

                    val fieldColumns = listOf("Row", "Column", "Plot", "plot_id")
                    studyDbId = helper.createField(field, fieldColumns)

                    updateFieldInsertText(rows.toString(), cols.toString())

                    insertPlotData(
                            fieldColumns,
                            rows,
                            cols,
                            linear = pattern == R.id.plot_linear_button
                    )

                    //eight different cases to consider, P = patterns (linear and zigzag), S = starting states (TL, BR, TR, BL)
//                    when (startId) {
//                        R.id.dialog_field_creator_top_left_radio_button -> {
//                            if (pattern == R.id.plot_linear_button) insertPlotData(
//                                fieldColumns,
//                                rows,
//                                cols
//                            )
//                            else insertPlotData(fieldColumns, rows, cols, linear = false)
//                        }
//                        R.id.dialog_field_creator_bottom_left_radio_button -> {
//                            if (pattern == R.id.plot_linear_button) insertPlotData(
//                                fieldColumns,
//                                rows,
//                                cols,
//                                ttb = false
//                            )
//                            else insertPlotData(fieldColumns, rows, cols, linear = false, ttb = false)
//                        }
//                        R.id.dialog_field_creator_top_right_radio_button -> {
//                            if (pattern == R.id.plot_linear_button) insertPlotData(
//                                fieldColumns,
//                                rows,
//                                cols,
//                                ltr = false
//                            )
//                            else insertPlotData(fieldColumns, rows, cols, linear = false, ltr = false)
//                        }
//                        else -> {
//                            if (pattern == R.id.plot_linear_button) insertPlotData(
//                                fieldColumns,
//                                rows,
//                                cols,
//                                ttb = false,
//                                ltr = false
//                            )
//                            else insertPlotData(
//                                fieldColumns,
//                                rows,
//                                cols,
//                                linear = false,
//                                ttb = false,
//                                ltr = false
//                            )
//                        }
//                    }

                    setTransactionSuccessful()

                } catch (e: SQLiteAbortException) {

                    e.printStackTrace()

                } finally {

                    endTransaction()

                    this@FieldCreatorDialog.dismiss()

                }
            }
        }
    }

    private fun updateFieldInsertText(row: String, col: String) = activity.runOnUiThread {
        findViewById<TextView>(R.id.dialog_field_creator_review_text).text =
            activity.getString(R.string.dialog_field_creator_insert_field_complete, row, col)
    }

    private fun updatePlotInsertText(row: String, col: String, index: String) = activity.runOnUiThread {
        findViewById<TextView>(R.id.field_creator_plot_insert_text_view).text =
            activity.getString(R.string.dialog_field_creator_insert_plot, index, row, col)
    }

    //insert a unique id plot and notify the ui
    private fun insertPlotData(fieldColumns: List<String>, i: Int, j: Int, k: Int,
                               uuid: String = UUID.randomUUID().toString()) {

        val row = i.toString()
        val col = j.toString()
        val index = k.toString()

        helper.createFieldData(studyDbId, fieldColumns, listOf(row, col, index, uuid))

        updatePlotInsertText(row, col, index)

    }

    //basic nested for loop to fill the field
    //General function for both linear and zigzag patterns
    //top-to-bottom parameter is used to change vertical orientation
    //left-to-right parameter is used to change horizontal orientation
    //zigzag patterns use an additional variable to toggle the "sow" direction
    private fun insertPlotData(fieldColumns: List<String>, rows: Int, cols: Int,
                                     linear: Boolean = true, ttb: Boolean = true, ltr: Boolean = true) {

        var direction = ltr
        var plotIndex = 0
        for (i in if (ttb) 1 until rows+1 else rows downTo 1) {

            for (j in if (direction) 1 until cols+1 else cols downTo 1) {

                plotIndex += 1

                insertPlotData(fieldColumns, i, j, plotIndex)

                //throws an exception which is caught in a transaction
                if (mCancelJobFlag) throw SQLiteAbortException()

            }

            //flip the direction before iterating over columns again
            if (!linear) direction = !direction
        }
    }
}