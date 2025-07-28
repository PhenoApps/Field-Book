package com.fieldbook.tracker.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.database.sqlite.SQLiteAbortException
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.Group
import androidx.fragment.app.DialogFragment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.FieldEditorActivity
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.ImportFormat
import com.fieldbook.tracker.utilities.FieldConfig
import com.fieldbook.tracker.utilities.FieldPattern
import com.fieldbook.tracker.utilities.FieldPlotCalculator
import com.fieldbook.tracker.utilities.FieldStartCorner
import com.fieldbook.tracker.views.FieldCreationStep
import com.fieldbook.tracker.views.FieldCreatorStepper
import com.fieldbook.tracker.views.FieldPreviewGrid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Extended Dialog class. Wizard for creating basic study/field details.
 * Each constraint group defined in field_creator_dialog.xml has a respective function which
 * starts by toggling its visibility.
 * Size group: User chooses a name which must not already exist, and row and column integer > 0
 * Start group: User chooses a starting point top left, top right, bottom left, bottom right in the field preview grid
 * Pattern group: Four buttons are used to choose from a combination of horizontal/vertical walking path paired with linear/zigzag pattern
 * Review group: Field preview grid, press OK to insert into database.
 *
 *                 FieldCreatorDialog dialog = new FieldCreatorDialog(this);
 */
