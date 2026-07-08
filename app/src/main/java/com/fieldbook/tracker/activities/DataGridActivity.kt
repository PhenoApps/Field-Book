package com.fieldbook.tracker.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.core.content.edit
import androidx.core.database.getStringOrNull
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.models.ObservationUnitModel
import com.fieldbook.tracker.databinding.ActivityDataGridBinding
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.ui.grid.datagrid.DataGridMapView
import com.fieldbook.tracker.ui.grid.datagrid.DataGridTable
import com.fieldbook.tracker.ui.grid.datagrid.DataGridUiColors
import com.fieldbook.tracker.ui.grid.datagrid.DataGridViewMode
import com.fieldbook.tracker.ui.grid.datagrid.MapFilter
import com.fieldbook.tracker.ui.grid.datagrid.MapPlotData
import com.fieldbook.tracker.utilities.InsetHandler
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.viewmodels.DataGridViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
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
class DataGridActivity : ThemedActivity() {

    private val viewModel: DataGridViewModel by viewModels()

    companion object {
        private const val TAG = "DataGridActivity"
        private const val ROW_HEADER_DELIMITER = " "
    }

    // for active highlighted cell (navigated from)
    private var activePlotId: Int? = null
    private var activeTrait: Int? = null
    private var activePlotIdString: String? by mutableStateOf(null)

    private var activeCellBgColor: Int = 0
    private var filledCellBgColor: Int = 0
    private var emptyCellBgColor: Int = 0
    private var activeCellTextColor: Int = 0
    private var cellTextColor: Int = 0

    private var isLoading by mutableStateOf(true)

    // Map view state
    private var viewMode by mutableStateOf(DataGridViewMode.GRID)
    private var activeMapFilter by mutableStateOf(MapFilter.NONE)
    private var mapPlotDataList by mutableStateOf(emptyList<MapPlotData>())
    private var mapGridRows by mutableIntStateOf(0)
    private var mapGridCols by mutableIntStateOf(0)
    private var mapGrid by mutableStateOf<Array<Array<MapPlotData?>>>(emptyArray())

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
        val mapViewEnabled = preferences.getBoolean(GeneralKeys.MAP_VIEW_SETTING, false)
        viewMode = if (mapViewEnabled) {
            DataGridViewMode.MAP
        } else {
            DataGridViewMode.GRID
        }

        // Trigger grid load — ViewModel survives rotation so this is a no-op on re-creation
        // if the grid is already loaded
        if (viewModel.uiState.value is DataGridViewModel.UiState.Loading) {
            lifecycleScope.launch {
                val headers = withContext(Dispatchers.IO) { getDisplayHeaders() }
                viewModel.loadGrid(getCurrentRowHeader(), headers)
            }
        }

        // Load map data if needed
        if (mapPlotDataList.isEmpty()) {
            loadMapData()
        }

        binding.composeView.setContent {
            val uiState by viewModel.uiState.collectAsState()
            val columnLocked by viewModel.columnLocked.collectAsState()
            val lockedColumnIds by viewModel.lockedColumnIds.collectAsState()
            val sortState by viewModel.sortState.collectAsState()
            val wrapContent by viewModel.wrapContent.collectAsState()
            val heatmapEnabled by viewModel.heatmapEnabled.collectAsState()
            val zoomLevel by viewModel.zoomLevel.collectAsState()
            val dataGridColors = DataGridUiColors(
                activeCellBgColor = activeCellBgColor,
                filledCellBgColor = filledCellBgColor,
                emptyCellBgColor = emptyCellBgColor,
                activeCellTextColor = activeCellTextColor,
                cellTextColor = cellTextColor
            )

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
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (isLoading && viewMode == DataGridViewMode.MAP) {
                            CircularProgressIndicator(color = Color(activeCellBgColor))
                        } else if (viewMode == DataGridViewMode.MAP) {
                            DataGridMapView(
                                mapGrid = mapGrid,
                                mapGridRows = mapGridRows,
                                mapGridCols = mapGridCols,
                                activeMapFilter = activeMapFilter,
                                colors = dataGridColors,
                                columnLocked = columnLocked,
                                wrapContent = wrapContent,
                                zoom = zoomLevel,
                                onMissingLayout = ::showMissingMapLayoutPicker,
                                onFilterClicked = ::toggleMapFilter,
                                onPlotClicked = { plot ->
                                    navigateFromValueClicked(plot.plotId, 0, 1)
                                },
                                onToggleLock = viewModel::toggleColumnLock
                            )
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
                                        state = state,
                                        colors = dataGridColors,
                                        lockedColumnIds = lockedColumnIds,
                                        sortState = sortState,
                                        wrapContent = wrapContent,
                                        heatmapEnabled = heatmapEnabled,
                                        zoom = zoomLevel,
                                        activePlotId = activePlotId,
                                        activePlotIdString = activePlotIdString,
                                        activeTrait = activeTrait,
                                        onSortByColumn = viewModel::sortByColumn,
                                        onToggleColumn = viewModel::toggleColumn,
                                        onNavigateFromValue = ::navigateFromValueClicked
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
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, standardBackCallback())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_data_grid, menu)
        val isGrid = viewMode == DataGridViewMode.GRID

