package com.fieldbook.tracker.activities

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.core.widget.NestedScrollView
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.HeaderAdapter
import com.fieldbook.tracker.databinding.ActivityDataGridBinding
import com.fieldbook.tracker.utilities.Utils
import java.util.*
import kotlin.collections.ArrayList

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
 *
 * TODO: Discuss using an async task and adding a progress bar while the data loads, currently there is a lag between clicking and moving to this activity on large databases.
 */
class DataGridActivity : AppCompatActivity(), GestureDetector.OnGestureListener {

    /***
     * Polymorphism class structure to serve different cell types to the grid.
     */
    open class BlockData
    data class HeaderData(val name: String, val code: String) : BlockData()
    data class CellData(val value: String?, val color: Int = Color.GREEN, val onClick: View.OnClickListener? = null): BlockData()
    class EmptyCell: BlockData()

    //gestures are used to synchronize scrolling between headers and table data
    private lateinit var mGesture: GestureDetectorCompat

    private lateinit var mDataAdapter: HeaderAdapter    //used to store table cells s.a 2
    private lateinit var mRowAdapter: HeaderAdapter     //used to store plotids s.a 13RNP00042
    private lateinit var mColumnAdapter: HeaderAdapter  //used to store trait s.a height

    /**
     * views that are initialized in oncreate
     */
    private lateinit var table: RecyclerView
    private lateinit var columns: RecyclerView
    private lateinit var rows: RecyclerView
    private lateinit var scrollView: NestedScrollView

    private var totalRows: Int = 0
    private var absoluteTablePosition: Int = 0

    private var rowPosition = 0
    private var tablePosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        //this activity uses databinding to inflate the content layout
        //this creates a 'binding' variable that has all the views as fields, an alternative to findViewById
        val binding = DataBindingUtil.setContentView<ActivityDataGridBinding>(this, R.layout.activity_data_grid)

        table = binding.table
        columns = binding.columns
        rows = binding.rows
        scrollView = binding.scrollView

        mGesture = GestureDetectorCompat(this, this@DataGridActivity)

        //initialize the adapters that control the data inside each recycler view
        mDataAdapter = HeaderAdapter(this)
        mRowAdapter = HeaderAdapter(this)
        mColumnAdapter = HeaderAdapter(this)

        table.adapter = mDataAdapter

        synchronizeScrollBars()

        columns.adapter = mColumnAdapter

        rows.adapter = mRowAdapter

        table.setHasFixedSize(true)
        table.itemAnimator = null
        rows.setHasFixedSize(true)
        rows.itemAnimator = null