class FieldCreatorDialogFragment(private val activity: ThemedActivity) :
    DialogFragment(), CoroutineScope by MainScope() {

    private val helper by lazy { (activity as FieldEditorActivity).getDatabase() }

    private val scope by lazy { CoroutineScope(Dispatchers.IO) }

    private var titleTextView: TextView? = null

    var studyDbId: Int = -1

    var mCancelJobFlag = false

    var fieldCreatorDialog: AlertDialog? = null

    // stepper ui index
    private var fieldCreatorStep by mutableStateOf(FieldCreationStep.FIELD_SIZE)

    // grid preview colors
    private var cellTextColor: Int = 0
    private var headerCellBgColor: Int = 0
    private var cellBgColor: Int = 0

    private var fieldConfig by mutableStateOf(
        FieldConfig(
            rows = 0,
            cols = 0,
            pattern = FieldPattern.HORIZONTAL_LINEAR,
            startCorner = FieldStartCorner.TOP_LEFT,
            cellTextColor = 0,
            headerCellBgColor = 0,
            cellBgColor = 0
        )
    )

    // Inside FieldCreatorDialog class
    interface FieldCreationCallback {
        fun onFieldCreated(studyDbId: Int)
    }

    var fieldCreationCallback: FieldCreationCallback? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(context, R.style.AppAlertDialog)
        builder.setNegativeButton(getString(R.string.dialog_cancel), null)

        builder.setPositiveButton(getString(R.string.dialog_ok), null)

        builder.setNeutralButton(getString(R.string.dialog_back), null)


        val inflater = this.getLayoutInflater()
        val view = inflater.inflate(R.layout.dialog_field_creator, null)
        builder.setView(view)

        fieldCreatorDialog = builder.create()
        fieldCreatorDialog?.show()

        titleTextView = view.findViewById(R.id.dialog_field_creator_title)
        val stepperComposeView = view.findViewById<ComposeView>(R.id.dialog_field_creator_stepper)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stepperComposeView?.setContent {
                MaterialTheme {
                    FieldCreatorStepper(fieldCreatorStep)
                }
            }
            stepperComposeView?.visibility = View.VISIBLE
            titleTextView?.visibility = View.GONE
        } else {
            stepperComposeView?.visibility = View.GONE
            titleTextView?.visibility = View.VISIBLE
        }

        setupSizeGroup()

        initializeThemeColors()

        return fieldCreatorDialog!!
    }

    private fun initializeThemeColors() {
        val typedValue = TypedValue()
        val theme = context?.theme
        theme?.resolveAttribute(R.attr.cellTextColor, typedValue, true)
        cellTextColor = typedValue.data
        theme?.resolveAttribute(R.attr.emptyCellColor, typedValue, true)
        cellBgColor = typedValue.data
        headerCellBgColor = android.graphics.Color.WHITE

        fieldConfig = fieldConfig.copy(
            cellTextColor = cellTextColor,
            headerCellBgColor = headerCellBgColor,
            cellBgColor = cellBgColor
        )
    }

    //the initial step that asks the user for row/column size of their field
    //row*column plots will be generated based on a chosen pattern
    private fun setupSizeGroup() {
        fieldCreatorStep = FieldCreationStep.FIELD_SIZE

        titleTextView?.setText(R.string.field_creator_size_title)

        val sizeGroup = fieldCreatorDialog?.findViewById<Group>(R.id.dialog_field_creator_group_size)

        val rowsEditText = fieldCreatorDialog?.findViewById<EditText>(R.id.dialog_field_creator_field_row_edit_text)
        val colsEditText = fieldCreatorDialog?.findViewById<EditText>(R.id.dialog_field_creator_field_column_edit_text)
        val nameEditText = fieldCreatorDialog?.findViewById<EditText>(R.id.dialog_field_creator_field_name_edit_text)

        val submitSizeButton = fieldCreatorDialog?.getButton(AlertDialog.BUTTON_POSITIVE)
        val cancelButton = fieldCreatorDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)
        val neutralButton = fieldCreatorDialog?.getButton(AlertDialog.BUTTON_NEUTRAL)

        submitSizeButton?.visibility = View.VISIBLE
        cancelButton?.visibility = View.VISIBLE
        neutralButton?.visibility = View.GONE

        cancelButton?.setOnClickListener {
            dismiss()
        }


        sizeGroup?.visibility = View.VISIBLE

        // set EditText hint color
        val typedValue = TypedValue()
        val theme = context?.theme
        theme?.resolveAttribute(R.attr.fb_color_hint_text, typedValue, true)
        val hintColor = typedValue.data

        rowsEditText?.setHintTextColor(hintColor)
        colsEditText?.setHintTextColor(hintColor)
        nameEditText?.setHintTextColor(hintColor)

        //when the size OK button is pressed...
        submitSizeButton?.setOnClickListener {

            //check that the rows/columns aren't empty
            val rowsText = rowsEditText?.text
            val colsText = colsEditText?.text
            val nameText = nameEditText?.text
            if (!nameText.isNullOrBlank() && !rowsText.isNullOrBlank() && !colsText.isNullOrBlank()) {

                val nameExists = helper.allFieldObjects.any { it.name == nameText.toString() }

                if (!nameExists) {

                    //check that the text can be parsed as a whole number and is greater than 0
                    try {

                        val rows = rowsText.toString().toInt()

                        val cols = colsText.toString().toInt()

                        if (rows < 1 || cols < 1
                            || rows > Int.MAX_VALUE
                            || cols > Int.MAX_VALUE) throw java.lang.NumberFormatException()

                        //change current group visibility before setting up next group
                        sizeGroup?.visibility = View.GONE

                        fieldConfig = fieldConfig.copy(rows = rows, cols = cols)

                        setupStartingPointSelection(nameText.toString())

                    } catch (e: NumberFormatException) {

                        e.printStackTrace()

                    }

                } else {

                    Toast.makeText(activity, R.string.dialog_field_creator_field_name_not_unique, Toast.LENGTH_LONG).show()

                    nameText.clear()
                }

            } else {

                Toast.makeText(activity, R.string.dialog_field_creator_size_group_error, Toast.LENGTH_LONG).show()

            }
        }
    }

    private fun setupStartingPointSelection(name: String) {
        fieldCreatorStep = FieldCreationStep.START_POINT

        titleTextView?.setText(R.string.field_creator_start_corner_title)

        val group = fieldCreatorDialog?.findViewById<Group>(R.id.dialog_field_creator_group_start_point)

        group?.visibility = View.VISIBLE

        val positiveButton = fieldCreatorDialog?.getButton(AlertDialog.BUTTON_POSITIVE)
        val negativeButton = fieldCreatorDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)
        val backButton = fieldCreatorDialog?.getButton(AlertDialog.BUTTON_NEUTRAL)

        positiveButton?.visibility = View.GONE
        negativeButton?.visibility = View.GONE
        backButton?.visibility = View.VISIBLE

        backButton?.setOnClickListener {

            group?.visibility = View.GONE

            setupSizeGroup()
        }

        val composeView = fieldCreatorDialog?.findViewById<ComposeView>(R.id.dialog_field_preview_starting_point)
        composeView?.visibility = View.VISIBLE


        composeView?.setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    FieldPreviewGrid(
                        config = fieldConfig.copy(showHeaders = true),
                        onCornerSelected = { corner ->
                            fieldConfig = fieldConfig.copy(startCorner = corner)
                            composeView.visibility = View.GONE
                            setupPatternGroup(name)
                        },
                        showPlotNumbers = false
                    )
                }
            }
        }
    }

    private fun setupPatternGroup(name: String) {
        fieldCreatorStep = FieldCreationStep.WALKING_PATTERN

        titleTextView?.setText(R.string.field_creator_pattern_title)

        val patternGroup = fieldCreatorDialog?.findViewById<Group>(R.id.dialog_field_creator_group_pattern)

        val horizontalLinearBtn = fieldCreatorDialog?.findViewById<ImageButton>(R.id.plot_horizontal_linear_button)
        val horizontalZigzagBtn = fieldCreatorDialog?.findViewById<ImageButton>(R.id.plot_horizontal_zigzag_button)
        val verticalLinearBtn = fieldCreatorDialog?.findViewById<ImageButton>(R.id.plot_vertical_linear_button)
        val verticalZigzagBtn = fieldCreatorDialog?.findViewById<ImageButton>(R.id.plot_vertical_zigzag_button)

        val positiveButton = fieldCreatorDialog?.getButton(AlertDialog.BUTTON_POSITIVE)
        val negativeButton = fieldCreatorDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)
        val backButton = fieldCreatorDialog?.getButton(AlertDialog.BUTTON_NEUTRAL)

        positiveButton?.visibility = View.GONE
        negativeButton?.visibility = View.GONE
        backButton?.visibility = View.VISIBLE

        backButton?.setOnClickListener {

            patternGroup?.visibility = View.GONE

            setupStartingPointSelection(name)
        }

        horizontalLinearBtn?.setOnClickListener {
            fieldConfig = fieldConfig.copy(pattern = FieldPattern.HORIZONTAL_LINEAR)
            hidePatternAndReview(patternGroup, name)
        }
        horizontalZigzagBtn?.setOnClickListener {
            fieldConfig = fieldConfig.copy(pattern = FieldPattern.HORIZONTAL_ZIGZAG)
            hidePatternAndReview(patternGroup, name)
        }
        verticalLinearBtn?.setOnClickListener {
            fieldConfig = fieldConfig.copy(pattern = FieldPattern.VERTICAL_LINEAR)
            hidePatternAndReview(patternGroup, name)
        }
        verticalZigzagBtn?.setOnClickListener {
            fieldConfig = fieldConfig.copy(pattern = FieldPattern.VERTICAL_ZIGZAG)
            hidePatternAndReview(patternGroup, name)
        }

        patternGroup?.visibility = View.VISIBLE
    }

    private fun hidePatternAndReview(patternGroup: Group?, name:String) {
        patternGroup?.visibility = View.GONE
        setupReviewGroup(name)
    }


    private fun setupReviewGroup(name: String) {
        fieldCreatorStep = FieldCreationStep.FIELD_PREVIEW

        titleTextView?.text = context?.getString(R.string.dialog_field_creator_review_title)

        //set the group visibility
        val reviewGroup = fieldCreatorDialog?.findViewById<Group>(R.id.dialog_field_creator_group_review)
        reviewGroup?.visibility = View.VISIBLE

        //initialize ui view objects
        val reviewTitleText = fieldCreatorDialog?.findViewById<TextView>(R.id.dialog_field_creator_review_text)
        //val reviewInsertText = findViewById<TextView>(R.id.field_creator_plot_insert_text_view)
        val largeTextView = fieldCreatorDialog?.findViewById<TextView>(R.id.field_creator_plot_large_field_text_view)
        val submitButton = fieldCreatorDialog?.getButton(AlertDialog.BUTTON_POSITIVE)
        val cancelButton = fieldCreatorDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)
        val backButton = fieldCreatorDialog?.getButton(AlertDialog.BUTTON_NEUTRAL)

        //warning if numbers are greater than 50
        if (fieldConfig.rows * fieldConfig.cols > 50*50) {
            largeTextView?.text = activity.getString(R.string.dialog_field_creator_large_field)
        }

        val composeView = fieldCreatorDialog?.findViewById<ComposeView>(R.id.dialog_field_creator_grid_preview)
        composeView?.visibility = View.VISIBLE
        composeView?.setContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                FieldPreviewGrid(
                    config = fieldConfig.copy(showHeaders = false),
                    showPlotNumbers = true,
                    selectedCorner = fieldConfig.startCorner
                )
            }
        }

        //set field format review, shows rows and columns
        reviewTitleText?.text = activity.getString(R.string.dialog_field_creator_insert_field,
            fieldConfig.rows.toString(), fieldConfig.cols.toString())

        submitButton?.visibility = View.VISIBLE
        cancelButton?.visibility = View.VISIBLE
        backButton?.visibility = View.VISIBLE

        //dismiss the dialog if the user clicks the cancel button, otherwise insert the data
        cancelButton?.setOnClickListener {
            dismiss()
        }

        backButton?.setOnClickListener {

            reviewGroup?.visibility = View.GONE

            setupPatternGroup(name)

        }

        submitButton?.setOnClickListener {

            it.isEnabled = false //no accidental double clicks...

            backButton?.isEnabled = false //no going back now

            fieldCreatorStep = FieldCreationStep.COMPLETED

            insertBasicField(name)
        }

    }

    //launch database IO coroutine that creates the field and its data
