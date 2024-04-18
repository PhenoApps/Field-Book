package com.fieldbook.tracker.activities.brapi.hackathon

import android.database.sqlite.SQLiteException
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.BuildConfig
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.activities.brapi.hackathon.cropontology.FtsDatabase
import com.fieldbook.tracker.activities.brapi.hackathon.cropontology.models.RankedStudy
import com.fieldbook.tracker.activities.brapi.hackathon.cropontology.viewmodels.BrapiStudyViewModel
import com.fieldbook.tracker.adapters.SimpleListAdapter
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.preferences.GeneralKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.nio.ByteBuffer
import java.nio.ByteOrder

abstract class AbstractImportBrapiActivity: ThemedActivity(), CoroutineScope by MainScope() {

    //An enum for tracking the sorting state of the UI
    //Sorting can be ascending or descending for only one of:
    //ID, name, and location
    enum class SortState {
        ID_DESC, ID_ASC,
        NAME_DESC, NAME_ASC,
        LOCATION_DESC, LOCATION_ASC
    }

    //search ui
    protected lateinit var searchEt: EditText
    protected lateinit var searchResultColumnsTv: TextView

    //sort ui
    protected lateinit var sortByFieldIdTv: TextView
    protected lateinit var sortByLocationTv: TextView
    protected lateinit var sortByFieldNameTv: TextView

    //endpoint url bar
    protected lateinit var endpointUrlTv: TextView

    protected lateinit var toolbarRecyclerView: RecyclerView
    protected lateinit var checkableItemRecyclerView: RecyclerView
    protected lateinit var resultCountTv: TextView
    protected lateinit var bottomToolbar: LinearLayoutCompat

    protected val brapiService by lazy {
        BrAPIServiceV2(this)
    }

    private val brapiEndPointUrl by lazy {
        prefs.getString(GeneralKeys.BRAPI_BASE_URL, "")
    }

    protected val ftsDatabase by lazy {
        FtsDatabase.getInstance(this)
    }

    private val mViewModel by lazy {
        BrapiStudyViewModel(ftsDatabase.studyFtsDao())
    }

//    private val filterAdapter by lazy {
//
//        FilterListAdapter(context)
//
//    }

    protected abstract val brapiObjectListAdapter: RecyclerView.Adapter<*>

    private var sortState: SortState = SortState.ID_ASC

    protected var lastPage = 0
    protected var pages = 50
    protected var pagesIncrement = 50

    //https://stackoverflow.com/questions/63654824/how-to-fix-the-error-wrong-number-of-arguments-to-function-rank-on-sqlite-an
    private fun rank(matchInfo: IntArray): Double {
        val numPhrases = matchInfo[0]
        val numColumns = matchInfo[1]

        var score = 0.0
        for (phrase in 0 until numPhrases) {
            val offset = 2 + phrase * numColumns * 3
            for (column in 0 until numColumns) {
                val numHitsInRow = matchInfo[offset + 3 * column]
                val numHitsInAllRows = matchInfo[offset + 3 * column + 1]
                if (numHitsInAllRows > 0) {
                    score += numHitsInRow.toDouble() / numHitsInAllRows.toDouble()
                }
            }
        }

        return score
    }

    fun ByteArray.skip(skipSize: Int): IntArray {
        val cleanedArr = IntArray(this.size / skipSize)
        var pointer = 0
        for (i in this.indices step skipSize) {
            cleanedArr[pointer] = this[i].toInt()
            pointer++
        }

        return cleanedArr
    }

