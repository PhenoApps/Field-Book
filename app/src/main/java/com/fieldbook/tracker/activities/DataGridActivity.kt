package com.fieldbook.tracker.activities

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.databinding.DataBindingUtil
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.viewmodels.DataGridViewModel
import com.fieldbook.tracker.databinding.ActivityDataGridBinding
import com.fieldbook.tracker.utilities.InsetHandler
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import eu.wewox.lazytable.LazyTable
import eu.wewox.lazytable.LazyTableItem
import eu.wewox.lazytable.lazyTableDimensions
import eu.wewox.lazytable.lazyTablePinConfiguration
import eu.wewox.lazytable.rememberSaveableLazyTableState
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
class DataGridActivity : ThemedActivity() {

    private val viewModel: DataGridViewModel by viewModels()

    // for active highlighted cell
    private var activePlotId: Int? = null
    private var activeTrait: Int? = null
    private var activePlotIdString: String? = null

    private var activeCellBgColor: Int = 0
    private var filledCellBgColor: Int = 0
    private var emptyCellBgColor: Int = 0
    private var activeCellTextColor: Int = 0
    private var cellTextColor: Int = 0

    @Inject
    lateinit var database: DataHelper

    @Inject
    lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

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

        activePlotId = intent.extras?.getInt("plot_id")
        activeTrait = intent.extras?.getInt("trait")

        setDataGridColors()

        // Trigger grid load — ViewModel survives rotation so this is a no-op on re-creation
        // if the grid is already loaded
        if (viewModel.uiState.value is DataGridViewModel.UiState.Loading) {
            viewModel.loadGrid(getCurrentRowHeader())
        }