        loadGridData()

    }

    private fun synchronizeScrollBars() {

        val scrollListeners = ArrayList<RecyclerView.OnScrollListener>()

        val nullScroller = (View.OnScrollChangeListener { _, _, _, _, _ ->  })

        fun setNestedScroller() {

            //parent nest scroll view handles static header vertical scrolling
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                scrollView.setOnScrollChangeListener { _, p1, p2, p3, p4 ->

                    rows.removeOnScrollListener(scrollListeners[2])

                    rows.scrollBy(0, p2-p4)

                    rows.addOnScrollListener(scrollListeners[2])

                }
            }
        }

        //table scroller handles horizontal scrolling of columns
        scrollListeners.add(object : RecyclerView.OnScrollListener() {

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {

                super.onScrolled(recyclerView, dx, dy)

                columns.removeOnScrollListener(scrollListeners[1])

                columns.scrollBy(dx, dy)

                columns.addOnScrollListener(scrollListeners[1])

            }
        })

        //cols scroller
        scrollListeners.add(object : RecyclerView.OnScrollListener() {

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {

                super.onScrolled(recyclerView, dx, dy)

                table.removeOnScrollListener(scrollListeners[0])

                table.scrollBy(dx, dy)

                table.addOnScrollListener(scrollListeners[0])
            }
        })

        //rows scroller
        scrollListeners.add(object : RecyclerView.OnScrollListener() {

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {

                super.onScrolled(recyclerView, dx, dy)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    scrollView.setOnScrollChangeListener(nullScroller)
                }

                scrollView.scrollBy(dx, dy)

                setNestedScroller()
            }
        })

        table.addOnScrollListener(scrollListeners[0])

        columns.addOnScrollListener(scrollListeners[1])

        rows.addOnScrollListener(scrollListeners[2])

        setNestedScroller()

    }

    /**
     * view holder data structure that helps form the cursor for collect activity
     */
    data class GridCursor(val plotId: String, val traitIndex: Int, val value: String)

    /**
     * Uses the convertDatabaseToTable query to create a spreadsheet of values.
     * Columns returned are plot_id followed by all traits.
     */
    private fun loadGridData() {

        val ep = getSharedPreferences("Settings", 0)

        val columns = arrayOf(ep.getString("ImportUniqueName", "") ?: "")

        val traits = ConfigActivity.dt.visibleTrait

        (this.columns.adapter as? HeaderAdapter)?.submitList(traits.map { HeaderData(it, it) })

        val cursor = ConfigActivity.dt.convertDatabaseToTable(columns, traits)

        cursor.moveToPosition(-1)

        val rows: Int = cursor.count

        totalRows = rows

        cursor.moveToFirst()

        val data = arrayListOf<GridCursor>()
        val plotIdData = arrayOfNulls<String>(rows)

        //data map is used to load the traits into memory so it can be transposed into the spreadsheet
        val dataMap = mutableMapOf<String, ArrayList<String>>().withDefault { arrayListOf() }

        for (i in 0 until rows) {

            val plotId = cursor.getString(cursor.getColumnIndex(cursor.getColumnName(0)))

            plotIdData[i] = plotId

            dataMap[plotId] = arrayListOf()

            traits.forEach { variable ->

                val index = cursor.getColumnIndex(variable)

                val value = cursor.getString(index) ?: ""

                dataMap[plotId]?.add(value)

            }
            cursor.moveToNext()
        }

        //transpose the data into the data array
        traits.forEachIndexed { index, _ ->

            dataMap.keys.forEach { plotId ->

                dataMap[plotId]?.get(index)?.let { data.add(GridCursor(plotId, index, it)) }
            }
        }


        (this.rows.adapter as? HeaderAdapter)?.submitList(plotIdData.mapNotNull { it }.map { HeaderData(it, it) })

        table.layoutManager = GridLayoutManager(this, rows, GridLayoutManager.HORIZONTAL, false)

        (this.table.adapter as? HeaderAdapter)?.submitList(data.map { cell -> CellData(cell.value) {

            //this is the onlick handler which displays a quick message and sets the intent result / finishes
            Utils.makeToast(applicationContext, cell.plotId)

            val returnIntent = Intent()

            returnIntent.putExtra("result", cell.plotId)

            //the trait index is used to move collect activity to the clicked trait
            returnIntent.putExtra("trait", cell.traitIndex)

            setResult(RESULT_OK, returnIntent)

            finish()

        } })

        cursor.close() //always remember to close your cursor! :)

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return super.onOptionsItemSelected(item)
    }

    override fun onShowPress(p0: MotionEvent?) {
        TODO("Not yet implemented")
    }

    override fun onSingleTapUp(p0: MotionEvent?): Boolean {
        TODO("Not yet implemented")
    }

    override fun onDown(p0: MotionEvent?): Boolean {
        TODO("Not yet implemented")
    }

    override fun onFling(p0: MotionEvent?, p1: MotionEvent?, p2: Float, p3: Float): Boolean {
        TODO("Not yet implemented")
    }

    override fun onScroll(p0: MotionEvent?, p1: MotionEvent?, p2: Float, p3: Float): Boolean {
        TODO("Not yet implemented")
    }

    override fun onLongPress(p0: MotionEvent?) {
        TODO("Not yet implemented")
    }
}