//    private fun insertBasicField(name: String, rows: Int, cols: Int, startId: Int, pattern: Int) {

    private fun insertBasicField(name: String) {
        Log.d("FieldCreatorDialog", "Starting to insert basic field with name: $name")
        //insert job is cancelled when the cancel button is pressed
        val cancelButton = fieldCreatorDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)
        cancelButton?.setOnClickListener {
            mCancelJobFlag = true
            Log.d("FieldCreatorDialog", "New field insert job cancelled by user.")
        }

        scope.launch {
            // Database operation might be time-consuming, so we run it on the IO dispatcher
            val createdFieldId = withContext(Dispatchers.IO) {
                helper.open()
                DataHelper.db.beginTransaction()
                try {
                    Log.d("FieldCreatorDialog", "Inserting new field in the database.")

                    val field = FieldObject().apply {
                        uniqueId = "plot_id"
                        primaryId = "Row"
                        secondaryId = "Column"
                        sortColumnsStringArray = "Plot"
                        this.name = name
                        alias = name
                        dataSource = activity.getString(R.string.field_book)
                        dataSourceFormat = ImportFormat.INTERNAL
                        entryCount = (fieldConfig.rows * fieldConfig.cols).toString()
                    }

                    val fieldColumns = listOf(
                        "Row",
                        "Column",
                        "Plot",
                        "plot_id",
                        "position_coordinate_x_type",
                        "position_coordinate_y_type",
                        "position_coordinate_x",
                        "position_coordinate_y"
                    )
                    studyDbId = helper.createField(field, fieldColumns, false)

                    updateFieldInsertText(fieldConfig.rows.toString(), fieldConfig.cols.toString())

                    insertPlotData(fieldColumns)

                    DataHelper.db.setTransactionSuccessful()
                    studyDbId // Return the new study ID

                } catch (e: Exception) {
                    Log.e("FieldCreatorDialog", "Exception during new field insertion: ${e.message}", e)
                    -1 // Indicate failure
                } finally {
                    DataHelper.db.endTransaction()
                }
            }

            if (studyDbId != -1) {
                // Switch to the Main dispatcher for UI operations
                withContext(Dispatchers.Main) {
                    Log.d("FieldCreatorDialog", "New field insertion successful, invoking callback with new field id: $studyDbId")
                    fieldCreationCallback?.onFieldCreated(createdFieldId)
                }
            }
            dismiss()

        }
    }

    private fun updateFieldInsertText(row: String, col: String) = activity.runOnUiThread {
        fieldCreatorDialog?.findViewById<TextView>(R.id.dialog_field_creator_review_text)?.text =
            activity.getString(R.string.dialog_field_creator_insert_field_complete, row, col)
    }

    private fun updatePlotInsertText(row: String, col: String, index: String) = activity.runOnUiThread {
        fieldCreatorDialog?.findViewById<TextView>(R.id.field_creator_plot_insert_text_view)?.text =
            activity.getString(R.string.dialog_field_creator_insert_plot, index, row, col)
    }

    //insert a unique id plot and notify the ui
    private fun insertPlotData(fieldColumns: List<String>, i: Int, j: Int, k: Int,
                               uuid: String = UUID.randomUUID().toString()) {

        val row = i.toString()
        val col = j.toString()
        val index = k.toString()

        val (posX, posY) = FieldPlotCalculator.calculatePositionCoordinatesOld(i, j, fieldConfig)

        val values = listOf(row, col, index, uuid, "x_coordinate", "y_coordinate", posX.toString(), posY.toString())

        helper.createFieldData(studyDbId, fieldColumns, values)

        updatePlotInsertText(row, col, index)

    }

    //basic nested for loop to fill the field
    //General function for both linear and zigzag patterns
    //top-to-bottom parameter is used to change vertical orientation
    //left-to-right parameter is used to change horizontal orientation
    //zigzag patterns use an additional variable to toggle the "sow" direction
    private fun insertPlotData(fieldColumns: List<String>) {
        var plotIndex = 0
        val pattern = fieldConfig.pattern

        val rows = fieldConfig.rows
        val cols = fieldConfig.cols

        // add position coordinate attributes ONCE to the attribute table
        // helper.insertPositionCoordinateAttributes(studyDbId)

        when (pattern) {
            FieldPattern.HORIZONTAL_LINEAR, FieldPattern.HORIZONTAL_ZIGZAG -> {
                var ltr = true
                for (i in 1..rows) {// outer: rows
                    for (j in if (ltr) 1..cols else cols downTo 1) { // inner: cols, L→R or R→L
                        plotIndex++
                        insertPlotData(fieldColumns, i, j ,plotIndex)
                        if (mCancelJobFlag) throw SQLiteAbortException()
                    }
                    //flip the direction before iterating over columns again
                    if (pattern == FieldPattern.HORIZONTAL_ZIGZAG) {
                        ltr = !ltr
                    }
                }
            }
            FieldPattern.VERTICAL_LINEAR, FieldPattern.VERTICAL_ZIGZAG -> {
                var topToBottom = true
                for (j in 1..cols) {// outer: cols
                    for (i in if (topToBottom) 1..rows else rows downTo 1) { // inner: rows, T→B or B→T
                        plotIndex++
                        insertPlotData(fieldColumns, i, j, plotIndex)
                        if (mCancelJobFlag) throw SQLiteAbortException()
                    }
                    //flip the direction before iterating over columns again
                    if (pattern == FieldPattern.VERTICAL_ZIGZAG) {
                        topToBottom = !topToBottom
                    }
                }
            }
        }
    }
}