        val mapItem = menu.findItem(R.id.menu_data_grid_action_map_view)
        mapItem?.let {
            it.title = if (isGrid) {
                getString(R.string.menu_data_grid_action_map_view)
            } else {
                getString(R.string.menu_data_grid_action_grid_view)
            }
            it.setIcon(if (isGrid) R.drawable.map else R.drawable.table)
        }

        menu.findItem(R.id.menu_data_grid_action_header_view)?.let {
            it.isVisible = true
            it.title = if (isGrid) getString(R.string.menu_action_header_view_data_grid_title)
            else getString(R.string.map_view_choose_attributes_title)
        }

        menu.findItem(R.id.menu_data_grid_action_heatmap)?.isVisible = isGrid

        menu.findItem(R.id.menu_data_grid_action_reset_sort)?.isVisible =
            isGrid && viewModel.sortState.value.columnIndex >= 0
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val isGrid = viewMode == DataGridViewMode.GRID

        val resetSortItem = menu.findItem(R.id.menu_data_grid_action_reset_sort)
        resetSortItem?.isVisible = isGrid && viewModel.sortState.value.columnIndex >= 0

        val mapItem = menu.findItem(R.id.menu_data_grid_action_map_view)
        mapItem?.let {
            it.title = if (isGrid) {
                getString(R.string.menu_data_grid_action_map_view)
            } else {
                getString(R.string.menu_data_grid_action_grid_view)
            }
            it.setIcon(if (isGrid) R.drawable.map else R.drawable.table)
        }

        val wrapItem = menu.findItem(R.id.menu_data_grid_action_wrap_content)
        val isWrapped = viewModel.wrapContent.value
        wrapItem?.setIcon(if (isWrapped) R.drawable.arrow_collapse_horizontal else R.drawable.arrow_expand_horizontal)

        menu.findItem(R.id.menu_data_grid_action_header_view)?.let {
            it.isVisible = true
            it.title = if (isGrid) getString(R.string.menu_action_header_view_data_grid_title)
            else getString(R.string.map_view_choose_attributes_title)
        }

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

            R.id.menu_data_grid_action_wrap_content -> {
                viewModel.toggleWrapContent()
            }

            R.id.menu_data_grid_action_header_view -> {
                if (viewMode == DataGridViewMode.GRID) {
                    showHeaderPickerDialog()
                } else {
                    showMapLayoutPickerDialog()
                }
            }

            R.id.menu_data_grid_action_reset_sort -> {
                viewModel.resetSort()
            }

            R.id.menu_data_grid_action_heatmap -> {
                viewModel.toggleHeatmap()
            }