    protected fun startSearch(query: String) {

        mViewModel.searchResult(query).observe(this) {

            val obs: List<RankedStudy> = it.sortedByDescending { result -> rank(result.matchInfo.skip(4)) }

            (checkableItemRecyclerView.adapter as SimpleListAdapter).submitList(
                obs.mapIndexed { index, x ->

                    val intBuffer = ByteBuffer.wrap(x.matchInfo)
                        .order(ByteOrder.nativeOrder())
                        .asIntBuffer()
                    val intArray = IntArray(intBuffer.remaining())
                    intBuffer.get(intArray)

                    //println(intArray.map { it })
                    //https://www.sqlite.org/fts3.html#matchinfo
                    //last of the array is splits into 3*cols*phrases chunks
                    //matchinfo is called with 'pcx' which means
                    //p = phrases is the first returned integer shows #matched phrases
                    //c = columns is the second returned integer shows defined columns in fts
                    val phrases = intArray[0]
                    val columns = intArray[1]

                    println("Matchable phrases: $phrases")
                    println("Columns: $columns")

                    /*

     val name: String?,
    val studyDbId: String?,
    val description: String?,
    val institution: String?,
    val scientist: String?,
    val crop: String?,
                     */
                    //user defined columns
                    //array of readable column names to show user which ones matched their query
//                    val columnNames = arrayOf(
//                        "Study DB ID",
//                        "Name",
//                        "Description",
//                        "Institution",
//                        "Scientist",
//                        "Language",
//                        "Crop")

                    //add ui with search result count
                    val columnNames = arrayOf(
                        "Study Name",
                        "Study DB ID",
                        "Active",
                        "Cultural Practices",
                        "Documentation URL",
                        "License",
                        "Location DB ID",
                        "Location Name",
                        "Obs Unit Description",
                        "Seasons",
                        "Study Code",
                        "Study PUI",
                        "Study Type",
                        "Trial DB ID",
                        "Trial Name",
                        "Description",
                        "Common Crop Name"
                    )
                    val matchedColumns = arrayListOf<String>() //save matched columns to display

                    var i = 2 //start index past p and c
                    for (p in 0 until phrases) { //loop through each phrase

                        for (c in 0 until columns) { //loop through all columns for each phrase

                            val name = columnNames[c]
                            //matchinfo 'x' prints three values per phrase/column combination
                            val hitsThisRow = intArray[i]
                            val hitsAllRows = intArray[i + 1]
                            val docsWithHits = intArray[i + 2]

                            if (hitsThisRow > 0) {

                                matchedColumns.add(name)
                                //println("$name matched query and found $docsWithHits documents.")
                            }

                            i += 3
                        }
                    }

                    searchResultColumnsTv.text = "Matched columns: ${matchedColumns.distinct().joinToString(", ")}"

                    x.variable //end to map that returns the variable model
                }
            )

            checkableItemRecyclerView.adapter?.notifyDataSetChanged() //.notifyItemRangeInserted(lastPage, pages)

            checkableItemRecyclerView.scrollToPosition(0)
            //mRecycler.scheduleLayoutAnimation()
        }
    }

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(
            s: CharSequence?,
            start: Int,
            count: Int,
            after: Int
        ) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            try {

                val text = s?.toString() ?: ""

                lastQuery = text

                startSearch(text)

                if (text.isEmpty()) {

                    searchResultColumnsTv.text = String()

                }

            } catch (e: SQLiteException) {

                e.printStackTrace()
            }
        }

        override fun afterTextChanged(s: Editable?) {}

    }

    protected var lastQuery: String = ""

    protected fun startSearchViews() {

        searchEt.removeTextChangedListener(watcher)

        searchEt.addTextChangedListener(watcher)

        if (BuildConfig.DEBUG) {

            //searchEt.setText("*")
        }
    }

    protected fun loadBrapiData() {

        ftsDatabase.studyFtsDao().rebuild()

        fetchBrapiData()

        setupRecyclerViewScrollListener()

//        Handler(Looper.getMainLooper()).postDelayed({
//
//            setupRecyclerViewScrollListener()
//
//        }, 5000)
    }

    abstract fun fetchBrapiData()

    private fun TextView.setEndDrawable(ascending: Boolean) {

        val drawable = AppCompatResources.getDrawable(context,
            if (ascending) R.drawable.sort_ascending
            else R.drawable.sort_descending)

        setCompoundDrawablesWithIntrinsicBounds(
            null,
            null,
            drawable,
            null)
    }

    private fun updateRecyclerViewSort() {

    }

    private fun updateSortTextViewDrawables() {

        when (sortState) {

            SortState.ID_ASC -> {
                sortByFieldIdTv.setEndDrawable(true)
                sortByFieldNameTv.setCompoundDrawablesRelative(null, null, null, null)
                sortByLocationTv.setCompoundDrawablesRelative(null, null, null, null)
            }

            SortState.ID_DESC -> {
                sortByFieldIdTv.setEndDrawable(false)
                sortByFieldNameTv.setCompoundDrawablesRelative(null, null, null, null)
                sortByLocationTv.setCompoundDrawablesRelative(null, null, null, null)
            }

            SortState.NAME_ASC -> {
                sortByFieldNameTv.setEndDrawable(true)
                sortByFieldIdTv.setCompoundDrawablesRelative(null, null, null, null)
                sortByLocationTv.setCompoundDrawablesRelative(null, null, null, null)
            }

            SortState.NAME_DESC -> {
                sortByFieldNameTv.setEndDrawable(false)
                sortByFieldIdTv.setCompoundDrawablesRelative(null, null, null, null)
                sortByLocationTv.setCompoundDrawablesRelative(null, null, null, null)
            }

            SortState.LOCATION_ASC -> {
                sortByLocationTv.setEndDrawable(true)
                sortByFieldIdTv.setCompoundDrawablesRelative(null, null, null, null)
                sortByFieldNameTv.setCompoundDrawablesRelative(null, null, null, null)
            }

            SortState.LOCATION_DESC -> {
                sortByLocationTv.setEndDrawable(false)
                sortByFieldIdTv.setCompoundDrawablesRelative(null, null, null, null)
                sortByFieldNameTv.setCompoundDrawablesRelative(null, null, null, null)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.brapi_importer)

        verifyValidBrapiUrl()

        setup()
    }

    override fun onDestroy() {
        super.onDestroy()
        ftsDatabase.close()
    }

    private fun verifyValidBrapiUrl() {

        if (brapiEndPointUrl.isNullOrEmpty()) {

            Toast.makeText(this,
                R.string.brapi_must_configure_url,
                Toast.LENGTH_LONG).show()

            finish()
        }
    }

    private fun setup() {

        endpointUrlTv = findViewById(R.id.brapi_importer_endpoint_url_tv)

        toolbarRecyclerView = findViewById(R.id.toolbar_recycler_view)

        sortByFieldIdTv = findViewById(R.id.brapi_importer_id_header)
        sortByFieldNameTv = findViewById(R.id.brapi_importer_name_header)
        sortByLocationTv = findViewById(R.id.brapi_importer_location_header)

        checkableItemRecyclerView = findViewById(R.id.checkable_item_recycler_view)
        resultCountTv = findViewById(R.id.result_count_tv)
        bottomToolbar = findViewById(R.id.bottom_toolbar)

        searchEt = findViewById(R.id.brapi_importer_search_et)
        searchResultColumnsTv = findViewById(R.id.brapi_importer_search_columns_result_tv)

        setupEndpointToolbar()
        setupSearchBar()
        setupSortTextViews()

        checkableItemRecyclerView.adapter = SimpleListAdapter(this)

        loadBrapiData()
    }

    protected fun setupRecyclerViewScrollListener() {

        checkableItemRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                val totalItemCount = recyclerView.adapter?.itemCount

                if (totalItemCount != null) {
                    if (lastVisibleItemPosition == totalItemCount - 1) {

                        checkableItemRecyclerView.removeOnScrollListener(this)

                        lastPage = pages
                        pages += pagesIncrement

                        //RecyclerView has shown the last item
                        Toast.makeText(this@AbstractImportBrapiActivity, "Loading $lastPage to $pages", Toast.LENGTH_SHORT).show()

                        fetchBrapiData()

                        Handler(Looper.getMainLooper()).postDelayed({

                            setupRecyclerViewScrollListener()

                        }, 5000L)
                    }
                }
            }
        })
    }

    private fun setupEndpointToolbar() {

        endpointUrlTv.text = brapiEndPointUrl

    }

    private fun setupSortTextViews() {

        //sortByFieldIdTv.visibility = View.GONE
        //sortByLocationTv.visibility = View.GONE
        //sortByFieldNameTv.visibility = View.GONE

        sortByFieldIdTv.setOnClickListener {

            sortState = when (sortState) {
                SortState.ID_ASC -> SortState.ID_DESC
                SortState.ID_DESC -> SortState.ID_ASC
                else -> SortState.ID_ASC
            }

            updateSortTextViewDrawables()
            updateRecyclerViewSort()
        }

        sortByFieldNameTv.setOnClickListener {

            sortState = when (sortState) {
                SortState.NAME_ASC -> SortState.NAME_DESC
                SortState.NAME_DESC -> SortState.NAME_ASC
                else -> SortState.NAME_ASC
            }

            updateSortTextViewDrawables()
            updateRecyclerViewSort()
        }

        sortByLocationTv.setOnClickListener {

            sortState = when (sortState) {
                SortState.LOCATION_ASC -> SortState.LOCATION_DESC
                SortState.LOCATION_DESC -> SortState.LOCATION_ASC
                else -> SortState.LOCATION_ASC
            }

            updateSortTextViewDrawables()
            updateRecyclerViewSort()
        }
    }

    private fun setupSearchBar() {

        searchEt.visibility = View.VISIBLE
        searchResultColumnsTv.visibility = View.VISIBLE

    }
}