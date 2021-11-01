package com.fieldbook.tracker.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.evrencoskun.tableview.TableView
import com.evrencoskun.tableview.listener.ITableViewListener
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.DataGridAdapter
import com.fieldbook.tracker.databinding.ActivityDataGridBinding
import com.fieldbook.tracker.utilities.Utils
import kotlinx.coroutines.*
import java.util.*

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
class DataGridActivity : AppCompatActivity(), CoroutineScope by MainScope(), ITableViewListener {

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
    private lateinit var mPlotIds: ArrayList<String>
    private lateinit var mTraits: Array<String>

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

        //if something goes wrong finish the activity
        try {

            loadGridData(intent.extras?.getInt("plot_id"),
                intent.extras?.getInt("trait"))

        } catch (e: Exception) {

            e.printStackTrace()

            setResult(RESULT_CANCELED)

            finish()
        }

    }

    //finish activity when back button is pressed
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Uses the convertDatabaseToTable query to create a spreadsheet of values.
     * Columns returned are plot_id followed by all traits.
     */
    private fun loadGridData(plotId: Int? = null, trait: Int? = null) {

        val ep = getSharedPreferences("Settings", MODE_PRIVATE)

        val uniqueName = ep.getString("ImportUniqueName", "") ?: ""

        if (uniqueName.isNotBlank()) {

            //background processing
            scope.launch {

                //query database for visible traits
                mTraits = ConfigActivity.dt.visibleTrait

                //expensive database call, only asks for the unique name plot attr and all visible traits
                val cursor = ConfigActivity.dt.convertDatabaseToTable(arrayOf(uniqueName), mTraits)

                if (cursor.moveToFirst()) {

                    mPlotIds = arrayListOf()

                    val dataMap = arrayListOf<List<CellData>>()

                    do { //iterate over cursor results and populate lists of plot ids and related trait values

                        //unique name column is always the first column
                        val uniqueIndex = cursor.getColumnIndex(cursor.getColumnName(0))

                        if (uniqueIndex > -1) { //if it doesn't exist skip this row

                            val plotId = cursor.getString(uniqueIndex)

                            val dataList = arrayListOf<CellData>()

                            mPlotIds.add(plotId) //add unique name row header

                            mTraits.forEachIndexed { _, variable ->

                                val index = cursor.getColumnIndex(variable)

                                if (index > -1) {

                                    val value = cursor.getString(index) ?: ""

                                    //data list is a trait row in the data grid
                                    dataList.add(CellData(value, plotId))
                                }
                            }

                            dataMap.add(dataList)
                        }

                    } while (cursor.moveToNext())

                    //send trait/plot indices to highlight the cell
                    mAdapter = DataGridAdapter((trait ?: 1) - 1, (plotId ?: 1) - 1)

                    runOnUiThread {

                        mTableView.setHasFixedWidth(true)

                        mTableView.tableViewListener = this@DataGridActivity

                        mTableView.isShowHorizontalSeparators = false

                        mTableView.isShowVerticalSeparators = false

                        mTableView.setAdapter(mAdapter)

                        mAdapter.setAllItems(mTraits.map { HeaderData(it, it) },
                            mPlotIds.map { HeaderData(it, it) },
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

        //populate plotId clicked from parameters and global store
        val plotId = mPlotIds[row]

        //this is the onlick handler which displays a quick message and sets the intent result / finishes
        Utils.makeToast(applicationContext, plotId)

        val returnIntent = Intent()

        returnIntent.putExtra("result", plotId)

        //the trait index is used to move collect activity to the clicked trait
        returnIntent.putExtra("trait", column)

        setResult(RESULT_OK, returnIntent)

        finish()

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