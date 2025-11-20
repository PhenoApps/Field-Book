package com.fieldbook.tracker.activities

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import androidx.constraintlayout.widget.Group
import androidx.core.database.getStringOrNull
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.evrencoskun.tableview.TableView
import com.evrencoskun.tableview.listener.ITableViewListener
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.DataGridAdapter
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.databinding.ActivityDataGridOldBinding
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.CategoryJsonUtil.Companion.decode
import com.fieldbook.tracker.utilities.Utils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.core.content.edit

/**
 * @author Chaney
 *
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
class OldDataGridActivity : ThemedActivity(), CoroutineScope by MainScope(), ITableViewListener {

    /***
     * Polymorphism class structure to serve different cell types to the grid.
     */
    open class BlockData(open val code: String) {
        override fun hashCode(): Int {
            return code.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BlockData

            if (code != other.code) return false

            return true
        }
    }

    data class HeaderData(val name: String, override val code: String) : BlockData(code)
    data class CellData(val value: String?, override val code: String, val color: Int = Color.GREEN, val onClick: View.OnClickListener? = null): BlockData(code)
    class EmptyCell(override val code: String): BlockData(code)

    //coroutine scope for launching background processes
    private val scope by lazy {
        CoroutineScope(Dispatchers.IO)
    }

    /**
     * views that are initialized in oncreate
     */
    private lateinit var mTableView: TableView
    private lateinit var progressBar: ProgressBar
    private lateinit var dataGridGroup: Group

    /**
     * Adapters/lists used to store grid information, also used for click events
     */
    private lateinit var mAdapter: DataGridAdapter
    private lateinit var mRowHeaders: ArrayList<String>
    private lateinit var mPlotIds: ArrayList<String>
    private var mTraits: ArrayList<TraitObject> = ArrayList()

    @Inject
    lateinit var database: DataHelper

    @Inject
    lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        //this activity uses databinding to inflate the content layout
        //this creates a 'binding' variable that has all the views as fields, an alternative to findViewById
        val binding = DataBindingUtil.setContentView<ActivityDataGridOldBinding>(
            this,
            R.layout.activity_data_grid_old
        )

        setSupportActionBar(binding.toolbar)

        if (supportActionBar != null) {
            supportActionBar?.title = null
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeButtonEnabled(true)
        }

        progressBar = binding.dataGridProgressBar
        dataGridGroup = binding.dataGridGroup
        mTableView = binding.tableView

        initialize(plotId = intent.extras?.getInt("plot_id"),
            trait = intent.extras?.getInt("trait"))
    }

    /**
     * Runs the data grid loading.
     */
    private fun initialize(plotId: Int? = null,
                           trait: Int? = null) {

        //if something goes wrong finish the activity
        try {

            loadGridData(plotId, trait)

        } catch (e: Exception) {

            e.printStackTrace()

            setResult(RESULT_CANCELED)

            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        menuInflater.inflate(R.menu.menu_data_grid, menu)

        return super.onCreateOptionsMenu(menu)
    }

    //finish activity when back button is pressed
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
            }
            R.id.menu_data_grid_action_header_view -> {

                val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
                // get all available obs. property columns
                val columns = database.getAllObservationUnitAttributeNames(studyId)

                if (columns.isNotEmpty()) {
                    val rowHeader = getCurrentRowHeader()
                    val rowHeaderIndex = columns.indexOf(rowHeader).takeIf { it >= 0 } ?: 0

                    //show a dialog to choose a prefix trait to be displayed
                    AlertDialog.Builder(this, R.style.AppAlertDialog)
                        .setTitle(R.string.dialog_data_grid_header_picker_title)
                        .setSingleChoiceItems(columns, rowHeaderIndex) { dialog, which ->

                            // Update the preference to the determined row header
                            preferences.edit {
                                putString(
                                    GeneralKeys.DATAGRID_PREFIX_TRAIT,
                                    columns[which]
                                )
                            }

                            initialize()

                            dialog.dismiss()

                        }.create().show()
                }
            }
        }

        return super.onOptionsItemSelected(item)
    }

    /**
     * Uses the getExportTableData query to create a spreadsheet of values.
     * Columns returned are plot_id followed by all traits.
     */
    private fun loadGridData(plotId: Int? = null,
                             trait: Int? = null) {

        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)

        val showLabel = preferences.getString(PreferenceKeys.LABELVAL_CUSTOMIZE, "value") == "value"

        val uniqueHeader = preferences.getString(GeneralKeys.UNIQUE_NAME, "") ?: ""

        val rowHeader = getCurrentRowHeader()
        val rowHeaderIndex = database.getAllObservationUnitAttributeNames(studyId)
            .indexOf(rowHeader).takeIf { it >= 0 } ?: 0

        if (rowHeader.isNotBlank()) {

            //background processing
            scope.launch {

                //query database for visible traits
                mTraits.clear()

                val traitObjects = database.allTraitObjects
                traitObjects.forEach {
                    if (it.visible) {
                        mTraits.add(it)
                    }
                }

                val traits = database.allTraitObjects

                //expensive database call, only asks for the unique name plot attr and all visible traits
                val cursor = database.getExportTableData(studyId, mTraits)

                if (cursor.moveToFirst()) {

                    Log.d("DataGridActivity", "Query executed. Row count: ${cursor.count}")

                    mRowHeaders = arrayListOf()

                    mPlotIds = arrayListOf()

                    val dataMap = arrayListOf<List<CellData>>()

                    try {

                        do { //iterate over cursor results and populate lists of plot ids and related trait values

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

                            //unique name column is always the first column
                            val uniqueIndex = columns.indexOf(uniqueHeader)

                            if (uniqueIndex > -1) { //if it doesn't exist skip this row

                                val id = rowData[uniqueIndex] ?: ""

                                val header = if (rowHeaderIndex > -1) rowData[rowHeaderIndex] ?: "" else ""

                                val dataList = arrayListOf<CellData>()

                                mRowHeaders.add(header) //add unique name row header

                                mPlotIds.add(id)

                                mTraits.forEachIndexed { _, variable ->

                                    val index = columns.indexOf(DataHelper.replaceIdentifiers(variable.name))

                                    if (index > -1) {

                                        val value = rowData[index] ?: ""

                                        val t = traits.find { it.format in setOf("categorical", "qualitative") }

                                        val repeatedValues =
                                            database.getRepeatedValues(studyId.toString(), id, variable.id)

                                        var cellValue = value

                                        if (t != null) {

                                            try {

                                                cellValue = CategoryJsonUtil
                                                    .flattenMultiCategoryValue(
                                                        decode(value),
                                                        showLabel
                                                    )

                                            } catch (e: Exception) {

                                                e.printStackTrace()

                                            }
                                        }

                                        //check repeated values and replace cellvalue with an ellipses

                                        if (repeatedValues.size > 1) {

                                            //data list is a trait row in the data grid
                                            dataList.add(CellData("...", id))

                                        } else {

                                            dataList.add(CellData(cellValue, id))

                                        }
                                    }
                                }

                                dataMap.add(dataList)
                            }

                        } while (cursor.moveToNext())

                    } catch (e: java.lang.IllegalStateException) {

                        withContext(Dispatchers.Main) {
                            Utils.makeToast(this@OldDataGridActivity, getString(R.string.act_data_grid_cursor_failed))
                        }

                        e.printStackTrace()

                    }

                    //send trait/plot indices to highlight the cell
                    mAdapter = DataGridAdapter((trait ?: 1) - 1, (plotId ?: 1) - 1)

                    runOnUiThread {

                        mTableView.setHasFixedWidth(true)

                        mTableView.tableViewListener = this@OldDataGridActivity

                        mTableView.isShowHorizontalSeparators = false

                        mTableView.isShowVerticalSeparators = false

                        mTableView.setAdapter(mAdapter)

                        mAdapter.setAllItems(mTraits.map { HeaderData(it.name, it.name) },
                            mRowHeaders.map { HeaderData(it, it) },
                            dataMap.toList()
                        )

                        //scroll to the position of the current trait/plot id
                        if (plotId != null && trait != null) {

                            mTableView.scrollToColumnPosition(trait - 1)

                            mTableView.scrollToRowPosition(plotId - 1)

                        }
                    }

                    cursor.close() //always remember to close your cursor! :)

                    //update the ui after background processing ends
                    runOnUiThread {

                        dataGridGroup.visibility = View.VISIBLE

                        progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    /**
     * Determines the current row header.
     * @return The row header as a string.
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

    override fun onCellClicked(cellView: RecyclerView.ViewHolder, column: Int, row: Int) {

        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()

        //populate plotId clicked from parameters and global store
        val plotId = mPlotIds[row]

        val trait = mTraits[column]

        val repeatedValues = database.getRepeatedValues(studyId, plotId, trait.id)

        if (repeatedValues.size <= 1) {

            navigateFromValueClicked(plotId, column)

        } else {

            //show alert dialog with repeated values
            showRepeatedValuesNavigatorDialog(repeatedValues)
        }
    }

    private fun navigateFromValueClicked(plotId: String, traitIndex: Int, rep: Int? = 1) {

        //this is the onlick handler which displays a quick message and sets the intent result / finishes
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

        val labelValPref: String =
            preferences.getString(PreferenceKeys.LABELVAL_CUSTOMIZE, "value") ?: "value"
        val scale = decode(
            value
        )
        return if (scale.isNotEmpty()) {
            if (labelValPref == "value") {
                scale[0].value
            } else scale[0].label
        } else ""
    }

    private fun showRepeatedValuesNavigatorDialog(repeatedValues: Array<ObservationModel>) {

        for (m in repeatedValues) {
            if (m.observation_variable_field_book_format in setOf("categorical", "qualitative")) {
                if (m.value.isNotEmpty()) {
                    m.value = decodeValue(m.value)
                }
            }
        }

        val choices = repeatedValues.map { it.value }.filter { it.isNotBlank() }.toTypedArray()

        //show a dialog to choose which value to navigate to
        AlertDialog.Builder(this, R.style.AppAlertDialog)
            .setTitle(R.string.dialog_data_grid_repeated_measures_title)
            .setSingleChoiceItems(choices, 0) { dialog, which ->

                val value = repeatedValues[which]

                val plotId = value.observation_unit_id

                val traitIndex =
                    mTraits.indexOfFirst { it.id == value.observation_variable_db_id.toString() }

                navigateFromValueClicked(plotId, traitIndex, which + 1)

                dialog.dismiss()

            }.create().show()
    }

    //region unimplemented click events
    override fun onCellDoubleClicked(cellView: RecyclerView.ViewHolder, column: Int, row: Int) {}
    override fun onCellLongPressed(cellView: RecyclerView.ViewHolder, column: Int, row: Int) {}
    override fun onColumnHeaderClicked(columnHeaderView: RecyclerView.ViewHolder, column: Int) {}
    override fun onColumnHeaderDoubleClicked(
        columnHeaderView: RecyclerView.ViewHolder,
        column: Int
    ) {}
    override fun onColumnHeaderLongPressed(columnHeaderView: RecyclerView.ViewHolder, column: Int) {}
    override fun onRowHeaderClicked(rowHeaderView: RecyclerView.ViewHolder, row: Int) {}
    override fun onRowHeaderDoubleClicked(rowHeaderView: RecyclerView.ViewHolder, row: Int) {}
    override fun onRowHeaderLongPressed(rowHeaderView: RecyclerView.ViewHolder, row: Int) {}
    //endregion
}