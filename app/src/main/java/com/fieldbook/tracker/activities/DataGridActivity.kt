package com.fieldbook.tracker.activities

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.database.getStringOrNull
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.Utils
import dagger.hilt.android.AndroidEntryPoint
import eu.wewox.lazytable.LazyTable
import eu.wewox.lazytable.LazyTableItem
import eu.wewox.lazytable.lazyTableDimensions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.core.content.edit
import androidx.databinding.DataBindingUtil
import com.fieldbook.tracker.databinding.ActivityDataGridBinding
import com.fieldbook.tracker.utilities.InsetHandler
import com.google.firebase.crashlytics.FirebaseCrashlytics
import eu.wewox.lazytable.LazyTableState
import eu.wewox.lazytable.lazyTablePinConfiguration
import eu.wewox.lazytable.rememberSaveableLazyTableState

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

    data class HeaderData(val name: String, val code: String)
    data class CellData(val value: String?, val code: String, val color: Int = android.graphics.Color.GREEN)

    private val scope by lazy {
        CoroutineScope(Dispatchers.IO)
    }

    /**
     * Lists used to store grid information, also used for click events
     */
    private var mRowHeaders = ArrayList<HeaderData>()
    private var mPlotIds = ArrayList<String>()
    private var mTraits = ArrayList<TraitObject>()
    private var mGridData = ArrayList<List<CellData>>()

    // for active highlighted cell
    private var activePlotId: Int? = null
    private var activeTrait: Int? = null

    private var activeCellBgColor: Int = 0
    private var filledCellBgColor: Int = 0
    private var emptyCellBgColor: Int = 0
    private var activeCellTextColor: Int = 0
    private var cellTextColor: Int = 0

    private var isLoading by mutableStateOf(true)
    private lateinit var lazyTableState: LazyTableState

    @Inject
    lateinit var database: DataHelper

    @Inject
    lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        //this activity uses databinding to inflate the content layout
        //this creates a 'binding' variable that has all the views as fields, an alternative to findViewById
        val binding = DataBindingUtil.setContentView<ActivityDataGridBinding>(
            this,
            R.layout.activity_data_grid
        )

        setSupportActionBar(binding.toolbar)

        InsetHandler.setupStandardInsets(binding.root, binding.toolbar)

        if (supportActionBar != null) {
            supportActionBar?.title = null
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeButtonEnabled(true)
        }

        // get plot and trait IDs from intent
        activePlotId = intent.extras?.getInt("plot_id")
        activeTrait = intent.extras?.getInt("trait")

        setDataGridColors()

        initialize()

        binding.composeView.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color(activeCellBgColor))
                    } else {
                        DataGridTable()
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, standardBackCallback())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_data_grid, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
            }
            R.id.menu_data_grid_action_header_view -> {
                showHeaderPickerDialog()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setDataGridColors() {
        theme.apply {
            val typedValue = TypedValue()

            resolveAttribute(R.attr.activeCellColor, typedValue, true)
            activeCellBgColor = typedValue.data

            resolveAttribute(R.attr.dataFilledColor, typedValue, true)
            filledCellBgColor = typedValue.data

            resolveAttribute(R.attr.emptyCellColor, typedValue, true)
            emptyCellBgColor = typedValue.data

            resolveAttribute(R.attr.activeCellTextColor, typedValue, true)
            activeCellTextColor = typedValue.data

            resolveAttribute(R.attr.cellTextColor, typedValue, true)
            cellTextColor = typedValue.data
        }
    }

    /**
     * Runs the data grid loading.
     */
    private fun initialize() {
        //if something goes wrong finish the activity
        try {
            loadGridData()
        } catch (e: Exception) {
            e.printStackTrace()
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    /**
     * Uses the getExportTableData query to create a spreadsheet of values.
     * Columns returned are plot_id followed by all traits.
     */
    private fun loadGridData() {
        isLoading = true
        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val showLabel = preferences.getString(PreferenceKeys.LABELVAL_CUSTOMIZE, "value") == "value"
        val uniqueHeader = preferences.getString(GeneralKeys.UNIQUE_NAME, "") ?: ""
        val rowHeader = getCurrentRowHeader()
        val rowHeaderIndex = database.getAllObservationUnitAttributeNames(studyId)
            .indexOf(rowHeader).takeIf { it >= 0 } ?: 0

        if (rowHeader.isBlank()) {
            isLoading = false
            return
        }

        scope.launch {
            // clear the lists
            mTraits.clear()
            mRowHeaders.clear()
            mPlotIds.clear()

            val dataMap = ArrayList<List<CellData>>()

            // query database for visible traits
            val traitObjects = database.allTraitObjects
            traitObjects.forEach {
                if (it.visible) {
                    mTraits.add(it)
                }
            }

            val traits = database.allTraitObjects

            // expensive database call, only asks for the unique name plot attr and all visible traits
            val cursor = database.getExportTableData(studyId, mTraits)

            if (cursor.moveToFirst()) {
                Log.d("DataGridActivity", "Query executed. Row count: ${cursor.count}")

                try {
                    do { // iterate over cursor results and populate lists of plot ids and related trait values
                        val rowData = arrayListOf<String?>()
                        val columns = arrayListOf<String>()
                        val columnCount = cursor.columnCount

                        for (i in 0 until columnCount) {
                            try {
                                columns.add(cursor.getColumnName(i))
                                rowData.add(cursor.getStringOrNull(i))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // unique name column is always the first column
                        val uniqueIndex = columns.indexOf(uniqueHeader)

                        if (uniqueIndex > -1) { // if it doesn't exist skip this row
                            val id = rowData[uniqueIndex] ?: ""
                            val header = if (rowHeaderIndex > -1) rowData[rowHeaderIndex] ?: "" else ""

                            val dataList = arrayListOf<CellData>()

                            mRowHeaders.add(HeaderData(header, header)) // add unique name row header
                            mPlotIds.add(id)

                            mTraits.forEachIndexed { _, variable ->
                                val index = columns.indexOf(DataHelper.replaceIdentifiers(variable.name))

                                if (index > -1) {
                                    val value = rowData[index] ?: ""
                                    val t = traits.find { it.format in setOf("categorical", "multicat", "qualitative") }
                                    val repeatedValues = database.getRepeatedValues(studyId.toString(), id, variable.id)
                                    var cellValue = value

                                    if (t != null) {
                                        try {
                                            cellValue = CategoryJsonUtil.flattenMultiCategoryValue(
                                                CategoryJsonUtil.decode(value),
                                                showLabel
                                            )
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }

                                    // check repeated values and replace cellvalue with an ellipses
                                    if (repeatedValues.size > 1) {
                                        // data list is a trait row in the data grid
                                        dataList.add(CellData("...", id))
                                    } else {
                                        dataList.add(CellData(cellValue, id))
                                    }
                                }
                            }

                            dataMap.add(dataList)
                        }

                    } while (cursor.moveToNext())

                } catch (e: IllegalStateException) {
                    withContext(Dispatchers.Main) {
                        Utils.makeToast(this@DataGridActivity, getString(R.string.act_data_grid_cursor_failed))
                    }
                    e.printStackTrace()
                }

                cursor.close()

                withContext(Dispatchers.Main) {
                    mGridData.clear()
                    mGridData.addAll(dataMap)
                    isLoading = false
                }
            } else {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    @Composable
    fun DataGridTable() {
        if (mTraits.isEmpty() || mRowHeaders.isEmpty()) {
            return
        }

        lazyTableState = rememberSaveableLazyTableState()

        val columnCount = mTraits.size + 1 // +1 for rowHeader column
        val rowCount = mRowHeaders.size + 1 // +1 for column headers (traits)

        val targetColumn = activeTrait ?: 1
        val targetRow = activePlotId ?: 1

        LaunchedEffect(mTraits, mRowHeaders) {
            // this will trigger when traits or row headers are updated
            Log.d("DataGridActivity", "Data loaded: ${mTraits.size} traits, ${mRowHeaders.size} rows")
            if (mTraits.isNotEmpty() && mRowHeaders.isNotEmpty() && targetColumn <= mTraits.size && targetRow <= mRowHeaders.size) {
                lazyTableState.animateToCell(column = targetColumn, row = targetRow)
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            LazyTable(
                state = lazyTableState,
                dimensions = lazyTableDimensions(
                    columnSize = { col ->
                        when (col) {
                            0 -> 120.dp
                            else -> 100.dp
                        }
                    },
                    rowSize = { 48.dp } // row height
                ),
                contentPadding = PaddingValues(0.dp),
                pinConfiguration = lazyTablePinConfiguration(
                    columns = 1,    // pin the rowHeaders (first column)
                    rows = 1        // pin the columnHeaders (first row)
                ),
                // modifier = Modifier
                //     .fillMaxWidth()
            ) {
                // set up the header row
                items(
                    count = columnCount,
                    layoutInfo = { LazyTableItem(column = it, row = 0) }) { index ->
                    if (index == 0) {
                        HeaderCell(text = getCurrentRowHeader())
                    } else {
                        val traitIndex = index - 1
                        if (traitIndex < mTraits.size) {
                            HeaderCell(text = mTraits[traitIndex].name)
                        } else {
                            HeaderCell(text = "")
                        }
                    }
                }

                // set up the remaining grid cells
                items(
                    count = (rowCount - 1) * columnCount,
                    layoutInfo = {
                        val row = (it / columnCount) + 1  // +1 to skip header row
                        val column = it % columnCount
                        LazyTableItem(column = column, row = row)
                    }
                ) { index ->
                    val row = (index / columnCount)
                    val column = index % columnCount

                    if (column == 0) {
                        // rowHeaders (first column)
                        if (row < mRowHeaders.size) {
                            val headerText = mRowHeaders[row].name
                            RowHeaderCell(text = headerText)
                        } else {
                            RowHeaderCell(text = "")
                        }
                    } else {
                        // data cells
                        val columnIndex = column - 1 // -1 for header column
                        val cellData =
                            if (row < mGridData.size && columnIndex < mGridData[row].size) {
                                mGridData[row][columnIndex]
                            } else null

                        DataCell(
                            value = cellData?.value ?: "",
                            isHighlighted = (row + 1 == activePlotId && columnIndex + 1 == activeTrait)
                        ) {
                            if (cellData != null && row < mPlotIds.size) {
                                onCellClicked(row, columnIndex)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * This is used for the first row in the data grid
     */
    @Composable
    fun HeaderCell(text: String) {
        TableCell(
            text = text,
            backgroundColor = Color.White,
            textColor = Color(cellTextColor)
        )
    }

    @Composable
    fun RowHeaderCell(text: String) {
        TableCell(
            text = text,
            backgroundColor = Color.White,
            textColor = Color(cellTextColor)
        )
    }

    @Composable
    fun DataCell(value: String, isHighlighted: Boolean = false, onClick: () -> Unit = {}) {
        val backgroundColor = when {
            isHighlighted -> Color(activeCellBgColor)
            value.isNotBlank() -> Color(filledCellBgColor)
            else -> Color(emptyCellBgColor)
        }

        val textColor = if (isHighlighted) Color(activeCellTextColor) else Color(cellTextColor)

        TableCell(
            text = value,
            backgroundColor = backgroundColor,
            textColor = textColor,
            onClick = onClick,
            isClickable = true
        )
    }

    @Composable
    fun TableCell(
        text: String,
        backgroundColor: Color,
        textColor: Color,
        onClick: () -> Unit = {},
        isClickable: Boolean = false
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(backgroundColor)
                .border(Dp.Hairline, Color(cellTextColor))
                .then(if (isClickable) Modifier.clickable(onClick = onClick) else Modifier)
        ) {
            Text(
                text = text,
                color = textColor,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    }

    /**
     * Determines the current row header.
     */
    private fun getCurrentRowHeader(): String {
        val uniqueHeader = preferences.getString(GeneralKeys.UNIQUE_NAME, "") ?: ""
        val rowHeader = preferences.getString(GeneralKeys.DATAGRID_PREFIX_TRAIT, uniqueHeader) ?: ""
        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val unitAttributes = database.getAllObservationUnitAttributeNames(studyId)
        return if (rowHeader in unitAttributes) {
            Log.d("DataGridActivity", "Using saved row header from preferences: $rowHeader")
            rowHeader
        } else {
            Log.d("DataGridActivity", "Saved row header invalid. Falling back to unique header: $uniqueHeader")
            uniqueHeader
        }
    }

    private fun onCellClicked(row: Int, col: Int) {
        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
        val plotId = mPlotIds[row]
        val trait = mTraits[col]
        val repeatedValues = database.getRepeatedValues(studyId, plotId, trait.id)

        try {
            if (repeatedValues.size <= 1) {
                navigateFromValueClicked(plotId, col)
            } else {
                //show alert dialog with repeated values
                showRepeatedValuesNavigatorDialog(repeatedValues)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error occurred while trying to navigate: " + e.printStackTrace())
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun navigateFromValueClicked(plotId: String, traitIndex: Int, rep: Int = 1) {

        //this is the onClick handler which displays a quick message and sets the intent result / finishes
        Utils.makeToast(applicationContext, plotId)

        val returnIntent = Intent()
        returnIntent.putExtra("result", plotId)
        //the trait index is used to move collect activity to the clicked trait
        returnIntent.putExtra("trait", traitIndex)
        returnIntent.putExtra("rep", rep)

        setResult(RESULT_OK, returnIntent)
        finish()
    }

    private fun decodeValue(value: String): String {
        val labelValPref = preferences.getString(PreferenceKeys.LABELVAL_CUSTOMIZE, "value") ?: "value"
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

        // show a dialog to choose which value to navigate to
        AlertDialog.Builder(this, R.style.AppAlertDialog)
            .setTitle(R.string.dialog_data_grid_repeated_measures_title)
            .setSingleChoiceItems(choices, 0) { dialog, which ->
                val value = repeatedValues[which]
                val plotId = value.observation_unit_id
                val traitIndex = mTraits.indexOfFirst { it.id == value.observation_variable_db_id.toString() }

                navigateFromValueClicked(plotId, traitIndex, which + 1)
                dialog.dismiss()
            }.create().show()
    }



    /**
     * Shows dialog to choose a prefix trait to be displayed.
     */
    private fun showHeaderPickerDialog() {

        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        // get all available obs. property columns
        val columns = database.getAllObservationUnitAttributeNames(studyId)

        if (columns.isNotEmpty()) {
            val rowHeader = getCurrentRowHeader()
            val rowHeaderIndex = columns.indexOf(rowHeader).takeIf { it >= 0 } ?: 0

            AlertDialog.Builder(this, R.style.AppAlertDialog)
                .setTitle(R.string.dialog_data_grid_header_picker_title)
                .setSingleChoiceItems(columns, rowHeaderIndex) { dialog, which ->
                    // update the preference to the determined row header
                    preferences.edit { putString(GeneralKeys.DATAGRID_PREFIX_TRAIT, columns[which]) }
                    initialize()
                    dialog.dismiss()
                }.create().show()
        }
    }
}