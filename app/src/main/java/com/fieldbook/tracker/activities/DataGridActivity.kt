package com.fieldbook.tracker.activities

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.widget.Toolbar
import androidx.core.database.getStringOrNull
import androidx.lifecycle.lifecycleScope
import com.bin.david.form.core.SmartTable
import com.bin.david.form.core.TableConfig
import com.bin.david.form.data.CellInfo
import com.bin.david.form.data.column.Column
import com.bin.david.form.data.format.draw.IDrawFormat
import com.bin.david.form.data.table.ArrayTableData
import com.bin.david.form.data.table.TableData
import com.bin.david.form.utils.DensityUtils
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.Utils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * This activity is available as an optional toolbar action.
 * Toolbar can be activated by selecting Preferences/General/Datagrid
 *
 * Displays a spreadsheet of plots and trait values.
 * Traits are shown as static column headers, while plot ids are shown as static row headers.
 *
 * Users can click on cell data to navigate to that specific plot/trait in the collect activity.
 * When a cell is clicked, the activity finishes and returns an intent with data e.g:
 * Intent i = Intent()
 * i.putExtra("result", plotId)
 * i.putExtra("trait", 1) <- actually a trait index s.a 0 -> "height", 1 -> "lodging"
 **/
@AndroidEntryPoint
class DataGridActivity : ThemedActivity(), CoroutineScope by MainScope() {

    private lateinit var table: SmartTable<String>
    private lateinit var progressBar: ProgressBar

    private var mTraits: ArrayList<TraitObject> = ArrayList()
    private lateinit var mRowHeaders: ArrayList<String>
    private lateinit var mPlotIds: ArrayList<String>

    @Inject
    lateinit var database: DataHelper

    @Inject
    lateinit var preferences: SharedPreferences

    private val scope by lazy {
        CoroutineScope(Dispatchers.IO)
    }

    val activeCellBgColor = TypedValue()
    val filledCellBgColor = TypedValue()
    val emptyCellBgColor = TypedValue()

