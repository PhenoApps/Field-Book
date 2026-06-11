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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.database.getStringOrNull
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.database.models.ObservationUnitModel
import com.fieldbook.tracker.databinding.ActivityDataGridBinding
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.ui.grid.rememberGridColors
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.InsetHandler
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.viewmodels.DataGridViewModel
import dagger.hilt.android.AndroidEntryPoint
import eu.wewox.lazytable.LazyTable
import eu.wewox.lazytable.LazyTableItem
import eu.wewox.lazytable.lazyTableDimensions
import eu.wewox.lazytable.lazyTablePinConfiguration
import eu.wewox.lazytable.rememberSaveableLazyTableState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.ceil

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

    data class HeaderData(val name: String, val code: String)
    data class CellData(
        val value: String?,
        val code: String,
        val color: Int = android.graphics.Color.GREEN
    )

    companion object {
        private const val TAG = "DataGridActivity"
        private const val ROW_HEADER_DELIMITER = " "
    }

    enum class DataGridViewMode { GRID, TRIAL }

    data class TrialPlotData(
        val plotId: String,
        val rowIndex: Int,
        val colIndex: Int,
        val label: String,
        val observedTraits: Int,
        val totalTraits: Int
    ) {
        val status: TrialPlotStatus
            get() = when {
                totalTraits == 0 -> TrialPlotStatus.EMPTY
                observedTraits >= totalTraits -> TrialPlotStatus.COMPLETE
                observedTraits > 0 -> TrialPlotStatus.PARTIAL
                else -> TrialPlotStatus.EMPTY
            }
    }

    enum class TrialPlotStatus { COMPLETE, PARTIAL, EMPTY }

    enum class TrialFilter { NONE, COMPLETE, PARTIAL, EMPTY, MISSING }

    data class SelectedCell(
        val row: Int,
        val col: Int,
        val plotId: String,
        val traitIndex: Int,
        val repeated: List<ObservationModel> = emptyList()
    )

    // for active highlighted cell (navigated from)
    private var activePlotId: Int? = null
    private var activeTrait: Int? = null
    private var activePlotIdString: String? by mutableStateOf(null)

    // for selected cell highlight (last clicked in grid)
    private var selectedPlotId by mutableStateOf<Int?>(null)
    private var selectedTrait by mutableStateOf<Int?>(null)

    // for last clicked trial plot
    private var selectedTrialPlotId by mutableStateOf<String?>(null)

    private var activeCellBgColor: Int = 0
    private var filledCellBgColor: Int = 0
    private var emptyCellBgColor: Int = 0
    private var activeCellTextColor: Int = 0
    private var cellTextColor: Int = 0

    private var isLoading by mutableStateOf(true)

    // Currently selected cell driving the bottom details panel + Collect button
    private var selectedCell by mutableStateOf<SelectedCell?>(null)

    // Trial view state
    private var viewMode by mutableStateOf(DataGridViewMode.GRID)
    private var showCellDetails by mutableStateOf(true)
    private var activeTrialFilter by mutableStateOf(TrialFilter.NONE)
    private var trialPlotDataList = emptyList<TrialPlotData>()
    private var trialGridRows = 0
    private var trialGridCols = 0
    private var trialGrid: Array<Array<TrialPlotData?>> = emptyArray()

    // RowHeaders for trial view display (from the grid state)
    private var mRowHeaders = ArrayList<HeaderData>()
    private var mPlotIds = ArrayList<String>()
    private var mTraits = ArrayList<TraitObject>()

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

        // Restore view mode preference
        viewMode = if (preferences.getBoolean(GeneralKeys.TRIAL_VIEW_SETTING, false)) {
            DataGridViewMode.TRIAL
        } else {
            DataGridViewMode.GRID
        }

        // Restore cell details preference
        showCellDetails = preferences.getBoolean(GeneralKeys.CELL_DETAILS_SETTING, true)

        // Trigger grid load — ViewModel survives rotation so this is a no-op on re-creation
        // if the grid is already loaded
        if (viewModel.uiState.value is DataGridViewModel.UiState.Loading) {
            lifecycleScope.launch {
                val headers = withContext(Dispatchers.IO) { getDisplayHeaders() }
                viewModel.loadGrid(getCurrentRowHeader(), headers)
            }
        }

        // Load trial data if needed
        if (trialPlotDataList.isEmpty()) {
            loadTrialData()
        }

        binding.composeView.setContent {
            val uiState by viewModel.uiState.collectAsState()
            val columnLocked by viewModel.columnLocked.collectAsState()
            val sortState by viewModel.sortState.collectAsState()
            val wrapContent by viewModel.wrapContent.collectAsState()
            val heatmapEnabled by viewModel.heatmapEnabled.collectAsState()
            val zoomLevel by viewModel.zoomLevel.collectAsState()

            LaunchedEffect(columnLocked) {
                invalidateOptionsMenu()
            }

            LaunchedEffect(sortState) {
                invalidateOptionsMenu()
            }

            LaunchedEffect(wrapContent) {
                invalidateOptionsMenu()
            }

            LaunchedEffect(heatmapEnabled) {
                invalidateOptionsMenu()
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(pass = PointerEventPass.Initial)
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        if (event.changes.size >= 2) {
                                            val zoomChange = event.calculateZoom()
                                            if (zoomChange != 1f) {
                                                viewModel.setZoom(viewModel.zoomLevel.value * zoomChange)
                                            }
                                            // Prioritize zoom: consume 2-finger events so children (LazyTable) don't scroll
                                            event.changes.forEach { it.consume() }
                                        }
                                        if (event.changes.all { !it.pressed }) break
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading && viewMode == DataGridViewMode.TRIAL) {
                            CircularProgressIndicator(color = Color(activeCellBgColor))
                        } else if (viewMode == DataGridViewMode.TRIAL) {
                            TrialView(columnLocked, wrapContent, zoomLevel)
                        } else {
                            when (val state = uiState) {
                                is DataGridViewModel.UiState.Loading -> {
                                    CircularProgressIndicator(color = Color(activeCellBgColor))
                                }

                                is DataGridViewModel.UiState.Loaded -> {
                                    if (activePlotIdString == null && activePlotId != null) {
                                        // activePlotId is 1-based (sent by CollectActivity); subtract 1 to index rawPlotIds
                                        activePlotIdString =
                                            viewModel.rawPlotIds.getOrNull(activePlotId!! - 1)
                                    }
                                    DataGridTable(
                                        state,
                                        columnLocked,
                                        sortState,
                                        wrapContent,
                                        heatmapEnabled,
                                        zoomLevel
                                    )
                                }

                                is DataGridViewModel.UiState.Empty -> {
                                    Text(
                                        text = getString(R.string.data_grid_empty),
                                        color = Color(cellTextColor),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                is DataGridViewModel.UiState.Error -> {
                                    finish()
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        thickness = Dp.Hairline,
                        color = Color(cellTextColor)
                    )

                    if (showCellDetails) {
                        ObservationDetails(
                            cell = selectedCell,
                            viewMode = viewMode,
                            onRepSelected = { repIndex ->
                                selectedCell?.let { cell ->
                                    //navigateFromValueClicked(cell.plotId, cell.traitIndex, repIndex + 1)
                                }
                            },
                            onCollectClicked = {
                                selectedCell?.let { cell ->
                                    navigateFromValueClicked(cell.plotId, cell.traitIndex, 1)
                                }
                            }
                        )
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, standardBackCallback())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_data_grid, menu)
        val isGrid = viewMode == DataGridViewMode.GRID

        val trialItem = menu.findItem(R.id.menu_data_grid_action_trial_view)
        trialItem?.let {
            it.title = if (isGrid) {
                getString(R.string.menu_data_grid_action_trial_view)
            } else {
                getString(R.string.menu_data_grid_action_grid_view)
            }
        }

        menu.findItem(R.id.menu_data_grid_action_header_view)?.let {
            it.isVisible = isGrid
            it.title = getString(R.string.menu_action_header_view_data_grid_title)
        }
        menu.findItem(R.id.menu_data_grid_action_column_view)?.isVisible = false

        menu.findItem(R.id.menu_data_grid_action_heatmap)?.isVisible = isGrid

        // Sync cell details toggle title
        menu.findItem(R.id.menu_data_grid_action_cell_details)?.title =
            if (showCellDetails) getString(R.string.menu_data_grid_action_cell_details_on)
            else getString(R.string.menu_data_grid_action_cell_details_off)
        menu.findItem(R.id.menu_data_grid_action_reset_sort)?.isVisible =
            isGrid && viewModel.sortState.value.columnIndex >= 0
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val isGrid = viewMode == DataGridViewMode.GRID
        val lockItem = menu.findItem(R.id.menu_data_grid_action_lock_column)
        val isLocked = viewModel.columnLocked.value
        lockItem?.setIcon(if (isLocked) R.drawable.ic_tb_lock else R.drawable.ic_tb_unlock)

        val resetSortItem = menu.findItem(R.id.menu_data_grid_action_reset_sort)
        resetSortItem?.isVisible = isGrid && viewModel.sortState.value.columnIndex >= 0

        val wrapItem = menu.findItem(R.id.menu_data_grid_action_wrap_content)
        val isWrapped = viewModel.wrapContent.value
        wrapItem?.setIcon(if (isWrapped) R.drawable.arrow_collapse_horizontal else R.drawable.arrow_expand_horizontal)

        val heatmapItem = menu.findItem(R.id.menu_data_grid_action_heatmap)
        heatmapItem?.isVisible = isGrid
        if (isGrid) {
            val isHeatmap = viewModel.heatmapEnabled.value
            if (isHeatmap) {
                heatmapItem?.icon?.setTint(getColor(R.color.main_primary_dark))
            } else {
                heatmapItem?.icon?.setTintList(null)
            }
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
            }

            R.id.menu_data_grid_action_lock_column -> {
                viewModel.toggleColumnLock()
            }

            R.id.menu_data_grid_action_wrap_content -> {
                viewModel.toggleWrapContent()
            }

            R.id.menu_data_grid_action_header_view -> {
                if (viewMode == DataGridViewMode.GRID) {
                    showHeaderPickerDialog()
                } else {
                    showTrialRowAttributePickerDialog()
                }
            }

            R.id.menu_data_grid_action_column_view -> {
                showTrialColumnAttributePickerDialog()
            }

            R.id.menu_data_grid_action_reset_sort -> {
                viewModel.resetSort()
            }

            R.id.menu_data_grid_action_heatmap -> {
                viewModel.toggleHeatmap()
            }

            R.id.menu_data_grid_action_trial_view -> {
                toggleViewMode()
            }

            R.id.menu_data_grid_action_cell_details -> {
                showCellDetails = !showCellDetails
                preferences.edit { putBoolean(GeneralKeys.CELL_DETAILS_SETTING, showCellDetails) }
                if (!showCellDetails) {
                    selectedCell = null
                    selectedPlotId = null
                    selectedTrait = null
                    selectedTrialPlotId = null
                }
                invalidateOptionsMenu()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun toggleViewMode() {
        viewMode = if (viewMode == DataGridViewMode.GRID) {
            DataGridViewMode.TRIAL
        } else {
            DataGridViewMode.GRID
        }
        preferences.edit {
            putBoolean(GeneralKeys.TRIAL_VIEW_SETTING, viewMode == DataGridViewMode.TRIAL)
        }
        // Reload data for the new mode if needed
        if (viewMode == DataGridViewMode.TRIAL && trialPlotDataList.isEmpty()) {
            loadTrialData()
        }
        invalidateOptionsMenu()
    }

    /**
     * Shows a dialog to choose the row attribute for the trial view layout.
     */
    private fun showTrialRowAttributePickerDialog() {
        showTrialSingleAttributePickerDialog(true)
    }

    /**
     * Shows a dialog to choose the column attribute for the trial view layout.
     */
    private fun showTrialColumnAttributePickerDialog() {
        showTrialSingleAttributePickerDialog(false)
    }

    private fun showTrialSingleAttributePickerDialog(isRow: Boolean) {
        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)

        val propColumns = database.getAllObservationUnitAttributeNames(studyId).toList()
        val coreColumns = database.existingObservationUnitCoreColumns
        val allColumns = (propColumns + coreColumns.filter { it !in propColumns }).toTypedArray()

        if (allColumns.isEmpty()) return

        val currentAttr = if (isRow) preferences.getString(GeneralKeys.TRIAL_ROW_ATTR, "") ?: ""
        else preferences.getString(GeneralKeys.TRIAL_COL_ATTR, "") ?: ""

        val items = allColumns
        var selected = items.indexOfFirst { it == currentAttr }.coerceAtLeast(0)

        AlertDialog.Builder(this, R.style.AppAlertDialog)
            .setTitle(if (isRow) R.string.dialog_data_grid_header_picker_title else R.string.dialog_data_grid_column_picker_title)
            .setSingleChoiceItems(items, selected) { _, which ->
                selected = which
            }
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val attr = items[selected]
                preferences.edit {
                    putString(
                        if (isRow) GeneralKeys.TRIAL_ROW_ATTR else GeneralKeys.TRIAL_COL_ATTR,
                        attr
                    )
                }
                loadTrialData()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show()
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

    // ─── Trial Data Loading ─────────────────────────────────────────────

    private fun loadTrialData() {
        isLoading = true
        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val units = database.getAllObservationUnits(studyId) ?: run {
            isLoading = false
            return
        }

        if (units.isEmpty()) {
            isLoading = false
            return
        }

        val traits = database.allTraitObjects.filter { it.visible }
        mTraits.clear()
        mTraits.addAll(traits)
        val totalTraits = traits.size

        val uniqueHeader = preferences.getString(GeneralKeys.UNIQUE_NAME, "") ?: ""

        val savedRowAttr = preferences.getString(GeneralKeys.TRIAL_ROW_ATTR, "") ?: ""
        val savedColAttr = preferences.getString(GeneralKeys.TRIAL_COL_ATTR, "") ?: ""

        val unitAttributeNames = database.getAllObservationUnitAttributeNames(studyId)
        val coreFieldNames = database.existingObservationUnitCoreColumns
        val allValidNames = unitAttributeNames + coreFieldNames.filter { it !in unitAttributeNames }

        val hasPosCoordY = units.any { it.position_coordinate_y?.toIntOrNull() != null }
        val hasPosCoordX = units.any { it.position_coordinate_x?.toIntOrNull() != null }

        val defaultRowAttr = if (hasPosCoordY) "position_coordinate_y"
        else allValidNames.firstOrNull {
            it.contains("row", ignoreCase = true) || it.contains("range", ignoreCase = true)
        } ?: allValidNames.firstOrNull() ?: "position_coordinate_y"

        val defaultColAttr = if (hasPosCoordX) "position_coordinate_x"
        else allValidNames.firstOrNull {
            it.contains("col", ignoreCase = true) || it.contains("column", ignoreCase = true)
        } ?: allValidNames.getOrNull(1) ?: "position_coordinate_x"

        val rowAttrName = savedRowAttr.ifBlank { defaultRowAttr }
        val colAttrName = savedColAttr.ifBlank { defaultColAttr }

        val effectiveRowAttr = if (rowAttrName in allValidNames) rowAttrName
        else if (defaultRowAttr in allValidNames) defaultRowAttr
        else null
        val effectiveColAttr = if (colAttrName in allValidNames) colAttrName
        else if (defaultColAttr in allValidNames) defaultColAttr
        else null

        lifecycleScope.launch(Dispatchers.IO) {
            val cursor = database.getExportTableData(studyId, ArrayList(traits))
            val plotObservationCounts = mutableMapOf<String, Int>()

            if (cursor.moveToFirst()) {
                try {
                    do {
                        val columns = mutableListOf<String>()
                        val rowData = mutableListOf<String?>()
                        for (i in 0 until cursor.columnCount) {
                            try {
                                columns.add(cursor.getColumnName(i))
                                rowData.add(cursor.getStringOrNull(i))
                            } catch (_: Exception) {
                            }
                        }

                        val uniqueIndex = columns.indexOf(uniqueHeader)
                        if (uniqueIndex > -1) {
                            val id = rowData[uniqueIndex] ?: ""
                            var observedCount = 0
                            traits.forEach { trait ->
                                val colIdx = columns.indexOf(
                                    DataHelper.replaceIdentifiers(
                                        trait.name
                                    )
                                )
                                if (colIdx > -1) {
                                    val value = rowData[colIdx]
                                    if (!value.isNullOrBlank()) {
                                        observedCount++
                                    }
                                }
                            }
                            plotObservationCounts[id] = observedCount
                        }
                    } while (cursor.moveToNext())
                } catch (_: Exception) {
                }
                cursor.close()
            }

            if (effectiveRowAttr == null || effectiveColAttr == null) {
                withContext(Dispatchers.Main) {
                    trialPlotDataList = emptyList()
                    trialGridRows = 0
                    trialGridCols = 0
                    trialGrid = emptyArray()
                    isLoading = false
                    showTrialRowAttributePickerDialog()
                }
                return@launch
            }

            val plots = mutableListOf<TrialPlotData>()
            var maxRow = 0
            var maxCol = 0

            units.forEach { unit ->
                val rowStr = getAttributeValue(unit, effectiveRowAttr)
                val colStr = getAttributeValue(unit, effectiveColAttr)
                val row = rowStr?.toIntOrNull() ?: return@forEach
                val col = colStr?.toIntOrNull() ?: return@forEach
                val rowZero = (row - 1).coerceAtLeast(0)
                val colZero = (col - 1).coerceAtLeast(0)
                maxRow = maxOf(maxRow, rowZero + 1)
                maxCol = maxOf(maxCol, colZero + 1)
                val plotId = unit.observation_unit_db_id
                val observed = plotObservationCounts[plotId] ?: 0
                plots.add(
                    TrialPlotData(
                        plotId = plotId,
                        rowIndex = rowZero,
                        colIndex = colZero,
                        label = "$row:$col",
                        observedTraits = observed,
                        totalTraits = totalTraits
                    )
                )
            }

            val grid = Array(maxRow) { arrayOfNulls<TrialPlotData?>(maxCol) }
            plots.forEach { plot ->
                if (plot.rowIndex < maxRow && plot.colIndex < maxCol) {
                    grid[plot.rowIndex][plot.colIndex] = plot
                }
            }

            withContext(Dispatchers.Main) {
                trialPlotDataList = plots
                trialGridRows = maxRow
                trialGridCols = maxCol
                trialGrid = grid
                isLoading = false
            }
        }
    }

    private fun getAttributeValue(unit: ObservationUnitModel, attrName: String): String? {
        return when (attrName) {
            "position_coordinate_x" -> unit.position_coordinate_x
            "position_coordinate_y" -> unit.position_coordinate_y
            "primary_id" -> unit.primary_id
            "secondary_id" -> unit.secondary_id
            "observation_unit_db_id" -> unit.observation_unit_db_id
            else -> {
                val uniqueName = preferences.getString(GeneralKeys.UNIQUE_NAME, "")
                database.getObservationUnitPropertyByPlotId(
                    uniqueName,
                    attrName,
                    unit.observation_unit_db_id
                )
            }
        }
    }


    @Composable
    fun TrialView(
        columnLocked: Boolean = true,
        wrapContent: Boolean = false,
        zoom: Float = 1f
    ) {
        if (trialGridRows == 0 || trialGridCols == 0) {
            Text(
                text = getString(R.string.trial_view_no_layout),
                color = Color(cellTextColor),
                textAlign = TextAlign.Center
            )
            return
        }

        val gridColors = rememberGridColors()

        val completeColor = Color(0xFF2E7D32)
        val partialColor = Color(0xFFF57C00)
        val emptyColor = Color(0xFFBDBDBD)

        // Filter and condense indices
        val activeRowIndices: List<Int>
        val activeColIndices: List<Int>
        val isCondensed = activeTrialFilter != TrialFilter.NONE

        if (isCondensed) {
            val matches = mutableListOf<Pair<Int, Int>>()
            for (r in 0 until trialGridRows) {
                for (c in 0 until trialGridCols) {
                    val plot = trialGrid[r][c]
                    val isMatch = when (activeTrialFilter) {
                        TrialFilter.NONE -> true
                        TrialFilter.COMPLETE -> plot?.status == TrialPlotStatus.COMPLETE
                        TrialFilter.PARTIAL -> plot?.status == TrialPlotStatus.PARTIAL
                        TrialFilter.EMPTY -> plot?.status == TrialPlotStatus.EMPTY
                        TrialFilter.MISSING -> plot == null
                    }
                    if (isMatch) matches.add(r to c)
                }
            }
            activeRowIndices = matches.map { it.first }.distinct().sorted()
            activeColIndices = matches.map { it.second }.distinct().sorted()
        } else {
            activeRowIndices = (0 until trialGridRows).toList()
            activeColIndices = (0 until trialGridCols).toList()
        }

        val displayRows = activeRowIndices.size
        val displayCols = activeColIndices.size
        val columnCount = displayCols + 1

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TrialLegend()

                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val availableWidth = maxWidth
                    val availableHeight = maxHeight

                    LazyTable(
                        modifier = Modifier.fillMaxSize(),
                        dimensions = lazyTableDimensions(
                            columnSize = { col ->
                                if (col == 0) (48 * zoom).dp
                                else if (wrapContent) {
                                    (64 * zoom).dp
                                } else {
                                    val cellW =
                                        ((availableWidth - 48.dp) / maxOf(displayCols, 1)) * zoom
                                    cellW.coerceIn(32.dp, 80.dp)
                                }
                            },
                            rowSize = { row ->
                                if (row == 0) (32 * zoom).dp
                                else if (wrapContent) {
                                    (48 * zoom).dp
                                } else {
                                    val cellH =
                                        ((availableHeight - 32.dp) / maxOf(displayRows, 1)) * zoom
                                    cellH.coerceIn(32.dp, 80.dp)
                                }
                            }
                        ),
                        contentPadding = PaddingValues(4.dp),
                        pinConfiguration = lazyTablePinConfiguration(
                            columns = if (columnLocked) 1 else 0,
                            rows = if (columnLocked) 1 else 0
                        )
                    ) {
                        // Header row (Column indices)
                        items(
                            count = columnCount,
                            layoutInfo = { LazyTableItem(column = it, row = 0) }
                        ) { col ->
                            val text =
                                if (col == 0) "" else activeColIndices[col - 1].let { it + 1 }
                                    .toString()
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .background(Color.LightGray.copy(alpha = 0.3f))
                                    .border(Dp.Hairline, gridColors.borderColor)
                            ) {
                                Text(
                                    text = text,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (10 * zoom).sp,
                                        color = Color(cellTextColor)
                                    )
                                )
                            }
                        }

                        // Row headers and Data cells
                        items(
                            count = displayRows * columnCount,
                            layoutInfo = {
                                val r = (it / columnCount) + 1
                                val c = it % columnCount
                                LazyTableItem(column = c, row = r)
                            }
                        ) { index ->
                            val rPrime = (index / columnCount)
                            val cPrime = index % columnCount

                            if (cPrime == 0) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .background(Color.LightGray.copy(alpha = 0.3f))
                                        .border(Dp.Hairline, gridColors.borderColor)
                                ) {
                                    Text(
                                        text = "${activeRowIndices[rPrime] + 1}",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (10 * zoom).sp,
                                            color = Color(cellTextColor)
                                        )
                                    )
                                }
                            } else {
                                val plotRow = activeRowIndices[rPrime]
                                val plotCol = activeColIndices[cPrime - 1]
                                val plot =
                                    if (plotRow < trialGrid.size && plotCol < trialGrid[plotRow].size) {
                                        trialGrid[plotRow][plotCol]
                                    } else null

                                val matchesFilter = when (activeTrialFilter) {
                                    TrialFilter.NONE -> true
                                    TrialFilter.COMPLETE -> plot?.status == TrialPlotStatus.COMPLETE
                                    TrialFilter.PARTIAL -> plot?.status == TrialPlotStatus.PARTIAL
                                    TrialFilter.EMPTY -> plot?.status == TrialPlotStatus.EMPTY
                                    TrialFilter.MISSING -> plot == null
                                }

                                val cellColor = when (plot?.status) {
                                    TrialPlotStatus.COMPLETE -> completeColor
                                    TrialPlotStatus.PARTIAL -> partialColor
                                    TrialPlotStatus.EMPTY -> emptyColor
                                    null -> Color.Transparent
                                }

                                val isSelectedTrial =
                                    plot != null && plot.plotId == selectedTrialPlotId
                                val cellBorder = if (isSelectedTrial) {
                                    Modifier.border(3.dp, Color(activeCellBgColor))
                                } else {
                                    Modifier.border(
                                        Dp.Hairline,
                                        gridColors.borderColor.copy(alpha = if (matchesFilter) 1f else 0.2f)
                                    )
                                }

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .background(
                                            if (matchesFilter) cellColor else cellColor.copy(
                                                alpha = 0.1f
                                            )
                                        )
                                        .then(cellBorder)
                                        .then(if (plot != null) Modifier.clickable {
                                            if (showCellDetails) {
                                                onTrialPlotClicked(plot)
                                            } else {
                                                navigateFromValueClicked(plot.plotId, 0, 1)
                                            }
                                        } else Modifier)
                                ) {
                                    val label = plot?.let {
                                        when {
                                            it.status == TrialPlotStatus.EMPTY -> ""
                                            it.status == TrialPlotStatus.COMPLETE -> "\u2713"
                                            else -> "~"
                                        }
                                    } ?: "\u2715"

                                    val textColor = if (plot == null) {
                                        Color.Gray.copy(alpha = if (matchesFilter) 0.5f else 0.1f)
                                    } else {
                                        Color.White.copy(alpha = if (matchesFilter) 1f else 0.1f)
                                    }

                                    Text(
                                        text = label,
                                        color = textColor,
                                        textAlign = TextAlign.Center,
                                        fontSize = when {
                                            displayRows * displayCols > 500 -> (8 * zoom).sp
                                            displayRows * displayCols > 200 -> (10 * zoom).sp
                                            else -> (12 * zoom).sp
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    @Composable
    fun TrialLegend() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(
                getString(R.string.trial_view_legend_complete),
                Color(0xFF2E7D32),
                isActive = activeTrialFilter == TrialFilter.COMPLETE,
                onClick = { toggleTrialFilter(TrialFilter.COMPLETE) }
            )
            LegendItem(
                getString(R.string.trial_view_legend_partial),
                Color(0xFFF57C00),
                isActive = activeTrialFilter == TrialFilter.PARTIAL,
                onClick = { toggleTrialFilter(TrialFilter.PARTIAL) }
            )
            LegendItem(
                getString(R.string.trial_view_legend_empty),
                Color(0xFFBDBDBD),
                isActive = activeTrialFilter == TrialFilter.EMPTY,
                onClick = { toggleTrialFilter(TrialFilter.EMPTY) }
            )
            LegendItem(
                getString(R.string.trial_view_legend_missing),
                Color.Transparent,
                "\u2715",
                Color.Gray,
                isActive = activeTrialFilter == TrialFilter.MISSING,
                onClick = { toggleTrialFilter(TrialFilter.MISSING) }
            )
        }
    }

    private fun toggleTrialFilter(filter: TrialFilter) {
        activeTrialFilter = if (activeTrialFilter == filter) TrialFilter.NONE else filter
    }

    @Composable
    fun LegendItem(
        label: String,
        color: Color,
        symbol: String? = null,
        symbolColor: Color = Color.White,
        isActive: Boolean = false,
        onClick: () -> Unit = {}
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    if (isActive) Color.Gray.copy(alpha = 0.2f) else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                )
                .border(
                    if (isActive) 1.dp else 0.dp,
                    Color.Gray,
                    shape = RoundedCornerShape(4.dp)
                )
                .clickable(onClick = onClick)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(color)
                    .border(0.5.dp, Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                if (symbol != null) {
                    Text(symbol, color = symbolColor, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 11.sp, color = Color(cellTextColor))
        }
    }

    /**
     * Bottom details panel that shows the currently selected cell's observation data.
     * For GRID view: shows the plot row header only.
     * For TRIAL view: shows plot and list of trait format icons with observations.
     * The Collect button is flush with the right side, full height, using an enter icon.
     */
    @Composable
    fun ObservationDetails(
        cell: SelectedCell?,
        viewMode: DataGridViewMode,
        onRepSelected: (Int) -> Unit,
        onCollectClicked: () -> Unit
    ) {
        val plotLabel = stringResource(R.string.act_data_grid_details_plot)
        val emptyHint = stringResource(R.string.act_data_grid_details_empty)
        val collectText = stringResource(R.string.act_data_grid_collect_button)
        val traitsWithDataTitle = stringResource(R.string.trial_view_traits_with_data)
        val noValue = stringResource(R.string.act_data_grid_details_no_value)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(filledCellBgColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                // Left side: details content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (cell == null) {
                        Text(
                            text = emptyHint,
                            color = Color(cellTextColor).copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    } else if (viewMode == DataGridViewMode.TRIAL) {
                        TrialDetails(
                            cell, plotLabel, mRowHeaders.getOrNull(cell.row)?.name ?: cell.plotId,
                            traitsWithDataTitle, noValue
                        )
                    } else {
                        GridDetails(
                            cell, plotLabel
                        )
                    }
                }

                // Right side: full-height Collect button
                if (cell != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(44.dp)
                            .background(Color(activeCellBgColor))
                            .clickable(onClick = onCollectClicked),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_table_arrow_right),
                            contentDescription = collectText,
                            tint = Color(activeCellTextColor),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun TrialDetails(
        cell: SelectedCell,
        plotLabel: String,
        rowHeader: String,
        traitsWithDataTitle: String,
        noValue: String
    ) {
        DetailsRow(plotLabel, rowHeader)

        Spacer(Modifier.height(8.dp))
        Text(
            text = traitsWithDataTitle,
            color = Color(cellTextColor),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(4.dp))

        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
        val traitIcons = mutableStateOf<List<Pair<TraitObject, Boolean>>>(emptyList())

        LaunchedEffect(cell.plotId) {
            val icons = mTraits.mapNotNull { trait ->
                val repeatedValues = database.getRepeatedValues(studyId, cell.plotId, trait.id)
                val hasValue = repeatedValues.any { it.value.isNotBlank() }
                if (hasValue) Pair(trait, true) else null
            }
            traitIcons.value = icons
        }

        if (traitIcons.value.isEmpty()) {
            Text(
                text = noValue,
                color = Color(cellTextColor).copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                traitIcons.value.forEach { (trait, _) ->
                    TraitFormatIcon(trait = trait)
                }
            }
        }
    }

    @Composable
    private fun GridDetails(
        cell: SelectedCell,
        plotLabel: String
    ) {
        val rowHeader = mRowHeaders.getOrNull(cell.row)?.name ?: cell.plotId
        DetailsRow(plotLabel, rowHeader)
    }

    @Composable
    private fun DetailsRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = label,
                color = Color(cellTextColor).copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(96.dp)
            )
            Text(
                text = value,
                color = Color(cellTextColor),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    fun TraitFormatIcon(trait: TraitObject) {
        val iconRes = when (trait.format.lowercase()) {
            "numeric" -> R.drawable.ic_trait_numeric
            "categorical", "qualitative" -> R.drawable.ic_trait_categorical
            "text" -> R.drawable.ic_trait_text
            "date" -> R.drawable.ic_trait_date
            "time" -> R.drawable.ic_trait_counter
            "photo" -> R.drawable.ic_trait_camera
            "video" -> R.drawable.ic_trait_gopro
            "audio" -> R.drawable.ic_trait_audio
            else -> R.drawable.ic_trait_numeric
        }
        val color = when (trait.format.lowercase()) {
            "numeric" -> Color(0xFF2196F3)
            "categorical", "qualitative" -> Color(0xFF4CAF50)
            "text" -> Color(0xFF9C27B0)
            "date" -> Color(0xFFFF9800)
            "time" -> Color(0xFF00BCD4)
            "photo" -> Color(0xFFE91E63)
            "video" -> Color(0xFF673AB7)
            "audio" -> Color(0xFF795548)
            else -> Color.Gray
        }

        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            val painter = painterResource(id = iconRes)
            Icon(
                painter = painter,
                contentDescription = trait.alias,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    @Composable
    fun DataGridTable(
        state: DataGridViewModel.UiState.Loaded,
        columnLocked: Boolean = true,
        sortState: DataGridViewModel.SortState = DataGridViewModel.SortState(),
        wrapContent: Boolean = false,
        heatmapEnabled: Boolean = false,
        zoom: Float = 1f
    ) {
        val traits = state.traits
        val rowHeaders = state.rowHeaders
        val plotIds = state.plotIds
        val gridData = state.gridData
        val extraHeaderNames = state.extraHeaderNames
        val extraHeaderData = state.extraHeaderData
        val extraCount = extraHeaderNames.size

        if (traits.isEmpty() || rowHeaders.isEmpty()) {
            return
        }

        val lazyTableState = rememberSaveableLazyTableState()

        val columnCount = traits.size + extraCount
        val rowCount = rowHeaders.size + 1

        val activeTraitIdx: Int = activeTrait?.let { pos ->
            traits.indexOfFirst { it.realPosition == pos }
        }?.takeIf { it >= 0 } ?: 0
        val targetColumn = extraCount + activeTraitIdx
        val targetRow = activePlotId ?: 1

        var hasScrolled by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(rowHeaders.size) {
            Log.d("DataGridActivity", "Data loaded: ${traits.size} traits, ${rowHeaders.size} rows")
            if (!hasScrolled && traits.isNotEmpty() && rowHeaders.isNotEmpty()
                && targetColumn < columnCount && targetRow <= rowHeaders.size
            ) {
                lazyTableState.animateToCell(column = targetColumn, row = targetRow)
                hasScrolled = true
            }
        }

        val columnWidths: List<Dp> = remember(state, wrapContent) {
            if (!wrapContent) emptyList()
            else (0 until columnCount).map { col ->
                val headerLen = when {
                    col < extraCount -> extraHeaderNames[col].length
                    else -> traits.getOrNull(col - extraCount)?.alias?.length ?: 0
                }
                val maxDataLen = when {
                    col < extraCount -> extraHeaderData.maxOfOrNull {
                        it.getOrNull(col)?.length ?: 0
                    } ?: 0

                    else -> gridData.maxOfOrNull { row ->
                        row.getOrNull(col - extraCount)?.value?.length ?: 0
                    } ?: 0
                }
                val maxLen = maxOf(headerLen, maxDataLen).coerceAtLeast(1)
                (maxLen * 10f + 16f).dp.coerceAtLeast(60.dp)
            }
        }

        val rowHeights: List<Dp> = remember(state, wrapContent, columnWidths) {
            if (!wrapContent) emptyList()
            else (0 until rowCount).map { row ->
                val maxLines = (0 until columnCount).maxOf { col ->
                    val colWidthPx = (columnWidths.getOrNull(col) ?: 100.dp).value
                    val charsPerLine = ((colWidthPx - 16f) / 10f).toInt().coerceAtLeast(1)
                    val textLen = when {
                        row == 0 -> when {
                            col < extraCount -> extraHeaderNames[col].length
                            else -> traits.getOrNull(col - extraCount)?.alias?.length ?: 0
                        }

                        col < extraCount -> extraHeaderData.getOrNull(row - 1)
                            ?.getOrNull(col)?.length ?: 0

                        else -> gridData.getOrNull(row - 1)
                            ?.getOrNull(col - extraCount)?.value?.length ?: 0
                    }.coerceAtLeast(1)
                    ceil(textLen.toFloat() / charsPerLine).toInt().coerceAtLeast(1)
                }
                (maxLines * 20 + 16).dp.coerceAtLeast(48.dp)
            }
        }

        val columnHeatmapRanges: List<Pair<Double, Double>?> = remember(state, heatmapEnabled) {
            if (!heatmapEnabled) List(traits.size) { null }
            else traits.indices.map { colIdx ->
                val nums =
                    gridData.mapNotNull { row -> row.getOrNull(colIdx)?.value?.toHeatmapDouble() }
                if (nums.size < 2) null else Pair(nums.min(), nums.max())
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyTable(
                state = lazyTableState,
                dimensions = lazyTableDimensions(
                    columnSize = { col ->
                        if (wrapContent) (columnWidths.getOrNull(col) ?: 100.dp) * zoom
                        else if (col < extraCount) 120.dp * zoom else 100.dp * zoom
                    },
                    rowSize = { row ->
                        if (wrapContent) (rowHeights.getOrNull(row) ?: 48.dp) * zoom
                        else 48.dp * zoom
                    }
                ),
                contentPadding = PaddingValues(0.dp),
                pinConfiguration = lazyTablePinConfiguration(
                    columns = if (columnLocked) extraCount else 0,
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
                    if (index < extraCount) {
                        HeaderCell(
                            text = extraHeaderNames[index],
                            sortIconRes = sortIcon,
                            onClick = { viewModel.sortByColumn(index) },
                            wrapContent = wrapContent,
                            zoom = zoom
                        )
                    } else {
                        val traitIndex = index - extraCount
                        HeaderCell(
                            text = if (traitIndex < traits.size) traits[traitIndex].alias else "",
                            sortIconRes = sortIcon,
                            onClick = { viewModel.sortByColumn(index) },
                            wrapContent = wrapContent,
                            zoom = zoom
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

                    if (column < extraCount) {
                        RowHeaderCell(
                            text = extraHeaderData.getOrNull(row)?.getOrNull(column) ?: "",
                            wrapContent = wrapContent,
                            zoom = zoom
                        )
                    } else {
                        val columnIndex = column - extraCount
                        val cellData =
                            if (row < gridData.size && columnIndex < gridData[row].size)
                                gridData[row][columnIndex]
                            else null

                        val heatmapColor: Color? = run {
                            val range = columnHeatmapRanges.getOrNull(columnIndex)
                            val numVal = cellData?.value?.toHeatmapDouble()
                            if (range != null && numVal != null && range.first != range.second) {
                                val t =
                                    ((numVal - range.first) / (range.second - range.first)).toFloat()
                                        .coerceIn(0f, 1f)
                                lerpHeatmapColor(t)
                            } else null
                        }

                        DataCell(
                            value = cellData?.value ?: "",
                            isHighlighted = (plotIds.getOrNull(row) == activePlotIdString && columnIndex == activeTraitIdx),
                            isSelected = (row + 1 == selectedPlotId && columnIndex + 1 == selectedTrait),
                            heatmapColor = heatmapColor,
                            wrapContent = wrapContent,
                            zoom = zoom,
                            onClick = {
                                if (cellData != null && row < plotIds.size) {
                                    if (showCellDetails) {
                                        onCellClickedFromGrid(row, columnIndex, traits, plotIds)
                                    } else {
                                        navigateFromValueClicked(plotIds[row], columnIndex, 1)
                                    }
                                }
                            },
                            onLongClick = {
                                if (cellData != null && row < plotIds.size) {
                                    navigateFromValueClicked(plotIds[row], columnIndex, 1)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun HeaderCell(
        text: String,
        sortIconRes: Int? = null,
        onClick: (() -> Unit)? = null,
        wrapContent: Boolean = false,
        zoom: Float = 1f
    ) {
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
                    fontSize = (13 * zoom).sp,
                    overflow = if (wrapContent) TextOverflow.Clip else TextOverflow.Ellipsis,
                    maxLines = if (wrapContent) Int.MAX_VALUE else 1,
                    softWrap = wrapContent,
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
    fun RowHeaderCell(text: String, wrapContent: Boolean = false, zoom: Float = 1f) {
        TableCell(
            text = text,
            backgroundColor = Color.White,
            textColor = Color(cellTextColor),
            wrapContent = wrapContent,
            zoom = zoom
        )
    }

    @Composable
    fun DataCell(
        value: String,
        isHighlighted: Boolean = false,
        isSelected: Boolean = false,
        heatmapColor: Color? = null,
        wrapContent: Boolean = false,
        zoom: Float = 1f,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) {
        val backgroundColor = when {
            isHighlighted -> Color(activeCellBgColor)
            isSelected -> Color(activeCellBgColor).copy(alpha = 0.6f)
            heatmapColor != null -> heatmapColor
            value.isNotBlank() -> Color(filledCellBgColor)
            else -> Color(emptyCellBgColor)
        }

        val textColor =
            if (isHighlighted || isSelected) Color(activeCellTextColor) else Color(cellTextColor)

        val borderModifier = if (isSelected) {
            Modifier.border(2.dp, Color(activeCellBgColor))
        } else {
            Modifier.border(Dp.Hairline, Color(cellTextColor))
        }

        TableCell(
            text = value,
            backgroundColor = backgroundColor,
            textColor = textColor,
            borderModifier = borderModifier,
            onClick = onClick,
            onLongClick = onLongClick,
            isClickable = true,
            wrapContent = wrapContent,
            zoom = zoom
        )
    }

    @Composable
    fun TableCell(
        text: String,
        backgroundColor: Color,
        textColor: Color,
        borderModifier: Modifier = Modifier.border(Dp.Hairline, Color(cellTextColor)),
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {},
        isClickable: Boolean = false,
        wrapContent: Boolean = false,
        zoom: Float = 1f
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(backgroundColor)
                .then(borderModifier)
                .then(
                    if (isClickable) Modifier
                        .clickable(onClick = onClick)
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    else Modifier
                )
        ) {
            Text(
                text = text,
                color = textColor,
                textAlign = TextAlign.Center,
                fontSize = (14 * zoom).sp,
                overflow = if (wrapContent) TextOverflow.Clip else TextOverflow.Ellipsis,
                maxLines = if (wrapContent) Int.MAX_VALUE else 1,
                softWrap = wrapContent,
            )
        }
    }


    private fun getCurrentRowHeader(): String {
        return preferences.getString(GeneralKeys.UNIQUE_NAME, "") ?: ""
    }

    private fun getDisplayHeaders(): List<String> {
        val uniqueHeader = getCurrentRowHeader()
        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val unitAttributes = database.getAllObservationUnitAttributeNames(studyId)
        val savedHeaders = preferences.getStringSet(GeneralKeys.DATAGRID_EXTRA_HEADERS, null)
        return if (savedHeaders == null) {
            listOf(uniqueHeader).filter { it in unitAttributes }
        } else {
            val others = unitAttributes.filter { it in savedHeaders && it != uniqueHeader }
            if (uniqueHeader in savedHeaders) listOf(uniqueHeader) + others else others
        }
    }

    private fun onCellClickedFromGrid(
        row: Int,
        col: Int,
        traits: List<TraitObject>,
        plotIds: List<String>
    ) {
        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
        val plotId = plotIds[row]

        // Check if clicking the same cell - deselect if so
        if (selectedPlotId == row + 1 && selectedTrait == col + 1) {
            selectedCell = null
            selectedPlotId = null
            selectedTrait = null
            return
        }

        val trait = traits[col]
        val repeatedValues = database.getRepeatedValues(studyId, plotId, trait.id)

        // Mark this cell as the last clicked one
        selectedPlotId = row + 1
        selectedTrait = col + 1

        // Populate details panel
        selectedCell = SelectedCell(
            row = row,
            col = col,
            plotId = plotId,
            traitIndex = col,
            repeated = repeatedValues.toList()
        )
    }

    private fun onTrialPlotClicked(plot: TrialPlotData) {
        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()

        // Check if clicking the same plot - deselect if so
        if (selectedTrialPlotId == plot.plotId) {
            selectedCell = null
            selectedTrialPlotId = null
            return
        }

        val firstTraitId = mTraits.firstOrNull()?.id ?: ""
        val repeatedValues = if (firstTraitId.isNotBlank()) {
            database.getRepeatedValues(studyId, plot.plotId, firstTraitId)
        } else emptyArray<ObservationModel>()

        selectedTrialPlotId = plot.plotId

        selectedCell = SelectedCell(
            row = trialPlotDataList.indexOfFirst { it.plotId == plot.plotId },
            col = 0,
            plotId = plot.plotId,
            traitIndex = 0,
            repeated = repeatedValues.toList()
        )
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
                val traitIndex =
                    traits.indexOfFirst { it.id == value.observation_variable_db_id.toString() }

                navigateFromValueClicked(plotId, traitIndex, which + 1)
                dialog.dismiss()
            }.create().show()
    }

    private fun showHeaderPickerDialog() {
        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val uniqueHeader = preferences.getString(GeneralKeys.UNIQUE_NAME, "") ?: ""

        lifecycleScope.launch {
            val allAttributes = withContext(Dispatchers.IO) {
                database.getAllObservationUnitAttributeNames(studyId)
            }

            if (allAttributes.isEmpty()) return@launch

            val savedHeaders = preferences.getStringSet(GeneralKeys.DATAGRID_EXTRA_HEADERS, null)

            val otherAttributes = allAttributes.filter { it != uniqueHeader }
            val displayItems =
                (if (uniqueHeader in allAttributes) listOf(uniqueHeader) else emptyList()) + otherAttributes

            if (displayItems.isEmpty()) return@launch

            val checkedItems = BooleanArray(displayItems.size) { idx ->
                if (savedHeaders == null) idx == 0
                else displayItems[idx] in savedHeaders
            }

            AlertDialog.Builder(this@DataGridActivity, R.style.AppAlertDialog)
                .setTitle(R.string.dialog_data_grid_header_picker_title)
                .setMultiChoiceItems(
                    displayItems.toTypedArray(),
                    checkedItems
                ) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val newSelected =
                        displayItems.filterIndexed { idx, _ -> checkedItems[idx] }.toSet()
                    preferences.edit {
                        putStringSet(
                            GeneralKeys.DATAGRID_EXTRA_HEADERS,
                            newSelected
                        )
                    }
                    val orderedDisplay = displayItems.filter { it in newSelected }
                    val currentHeaders =
                        (viewModel.uiState.value as? DataGridViewModel.UiState.Loaded)
                            ?.extraHeaderNames
                    if (orderedDisplay != currentHeaders && viewMode == DataGridViewMode.GRID) {
                        viewModel.loadGrid(getCurrentRowHeader(), orderedDisplay)
                    }
                }
                .create().show()
        }
    }

    private fun String.toHeatmapDouble(): Double? {
        if (isBlank() || equals("NA", ignoreCase = true) || this == "...") return null
        toDoubleOrNull()?.let { return it }
        return when (lowercase()) {
            "true", "yes" -> 1.0
            "false", "no" -> 0.0
            else -> null
        }
    }

    private fun lerpHeatmapColor(t: Float): Color {
        val low = Color(0xFFF44336.toInt())
        val mid = Color(0xFFFFEB3B.toInt())
        val high = Color(0xFF4CAF50.toInt())
        return if (t < 0.5f) {
            val s = t * 2f
            Color(
                red = low.red + (mid.red - low.red) * s,
                green = low.green + (mid.green - low.green) * s,
                blue = low.blue + (mid.blue - low.blue) * s,
                alpha = 1f
            )
        } else {
            val s = (t - 0.5f) * 2f
            Color(
                red = mid.red + (high.red - mid.red) * s,
                green = mid.green + (high.green - mid.green) * s,
                blue = mid.blue + (high.blue - mid.blue) * s,
                alpha = 1f
            )
        }
    }
}