            R.id.menu_data_grid_action_map_view -> {
                toggleViewMode()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun toggleViewMode() {
        viewMode = if (viewMode == DataGridViewMode.GRID) {
            DataGridViewMode.MAP
        } else {
            DataGridViewMode.GRID
        }
        preferences.edit {
            putBoolean(GeneralKeys.MAP_VIEW_SETTING, viewMode != DataGridViewMode.GRID)
        }
        // Reload data for the new mode if needed
        if (viewMode != DataGridViewMode.GRID) {
            loadMapData()
        }
        invalidateOptionsMenu()
    }

    /**
     * Shows a dialog to choose both row (Y) and column (X) attributes for the map view layout.
     */
    private fun showMapLayoutPickerDialog() {
        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val units = database.getAllObservationUnits(studyId) ?: emptyArray<ObservationUnitModel>()
        val geoCount = units.count { !it.geo_coordinates.isNullOrBlank() }

        val propColumns = database.getAllObservationUnitAttributeNames(studyId).toList()
        val coreColumns = database.existingObservationUnitCoreColumns
        val allColumns = (propColumns + coreColumns.filter { it !in propColumns }).toTypedArray()

        if (allColumns.isEmpty()) return

        val currentRowAttr = preferences.getString(GeneralKeys.MAP_ROW_ATTR, "") ?: ""
        val currentColAttr = preferences.getString(GeneralKeys.MAP_COL_ATTR, "") ?: ""

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                16f,
                resources.displayMetrics
            ).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val rowLabel =
            TextView(this).apply { text = context.getString(R.string.map_view_row_prompt) }
        val rowSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@DataGridActivity,
                android.R.layout.simple_spinner_dropdown_item,
                allColumns
            )
            setSelection(allColumns.indexOfFirst { it == currentRowAttr }.coerceAtLeast(0))
        }

        val colLabel = TextView(this).apply {
            text = context.getString(R.string.map_view_col)
            setPadding(
                0,
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    16f,
                    resources.displayMetrics
                ).toInt(),
                0,
                0
            )
        }
        val colSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@DataGridActivity,
                android.R.layout.simple_spinner_dropdown_item,
                allColumns
            )
            setSelection(allColumns.indexOfFirst { it == currentColAttr }.coerceAtLeast(0))
        }

        layout.addView(rowLabel)
        layout.addView(rowSpinner)
        layout.addView(colLabel)
        layout.addView(colSpinner)

        val builder = AlertDialog.Builder(this, R.style.AppAlertDialog)
            .setTitle(R.string.map_view_choose_attributes_title)
            .setView(layout)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val rowAttr = allColumns[rowSpinner.selectedItemPosition]
                val colAttr = allColumns[colSpinner.selectedItemPosition]
                preferences.edit {
                    putString(GeneralKeys.MAP_ROW_ATTR, rowAttr)
                    putString(GeneralKeys.MAP_COL_ATTR, colAttr)
                }
                loadMapData()
            }
            .setNegativeButton(android.R.string.cancel, null)

        if (geoCount > 0) {
            builder.setNeutralButton(
                getString(
                    R.string.map_view_use_geo_coordinates,
                    geoCount
                )
            ) { _, _ ->
                preferences.edit {
                    putString(GeneralKeys.MAP_ROW_ATTR, "geo_coordinates")
                    putString(GeneralKeys.MAP_COL_ATTR, "geo_coordinates")
                    putBoolean(GeneralKeys.MAP_VIEW_SETTING, true)
                }
                viewMode = DataGridViewMode.MAP
                loadMapData()
            }
        }

        builder.create().show()
    }

    private fun showMissingMapLayoutPicker() {
        showMapLayoutPickerDialog()
    }

    private fun toggleMapFilter(filter: MapFilter) {
        activeMapFilter = if (activeMapFilter == filter) MapFilter.NONE else filter
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

    private fun loadMapData() {
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

        val savedRowAttr = preferences.getString(GeneralKeys.MAP_ROW_ATTR, "") ?: ""
        val savedColAttr = preferences.getString(GeneralKeys.MAP_COL_ATTR, "") ?: ""

        val unitAttributeNames = database.getAllObservationUnitAttributeNames(studyId)
        val coreFieldNames = database.existingObservationUnitCoreColumns
        val allValidNames = unitAttributeNames + coreFieldNames.filter { it !in unitAttributeNames }

        val defaultRowAttr = allValidNames.firstOrNull {
            it.contains("row", ignoreCase = true) || it.contains("range", ignoreCase = true)
        } ?: allValidNames.firstOrNull() ?: "position_coordinate_y"

        val defaultColAttr = allValidNames.firstOrNull {
            it.contains("col", ignoreCase = true) || it.contains("column", ignoreCase = true)
        } ?: allValidNames.getOrNull(1) ?: "position_coordinate_x"

        val rowAttrName = savedRowAttr.ifBlank { defaultRowAttr }
        val colAttrName = savedColAttr.ifBlank { defaultColAttr }

        val isSpatialMode = (rowAttrName == "geo_coordinates" && colAttrName == "geo_coordinates")

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

            if (!isSpatialMode && (effectiveRowAttr == null || effectiveColAttr == null)) {
                withContext(Dispatchers.Main) {
                    mapPlotDataList = emptyList()
                    mapGridRows = 0
                    mapGridCols = 0
                    mapGrid = emptyArray()
                    isLoading = false
                }
                return@launch
            }

            val plots = mutableListOf<MapPlotData>()
            var maxRow = 0
            var maxCol = 0

            if (isSpatialMode) {
                val unitLocations = units.mapNotNull { unit ->
                    val loc =
                        com.fieldbook.tracker.utilities.GeodeticUtils.parseGeoCoordinate(unit.geo_coordinates)
                    if (loc != null) unit to loc else null
                }

                if (unitLocations.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        mapPlotDataList = emptyList()
                        mapGridRows = 0
                        mapGridCols = 0
                        mapGrid = emptyArray()
                        isLoading = false
                    }
                    return@launch
                }

                // Get unique sorted latitudes (descending, North to South) and longitudes (ascending, West to East)
                val sortedLats =
                    unitLocations.map { it.second.latitude }.distinct().sortedDescending()
                val sortedLons = unitLocations.map { it.second.longitude }.distinct().sorted()

                maxRow = sortedLats.size
                maxCol = sortedLons.size

                unitLocations.forEach { (unit, loc) ->
                    val rowIndex = sortedLats.indexOf(loc.latitude)
                    val colIndex = sortedLons.indexOf(loc.longitude)

                    val plotId = unit.observation_unit_db_id
                    val observed = plotObservationCounts[plotId] ?: 0
                    plots.add(
                        MapPlotData(
                            plotId = plotId,
                            rowIndex = rowIndex,
                            colIndex = colIndex,
                            label = String.format(
                                java.util.Locale.US,
                                "%.5f, %.5f",
                                loc.latitude,
                                loc.longitude
                            ),
                            observedTraits = observed,
                            totalTraits = totalTraits
                        )
                    )
                }
            } else {
                units.forEach { unit ->
                    val rowStr = getAttributeValue(unit, effectiveRowAttr!!)
                    val colStr = getAttributeValue(unit, effectiveColAttr!!)
                    val row = rowStr?.toIntOrNull() ?: return@forEach
                    val col = colStr?.toIntOrNull() ?: return@forEach
                    val rowZero = (row - 1).coerceAtLeast(0)
                    val colZero = (col - 1).coerceAtLeast(0)
                    maxRow = maxOf(maxRow, rowZero + 1)
                    maxCol = maxOf(maxCol, colZero + 1)
                    val plotId = unit.observation_unit_db_id
                    val observed = plotObservationCounts[plotId] ?: 0
                    plots.add(
                        MapPlotData(
                            plotId = plotId,
                            rowIndex = rowZero,
                            colIndex = colZero,
                            label = "$row:$col",
                            observedTraits = observed,
                            totalTraits = totalTraits
                        )
                    )
                }
            }

            val grid = Array(maxRow) { arrayOfNulls<MapPlotData?>(maxCol) }
            plots.forEach { plot ->
                if (plot.rowIndex < maxRow && plot.colIndex < maxCol) {
                    grid[plot.rowIndex][plot.colIndex] = plot
                }
            }

            withContext(Dispatchers.Main) {
                mapPlotDataList = plots
                mapGridRows = maxRow
                mapGridCols = maxCol
                mapGrid = grid
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
            "geo_coordinates" -> unit.geo_coordinates
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


    private fun getCurrentRowHeader(): String {
        return preferences.getString(GeneralKeys.UNIQUE_NAME, "") ?: ""
    }

    private fun getDisplayHeaders(): List<String> {
        val uniqueHeader = getCurrentRowHeader()
        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val unitAttributes = database.getAllObservationUnitAttributeNames(studyId)
        val coreFieldNames = database.existingObservationUnitCoreColumns
        val allValidNames = unitAttributes + coreFieldNames.filter { it !in unitAttributes }

        val savedHeaders = preferences.getStringSet(GeneralKeys.DATAGRID_EXTRA_HEADERS, null)
        return if (savedHeaders == null) {
            val base = listOf<String>(uniqueHeader)
            if ("geo_coordinates" in allValidNames) {
                (base + "geo_coordinates").filter { it in allValidNames }
            } else {
                base.filter { it in allValidNames }
            }
        } else {
            val others = allValidNames.filter { it in savedHeaders && it != uniqueHeader }
            if (uniqueHeader in savedHeaders) listOf<String>(uniqueHeader) + others else others
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

            val dialog = AlertDialog.Builder(this@DataGridActivity, R.style.AppAlertDialog)
                .setTitle(getString(R.string.dialog_data_grid_column_picker_title))
                .setMultiChoiceItems(
                    displayItems.toTypedArray(),
                    checkedItems
                ) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.dialog_fragment_summary_neutral_button, null)
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
                .create()

            dialog.show()

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val allChecked = checkedItems.all { it }
                for (i in checkedItems.indices) {
                    checkedItems[i] = !allChecked
                    dialog.listView.setItemChecked(i, !allChecked)
                }
            }
        }
    }

}
