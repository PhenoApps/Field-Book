package com.fieldbook.tracker.dialogs

import android.app.Dialog
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.Group
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.FieldEditorActivity
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.withDatabase
import com.fieldbook.tracker.location.GPSTracker
import com.fieldbook.tracker.objects.FieldObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.*

class FieldCreatorDialog(private val activity: FieldEditorActivity) : Dialog(activity), CoroutineScope by MainScope() {

    private val helper by lazy { DataHelper(context) }

    private val database by lazy { DataHelper.db }

    private val scope by lazy { CoroutineScope(Dispatchers.IO) }

    private val gps by lazy { GPSTracker(context) }

    var studyDbId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.field_creator_dialog)

        setupSizeGroup()

    }

    //the initial step that asks the user for row/column size of their field
    //row*column plots will be generated based on a chosen pattern
    private fun setupSizeGroup() {

        val sizeGroup = findViewById<Group>(R.id.dialog_field_creator_group_size)

        val submitSizeButton = findViewById<Button>(R.id.dialog_field_creator_size_button)

        //when the size OK button is pressed...
        submitSizeButton.setOnClickListener {

            //check that the rows/columns aren't empty
            val rowsText = findViewById<EditText>(R.id.dialog_field_creator_field_row_edit_text).text
            val colsText = findViewById<EditText>(R.id.dialog_field_creator_field_column_edit_text).text

            if (rowsText.isNotBlank() && colsText.isNotBlank()) {

                //check that the text can be parsed as a whole number and is greater than 0
                try {

                    val rows = rowsText.toString().toInt()

                    val cols = colsText.toString().toInt()

                    if (rows < 1 || cols < 1) throw java.lang.NumberFormatException()

                    //change current group visibility before setting up next group
                    sizeGroup.visibility = View.GONE

                    setupRadioGroup(rows, cols)

                } catch (e: NumberFormatException) {

                    e.printStackTrace()

                } finally { //quit by clearing the edit texts

                    rowsText.clear()

                    colsText.clear()
                }
            }
        }
    }

    private fun setupRadioGroup(rows: Int, cols: Int) {

        val radioGroup = findViewById<Group>(R.id.dialog_field_creator_group_start_point)

        radioGroup.visibility = View.VISIBLE

        //set click listeners for the buttons, when one is pressed switch to the next group
        radioGroup.referencedIds.forEach { id ->

            (findViewById(id) as? RadioButton)?.apply {

                setOnClickListener {

                    radioGroup.visibility = View.GONE

                    setupPatternGroup(rows, cols, id)
                }
            }
        }
    }

    private fun setupPatternGroup(rows: Int, cols: Int, startId: Int) {

        val patternGroup = findViewById<Group>(R.id.dialog_field_creator_group_pattern)

        val linearButton = findViewById<ImageButton>(R.id.plot_linear_button)
        val zigButton = findViewById<ImageButton>(R.id.plot_zigzag_button)

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

                insertBasicField(rows, cols, startId, id)

            }
        }
    }

    private fun insertBasicField(rows: Int, cols: Int, startId: Int, pattern: Int) {

        scope.launch {

            val field = FieldObject().apply {
                unique_id = "observationUnitDbId"
                primary_id = "Row"
                secondary_id = "Column"
                exp_sort = "Plot"
                exp_name = UUID.randomUUID().toString()
                count = (rows * cols).toString()
            }

            val fieldColumns = listOf("Row", "Column", "Plot", "observationUnitDbId")
            studyDbId = helper.createField(field, fieldColumns)

            when (startId) {
                R.id.dialog_field_creator_top_left_radio_button -> {
                    if (pattern == R.id.plot_linear_button) insertLinearPlotData(fieldColumns, rows, cols)
                    else insertZigZagPlotData(studyDbId, fieldColumns, rows, cols)
                }
                R.id.dialog_field_creator_bottom_left_radio_button -> {
                    if (pattern == R.id.plot_linear_button) insertLinearBottomPlotData(fieldColumns, rows, cols)
                    else insertZigZagBottomPlotData(studyDbId, fieldColumns, rows, cols)
                }
                R.id.dialog_field_creator_top_right_radio_button -> {
                    if (pattern == R.id.plot_linear_button) insertLinearRtlPlotData(fieldColumns, rows, cols)
                    else insertZigZagRtlPlotData(studyDbId, fieldColumns, rows, cols)
                }
                else -> {
                    if (pattern == R.id.plot) insertLinearBottomRtlPlotData(studyDbId, fieldColumns, rows, cols)
                    else insertZigZagBottomRtlPlotData(studyDbId, fieldColumns, rows, cols)
                }
            }

            this@FieldCreatorDialog.dismiss()

        }
    }

    private fun insertPlotData(fieldColumns: List<String>, i: Int, j: Int, k: Int,
                               uuid: String = UUID.randomUUID().toString()) {

        val row = i.toString()
        val col = j.toString()
        val index = k.toString()

        helper.createFieldData(studyDbId, fieldColumns, listOf(row, col, index, uuid))

        updatePlotInsertText(row, col, index)

    }

    private fun insertLinearPlotData(fieldColumns: List<String>, rows: Int, cols: Int) {

        var plotIndex = 0
        for (i in 0 until rows) {

            for (j in 0 until cols) {

                plotIndex += 1

                insertPlotData(fieldColumns, i, j, plotIndex)

            }
        }
    }

    /**
     * Top - down, Right to left plot creator.
     */
    private fun insertLinearRtlPlotData(fieldColumns: List<String>, rows: Int, cols: Int) {

        var plotIndex = 0
        for (i in 0 until rows) {

            for (j in cols-1 downTo 1) {

                plotIndex += 1

                insertPlotData(fieldColumns, i, j, plotIndex)

            }
        }
    }

    //bottom left linear pattern
    private fun insertLinearBottomPlotData(fieldColumns: List<String>, rows: Int, cols: Int) {

        var plotIndex = 0
        for (i in rows-1 downTo 0) {

            for (j in 0 until cols) {

                plotIndex += 1

                insertPlotData(fieldColumns, i, j, plotIndex)

            }
        }
    }

    private fun insertLinearBottomRtlPlotData(studyDbId: Int, fieldColumns: List<String>, rows: Int, cols: Int) {

        var plotIndex = 0
        for (i in rows-1 downTo 1) {

            for (j in cols-1 downTo 1) {

                plotIndex += 1

                insertPlotData(fieldColumns, i, j, plotIndex)

            }
        }
    }

    private fun insertZigZagBottomRtlPlotData(studyDbId: Int, fieldColumns: List<String>, rows: Int, cols: Int) {

        var plotIndex = 0
        for (i in 0 until rows) {

            for (j in 0 until cols) {

                plotIndex += 1

                val row = i.toString()
                val col = j.toString()
                val index = plotIndex.toString()
                val uuid = UUID.randomUUID().toString()
                helper.createFieldData(studyDbId, fieldColumns,
                    listOf(row, col, index, uuid))

                updatePlotInsertText(row, col, index)
            }
        }
    }

    private fun insertZigZagBottomPlotData(studyDbId: Int, fieldColumns: List<String>, rows: Int, cols: Int) {

        var plotIndex = 0
        for (i in 0 until rows) {

            for (j in 0 until cols) {

                plotIndex += 1

                val row = i.toString()
                val col = j.toString()
                val index = plotIndex.toString()
                val uuid = UUID.randomUUID().toString()
                helper.createFieldData(studyDbId, fieldColumns,
                    listOf(row, col, index, uuid))

                updatePlotInsertText(row, col, index)
            }
        }
    }

    private fun insertZigZagPlotData(studyDbId: Int, fieldColumns: List<String>, rows: Int, cols: Int) {

        var plotIndex = 0
        for (i in 0 until rows) {

            for (j in 0 until cols) {

                plotIndex += 1

                val row = i.toString()
                val col = j.toString()
                val index = plotIndex.toString()
                val uuid = UUID.randomUUID().toString()
                helper.createFieldData(studyDbId, fieldColumns,
                    listOf(row, col, index, uuid))

                updatePlotInsertText(row, col, index)
            }
        }
    }

    private fun insertZigZagRtlPlotData(studyDbId: Int, fieldColumns: List<String>, rows: Int, cols: Int) {

        var plotIndex = 0
        for (i in 0 until rows) {

            for (j in 0 until cols) {

                plotIndex += 1

                val row = i.toString()
                val col = j.toString()
                val index = plotIndex.toString()
                val uuid = UUID.randomUUID().toString()
                helper.createFieldData(studyDbId, fieldColumns,
                    listOf(row, col, index, uuid))

                updatePlotInsertText(row, col, index)
            }
        }
    }

    private fun updatePlotInsertText(row: String, col: String, index: String) = activity.runOnUiThread {
        findViewById<TextView>(R.id.field_creator_plot_insert_text_view).text =
            activity.getString(R.string.dialog_field_creator_insert_plot, index, row, col)
    }

}