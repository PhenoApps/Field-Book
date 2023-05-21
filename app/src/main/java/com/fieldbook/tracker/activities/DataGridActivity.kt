package com.fieldbook.tracker.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.Group
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.evrencoskun.tableview.TableView
import com.evrencoskun.tableview.listener.ITableViewListener
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.DataGridAdapter
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.databinding.ActivityDataGridBinding
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.CategoryJsonUtil.Companion.decode
import com.fieldbook.tracker.utilities.Utils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

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
class DataGridActivity : ThemedActivity(), CoroutineScope by MainScope(), ITableViewListener {

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
    private lateinit var mTraits: Array<String>

    @Inject
    lateinit var database: DataHelper

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        //this activity uses databinding to inflate the content layout
        //this creates a 'binding' variable that has all the views as fields, an alternative to findViewById
        val binding = DataBindingUtil.setContentView<ActivityDataGridBinding>(this, R.layout.activity_data_grid)

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
    private fun initialize(prefixTrait: String? = null,
                           plotId: Int? = null,
                           trait: Int? = null) {

        //if something goes wrong finish the activity
        try {

            loadGridData(prefixTrait, plotId, trait)

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

                //get all available obs. property columns
                val prefixTraits = database.rangeColumns

                if (prefixTraits.isNotEmpty()) {

                    //show a dialog to choose a prefix trait to be displayed
                    AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_data_grid_header_picker_title)
                        .setSingleChoiceItems(prefixTraits, 0) { dialog, which ->

                            initialize(prefixTraits[which])

                            dialog.dismiss()

                        }.create().show()
                }
            }
        }

        return super.onOptionsItemSelected(item)
    }

    /**
     * Uses the convertDatabaseToTable query to create a spreadsheet of values.
     * Columns returned are plot_id followed by all traits.
     */
    private fun loadGridData(prefixTrait: String? = null,
                             plotId: Int? = null,
                             trait: Int? = null) {

        val ep = getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, MODE_PRIVATE)

        val studyId = ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()

        val showLabel = ep.getString(GeneralKeys.LABELVAL_CUSTOMIZE, "value") == "value"

        val uniqueHeader = ep.getString(GeneralKeys.UNIQUE_NAME, "") ?: ""

        //if row header was not chosen, then use the preference unique name
        var rowHeader = prefixTrait ?: ep.getString(GeneralKeys.DATAGRID_PREFIX_TRAIT, uniqueHeader) ?: ""

        if (rowHeader !in database.rangeColumnNames) {
            rowHeader = uniqueHeader
        }

        //if rowHeader was updated, update the preference
        ep.edit().putString(GeneralKeys.DATAGRID_PREFIX_TRAIT, rowHeader).apply()

        if (rowHeader.isNotBlank()) {

            //background processing
            scope.launch {

                //query database for visible traits
                mTraits = database.visibleTrait

                val traits = database.allTraitObjects;

                //expensive database call, only asks for the unique name plot attr and all visible traits
                val cursor = database.convertDatabaseToTable(arrayOf(uniqueHeader, rowHeader), mTraits)

                if (cursor.moveToFirst()) {

                    mRowHeaders = arrayListOf()

                    mPlotIds = arrayListOf()

                    val dataMap = arrayListOf<List<CellData>>()

                    try {

                        do { //iterate over cursor results and populate lists of plot ids and related trait values

                            val rowHeaderIndex = cursor.getColumnIndex(rowHeader)

                            //unique name column is always the first column
                            val uniqueIndex = cursor.getColumnIndex(cursor.getColumnName(0))

                            if (uniqueIndex > -1) { //if it doesn't exist skip this row

                                val id = cursor.getString(uniqueIndex)

                                val header = cursor.getString(rowHeaderIndex)

                                val dataList = arrayListOf<CellData>()

                                mRowHeaders.add(header) //add unique name row header

                                mPlotIds.add(id)

                                mTraits.forEachIndexed { _, variable ->

                                    val index = cursor.getColumnIndex(variable)

                                    if (index > -1) {

                                        val value = cursor.getString(index) ?: ""

                                        val t = traits.find { it.format in setOf("categorical", "multicat", "qualitative") }

                                        val repeatedValues = database.getRepeatedValues(studyId, id, variable)
                                        if (repeatedValues.size > 1) {
                                            println("$studyId $id $variable has repeated values...!")
                                        }

                                        var cellValue = value

                                        if (t != null) {

                                            try {

                                                cellValue = CategoryJsonUtil
                                                    .flattenMultiCategoryValue(CategoryJsonUtil.decode(value), showLabel)

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

                        Utils.makeToast(this@DataGridActivity, getString(R.string.act_data_grid_cursor_failed))

                        e.printStackTrace()

                    }

                    //send trait/plot indices to highlight the cell
                    mAdapter = DataGridAdapter((trait ?: 1) - 1, (plotId ?: 1) - 1)

                    runOnUiThread {

                        mTableView.setHasFixedWidth(true)

                        mTableView.tableViewListener = this@DataGridActivity

                        mTableView.isShowHorizontalSeparators = false

                        mTableView.isShowVerticalSeparators = false

                        mTableView.setAdapter(mAdapter)

                        mAdapter.setAllItems(mTraits.map { HeaderData(it, it) },
                            mRowHeaders.map { HeaderData(it, it) },
                            dataMap.toList())

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

    override fun onCellClicked(cellView: RecyclerView.ViewHolder, column: Int, row: Int) {

        val ep = getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, MODE_PRIVATE)

        val studyId = ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()

        //populate plotId clicked from parameters and global store
        val plotId = mPlotIds[row]

        val trait = mTraits[column]

        val repeatedValues = database.getRepeatedValues(studyId, plotId, trait)

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

        val ep = getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, MODE_PRIVATE)

        val labelValPref: String = ep.getString(GeneralKeys.LABELVAL_CUSTOMIZE, "value") ?: "value"
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
            if (m.observation_variable_field_book_format in setOf("categorical", "multicat", "qualitative")) {
                if (m.value.isNotEmpty()) {
                    m.value = decodeValue(m.value)
                }
            }
        }

        val choices = repeatedValues.map { it.value }.filter { it.isNotBlank() }.toTypedArray()

        //show a dialog to choose which value to navigate to
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_data_grid_repeated_measures_title)
            .setSingleChoiceItems(choices, 0) { dialog, which ->

                val value = repeatedValues[which]

                val plotId = value.observation_unit_id

                val traitIndex = mTraits.indexOf(value.observation_variable_name)

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