        binding.composeView.setContent {
            val uiState by viewModel.uiState.collectAsState()
            val columnLocked by viewModel.columnLocked.collectAsState()
            val sortState by viewModel.sortState.collectAsState()

            LaunchedEffect(columnLocked) {
                invalidateOptionsMenu()
            }

            LaunchedEffect(sortState) {
                invalidateOptionsMenu()
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (val state = uiState) {
                        is DataGridViewModel.UiState.Loading -> {
                            CircularProgressIndicator(color = Color(activeCellBgColor))
                        }
                        is DataGridViewModel.UiState.Loaded -> {
                            if (activePlotIdString == null && activePlotId != null) {
                                activePlotIdString = viewModel.rawPlotIds.getOrNull(activePlotId!! - 1)
                            }
                            DataGridTable(state, columnLocked, sortState)
                        }
                        is DataGridViewModel.UiState.Empty,
                        is DataGridViewModel.UiState.Error -> {
                            // Nothing to show; finish was called in the old version on error
                        }
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
            R.id.menu_data_grid_action_lock_column -> {
                viewModel.toggleColumnLock()
            }
            R.id.menu_data_grid_action_header_view -> {
                showHeaderPickerDialog()
            }
            R.id.menu_data_grid_action_reset_sort -> {
                viewModel.resetSort()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val lockItem = menu.findItem(R.id.menu_data_grid_action_lock_column)
        val isLocked = viewModel.columnLocked.value
        lockItem?.setIcon(if (isLocked) R.drawable.ic_tb_lock else R.drawable.ic_tb_unlock)
        val resetSortItem = menu.findItem(R.id.menu_data_grid_action_reset_sort)
        resetSortItem?.isVisible = viewModel.sortState.value.columnIndex >= 0
        return super.onPrepareOptionsMenu(menu)
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

    @Composable
    fun DataGridTable(
        state: DataGridViewModel.UiState.Loaded,
        columnLocked: Boolean = true,
        sortState: DataGridViewModel.SortState = DataGridViewModel.SortState()
    ) {
        val traits = state.traits
        val rowHeaders = state.rowHeaders
        val plotIds = state.plotIds
        val gridData = state.gridData

        if (traits.isEmpty() || rowHeaders.isEmpty()) {
            return
        }

        val lazyTableState = rememberSaveableLazyTableState()

        val columnCount = traits.size + 1 // +1 for rowHeader column
        val rowCount = rowHeaders.size + 1 // +1 for column headers (traits)

        val targetColumn = activeTrait ?: 1
        val targetRow = activePlotId ?: 1

        LaunchedEffect(traits) {
            // Triggers once per load (traits identity is stable across progressive batches)
            Log.d("DataGridActivity", "Data loaded: ${traits.size} traits, ${rowHeaders.size} rows")
            if (traits.isNotEmpty() && rowHeaders.isNotEmpty()
                && targetColumn <= traits.size && targetRow <= rowHeaders.size) {
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
                    rowSize = { 48.dp }
                ),
                contentPadding = PaddingValues(0.dp),
                pinConfiguration = lazyTablePinConfiguration(
                    columns = if (columnLocked) 1 else 0,
                    rows = 1
                )
            ) {
                // Header row
                items(
                    count = columnCount,
                    layoutInfo = { LazyTableItem(column = it, row = 0) }) { index ->
                    val isSorted = sortState.columnIndex == index
                    val sortIcon = when {
                        !isSorted -> null
                        sortState.ascending -> R.drawable.ic_chevron_up
                        else -> R.drawable.ic_chevron_down
                    }
                    if (index == 0) {
                        HeaderCell(
                            text = getCurrentRowHeader(),
                            sortIconRes = sortIcon,
                            onClick = { viewModel.sortByColumn(0) }
                        )
                    } else {
                        val traitIndex = index - 1
                        HeaderCell(
                            text = if (traitIndex < traits.size) traits[traitIndex].alias else "",
                            sortIconRes = sortIcon,
                            onClick = { viewModel.sortByColumn(index) }
                        )
                    }
                }

                // Data cells
                items(
                    count = (rowCount - 1) * columnCount,
                    layoutInfo = {
                        val row = (it / columnCount) + 1
                        val column = it % columnCount
                        LazyTableItem(column = column, row = row)
                    }
                ) { index ->
                    val row = (index / columnCount)
                    val column = index % columnCount

                    if (column == 0) {
                        RowHeaderCell(text = if (row < rowHeaders.size) rowHeaders[row].name else "")
                    } else {
                        val columnIndex = column - 1
                        val cellData =
                            if (row < gridData.size && columnIndex < gridData[row].size)
                                gridData[row][columnIndex]
                            else null

                        DataCell(
                            value = cellData?.value ?: "",
                            isHighlighted = (plotIds.getOrNull(row) == activePlotIdString && columnIndex + 1 == activeTrait)
                        ) {
                            if (cellData != null && row < plotIds.size) {
                                onCellClicked(row, columnIndex, traits, plotIds)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun HeaderCell(text: String, sortIconRes: Int? = null, onClick: (() -> Unit)? = null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(Color.White)
                .border(Dp.Hairline, Color(cellTextColor))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = text,
                    color = Color(cellTextColor),
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
                if (sortIconRes != null) {
                    Icon(
                        painter = painterResource(id = sortIconRes),
                        contentDescription = null,
                        tint = Color(cellTextColor),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
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
     * Determines the current row header from preferences.
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

    private fun onCellClicked(
        row: Int,
        col: Int,
        traits: List<TraitObject>,
        plotIds: List<String>
    ) {
        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
        val plotId = plotIds[row]
        val trait = traits[col]
        val repeatedValues = database.getRepeatedValues(studyId, plotId, trait.id)

        try {
            if (repeatedValues.size <= 1) {
                navigateFromValueClicked(plotId, col)
            } else {
                showRepeatedValuesNavigatorDialog(trait, repeatedValues, traits)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error occurred while trying to navigate: " + e.printStackTrace())
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun navigateFromValueClicked(plotId: String, traitIndex: Int, rep: Int = 1) {
        Utils.makeToast(applicationContext, plotId)

        val returnIntent = Intent()
        returnIntent.putExtra("result", plotId)
        returnIntent.putExtra("trait", traitIndex)
        returnIntent.putExtra("rep", rep)

        setResult(RESULT_OK, returnIntent)
        finish()
    }

    private fun decodeValue(showValue: Boolean, value: String): String {
        val scale = CategoryJsonUtil.decode(value)
        return if (scale.isNotEmpty()) {
            if (showValue) scale[0].value else scale[0].label
        } else ""
    }

    private fun showRepeatedValuesNavigatorDialog(
        trait: TraitObject,
        repeatedValues: Array<ObservationModel>,
        traits: List<TraitObject>
    ) {
        for (m in repeatedValues) {
            if (m.observation_variable_field_book_format in setOf("categorical", "qualitative")) {
                if (m.value.isNotEmpty()) {
                    m.value = decodeValue(trait.categoryDisplayValue, m.value)
                }
            }
        }

        val choices = repeatedValues.map { it.value }.filter { it.isNotBlank() }.toTypedArray()

        AlertDialog.Builder(this, R.style.AppAlertDialog)
            .setTitle(R.string.dialog_data_grid_repeated_measures_title)
            .setSingleChoiceItems(choices, 0) { dialog, which ->
                val value = repeatedValues[which]
                val plotId = value.observation_unit_id
                val traitIndex = traits.indexOfFirst { it.id == value.observation_variable_db_id.toString() }

                navigateFromValueClicked(plotId, traitIndex, which + 1)
                dialog.dismiss()
            }.create().show()
    }

    /**
     * Shows dialog to choose a prefix trait to be displayed.
     */
    private fun showHeaderPickerDialog() {
        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val columns = database.getAllObservationUnitAttributeNames(studyId)

        if (columns.isNotEmpty()) {
            val rowHeader = getCurrentRowHeader()
            val rowHeaderIndex = columns.indexOf(rowHeader).takeIf { it >= 0 } ?: 0

            AlertDialog.Builder(this, R.style.AppAlertDialog)
                .setTitle(R.string.dialog_data_grid_header_picker_title)
                .setSingleChoiceItems(columns, rowHeaderIndex) { dialog, which ->
                    preferences.edit { putString(GeneralKeys.DATAGRID_PREFIX_TRAIT, columns[which]) }
                    viewModel.loadGrid(columns[which])
                    dialog.dismiss()
                }.create().show()
        }
    }
}