    val activeCellTextColor = TypedValue()
    val cellTextColor = TypedValue()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_data_grid)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            title = null
            setDisplayHomeAsUpEnabled(true)
            setHomeButtonEnabled(true)
        }

        table = findViewById(R.id.table)
        progressBar = findViewById(R.id.data_grid_progress_bar)

        setupTableStyle()

        initialize(
            plotId = intent.extras?.getInt("plot_id"),
            trait = intent.extras?.getInt("trait")
        )

        theme.apply {
            resolveAttribute(R.attr.activeCellColor, activeCellBgColor, true)
            resolveAttribute(R.attr.dataFilledColor, filledCellBgColor, true)
            resolveAttribute(R.attr.emptyCellColor, emptyCellBgColor, true)
            resolveAttribute(R.attr.activeCellTextColor, activeCellTextColor, true)
            resolveAttribute(R.attr.cellTextColor, cellTextColor, true)
        }
    }



    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_data_grid, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.menu_data_grid_action_header_view -> {
                val prefixTraits = database.rangeColumns
                if (prefixTraits.isNotEmpty()) {
                    val currentRowHeader = preferences.getString(GeneralKeys.DATAGRID_PREFIX_TRAIT, "")
                    val selectedIndex = prefixTraits.indexOf(currentRowHeader).takeIf { it >= 0 } ?: 0

                    AlertDialog.Builder(this, R.style.AppAlertDialog)
                        .setTitle(R.string.dialog_data_grid_header_picker_title)
                        .setSingleChoiceItems(prefixTraits, selectedIndex) { dialog, which ->
                            initialize(prefixTraits[which])
                            dialog.dismiss()
                        }.create().show()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupTableStyle() {

        table.config.apply {
            setHorizontalPadding(0)
            setVerticalPadding(0)

            isShowXSequence = false
            isShowYSequence = false
            isShowTableTitle = false

            isFixedTitle = true

            minTableWidth = resources.displayMetrics.widthPixels
        }

        table.setZoom(true, 2f, 0.5f)
    }

    private fun initialize(prefixTrait: String? = null, plotId: Int? = null, trait: Int? = null) {
        try {
            loadGridData(prefixTrait, plotId, trait)
        } catch (e: Exception) {
            e.printStackTrace()
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun loadGridData(prefixTrait: String?, plotId: Int?, trait: Int?) {
        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val showLabel = preferences.getString(GeneralKeys.LABELVAL_CUSTOMIZE, "value") == "value"
        val uniqueHeader = preferences.getString(GeneralKeys.UNIQUE_NAME, "") ?: ""

        // if row header was not chosen, then use the preference unique name
        var rowHeader = prefixTrait ?: preferences.getString(GeneralKeys.DATAGRID_PREFIX_TRAIT, uniqueHeader) ?: ""

        if (rowHeader !in database.rangeColumnNames) rowHeader = uniqueHeader

        // if rowHeader was updated, update the preference
        preferences.edit().putString(GeneralKeys.DATAGRID_PREFIX_TRAIT, rowHeader).apply()

        if (rowHeader.isBlank()) return

        // background processing
        scope.launch {
            // query database for visible traits
            mTraits.clear()

            database.allTraitObjects.forEach {
                if (it.visible) {
                    mTraits.add(it)
                }
            }

            // expensive database call, only asks for the unique name plot attr and all visible traits
            val cursor = database.getExportTableData(studyId, mTraits)

            if (cursor.moveToFirst()) {
                Log.d("DataGridActivity", "Query executed. Row count: ${cursor.count}")

                mRowHeaders = arrayListOf()
                mPlotIds = arrayListOf()
                val dataArray = arrayListOf<Array<String>>()

                try {
                    do { // iterate over cursor results and populate lists of plot ids and related trait values
                        val rowData = arrayListOf<String?>()
                        val columns = arrayListOf<String>()

                        for (i in 0 until cursor.columnCount) {
                            columns.add(cursor.getColumnName(i))
                            rowData.add(cursor.getStringOrNull(i))
                        }

                        val rowHeaderIndex = columns.indexOf(rowHeader)
                        // unique name column is always the first column
                        val uniqueIndex = columns.indexOf(uniqueHeader)

                        if (uniqueIndex > -1) { // if it doesn't exist skip this row
                            val id = rowData[uniqueIndex] ?: ""
                            val header = if (rowHeaderIndex > -1) rowData[rowHeaderIndex] ?: "" else ""

                            mRowHeaders.add(header) // add unique name row header
                            mPlotIds.add(id)

                            val rowValues = arrayListOf<String>()

                            // ensure we create a value for each trait
                            mTraits.forEach { trait ->
                                val index = columns.indexOf(DataHelper.replaceIdentifiers(trait.name))
                                var cellValue = ""

                                if (index > -1) {
                                    val value = rowData[index] ?: ""
                                    val repeatedValues = database.getRepeatedValues(studyId.toString(), id, trait.id)

                                    if (trait.format in setOf("categorical", "multicat", "qualitative")) {
                                        try {
                                            cellValue = CategoryJsonUtil.flattenMultiCategoryValue(
                                                CategoryJsonUtil.decode(value),
                                                showLabel
                                            )
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    } else {
                                        cellValue = value
                                    }

                                    // check repeated values and replace cellValue with an ellipses
                                    if (repeatedValues.size > 1) {
                                        cellValue = "..."
                                    }
                                }

                                rowValues.add(cellValue)
                            }

                            dataArray.add(rowValues.toTypedArray())
                        }

                    } while (cursor.moveToNext())


                    withContext(Dispatchers.Main) {
                        val columnTitles = mTraits.map { it.name }.toTypedArray()

                        setupTableData(columnTitles, dataArray, plotId, trait)

                        progressBar.visibility = View.GONE
                    }

                } catch (e: IllegalStateException) {
                    withContext(Dispatchers.Main) {
                        Utils.makeToast(
                            this@DataGridActivity,
                            getString(R.string.act_data_grid_cursor_failed)
                        )
                    }
                    e.printStackTrace()
                }

                cursor.close()
            }
        }
    }

    private fun setupTableData(columnTitles: Array<String>, data: ArrayList<Array<String>>, plotId: Int?, trait: Int?) {
        val headerColumnTitle = preferences.getString(GeneralKeys.DATAGRID_PREFIX_TRAIT, "") ?: ""
        val allColumnTitles = arrayOf(headerColumnTitle) + columnTitles

        // add row headers to the data
        val dataWithHeaders = data.mapIndexed { index, row ->
            arrayOf(mRowHeaders[index]) + row
        }.toTypedArray()

        val tableData = ArrayTableData.create(
            "DataGrid",
            allColumnTitles,
            ArrayTableData.transformColumnArray(dataWithHeaders),
            object : IDrawFormat<String> {
                val context = this@DataGridActivity

                override fun measureWidth(column: Column<String>, position: Int, config: TableConfig): Int {
                    return DensityUtils.dp2px(context, 120f)
                }

                override fun measureHeight(column: Column<String>, position: Int, config: TableConfig): Int {
                    return DensityUtils.dp2px(context, 50f)
                }

                override fun draw(canvas: Canvas, rect: Rect, cellInfo: CellInfo<String>, config: TableConfig) {
                    val paint = config.paint
                    paint.style = Paint.Style.FILL

                    val text = cellInfo.value ?: cellInfo.data ?: ""

                    // determine if this is the active cell
                    // row is col both use 0-based indexing
                    // col effectively can be considered to use 1-based due to the added first column for row headers
                    val isActiveCell = (plotId != null && trait != null && cellInfo.row + 1 == plotId && (cellInfo.col == trait))

                    // set background colors for cells
                    paint.color = when {
                        isActiveCell -> activeCellBgColor.data
                        cellInfo.col != 0 && text.isNotBlank() -> filledCellBgColor.data
                        else -> emptyCellBgColor.data
                    }

                    // draw cell and fill color
                    canvas.drawRect(
                        (rect.left + 5).toFloat(),
                        (rect.top + 5).toFloat(),
                        (rect.right - 5).toFloat(),
                        (rect.bottom - 5).toFloat(),
                        paint
                    )

                    // set text colors
                    paint.color = if (isActiveCell) {
                        activeCellTextColor.data
                    } else {
                        cellTextColor.data
                    }

                    paint.textSize = DensityUtils.sp2px(
                        context,
                        14f
                    ).toFloat()
                    paint.textAlign = Paint.Align.CENTER

                    val xPos = (rect.centerX()).toFloat()
                    val yPos = rect.exactCenterY() - ((paint.descent() + paint.ascent()) / 2)

                    // draw the text
                    canvas.drawText(
                        getCellTextDisplay(text, rect, paint),
                        xPos,
                        yPos,
                        paint
                    )
                }
            }
        )

        tableData.onItemClickListener =
            TableData.OnItemClickListener<String> { _, _, _, col, row ->
                if (col > 0) {
                    onCellClicked(col - 1, row) // col - 1 as we have added rowHeaders
                }
            }

        tableData.columns[0].isFixed = true
        table.tableData = tableData
    }

    /**
     * Get the text to be displayed in the table cell
     * Use ellipsis if the string is too long to fit in the available width
     * We can use binary search to efficiently find the max characters that can fit
     */
    private fun getCellTextDisplay(text: String, rect: Rect, paint: Paint): String {
        val padding = DensityUtils.dp2px(this@DataGridActivity, 4f)
        val availableWidth = rect.width() - (padding * 2)

        val ellipsis = "..."
        var displayText = text
        val textWidth = paint.measureText(displayText)

        if (textWidth > availableWidth) {
            // binary search to find max chars
            var left = 0
            var right = text.length
            while (left < right) {
                val mid = (left + right + 1) / 2
                val s = text.substring(0, mid) + ellipsis
                if (paint.measureText(s) <= availableWidth) {
                    left = mid
                } else {
                    right = mid - 1
                }
            }
            displayText = text.substring(0, left) + ellipsis
        }

        return displayText
    }

    private fun onCellClicked(column: Int, row: Int) {
        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
        val plotId = mPlotIds[row]
        val trait = mTraits[column]

        val repeatedValues = database.getRepeatedValues(studyId, plotId, trait.id)

        if (repeatedValues.size <= 1) {
            navigateFromValueClicked(plotId, column)
        } else {
            showRepeatedValuesNavigatorDialog(repeatedValues)
        }
    }

    private fun navigateFromValueClicked(plotId: String, traitIndex: Int, rep: Int? = 1) {
        Utils.makeToast(applicationContext, plotId)

        val returnIntent = Intent().apply {
            putExtra("result", plotId)
            putExtra("trait", traitIndex)
            putExtra("rep", rep)
        }

        setResult(RESULT_OK, returnIntent)
        finish()
    }

    private fun decodeValue(value: String): String {
        val labelValPref = preferences.getString(GeneralKeys.LABELVAL_CUSTOMIZE, "value") ?: "value"
        val scale = CategoryJsonUtil.decode(value)
        return if (scale.isNotEmpty()) {
            if (labelValPref == "value") scale[0].value else scale[0].label
        } else ""
    }

    private fun showRepeatedValuesNavigatorDialog(repeatedValues: Array<ObservationModel>) {
        for (m in repeatedValues) {
            if (m.observation_variable_field_book_format in setOf("categorical", "multicat", "qualitative")) {
                if (m.value.isNotEmpty()) {
                    m.value = decodeValue(m.value)
                }
            }
        }

        val choices = repeatedValues.map { it.value }.filter { it.isNotBlank() }.toTypedArray()

        AlertDialog.Builder(this, R.style.AppAlertDialog)
            .setTitle(R.string.dialog_data_grid_repeated_measures_title)
            .setSingleChoiceItems(choices, 0) { dialog, which ->
                val value = repeatedValues[which]
                val plotId = value.observation_unit_id
                val traitIndex = mTraits.indexOfFirst {
                    it.id == value.observation_variable_db_id.toString()
                }

                navigateFromValueClicked(plotId, traitIndex, which + 1)
                dialog.dismiss()
            }.create().show()
    }
}