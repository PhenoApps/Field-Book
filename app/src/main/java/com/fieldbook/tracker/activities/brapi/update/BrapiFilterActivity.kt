package com.fieldbook.tracker.activities.brapi.update

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrAPIServiceV1
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager
import com.fieldbook.tracker.views.SearchBar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.swagger.client.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brapi.client.v2.model.queryParams.core.BrAPIQueryParams
import org.brapi.v2.model.BrAPIPagination
import org.brapi.v2.model.BrAPIResponse
import org.brapi.v2.model.BrAPIResponseResult
import org.brapi.v2.model.core.BrAPIProgram
import org.brapi.v2.model.core.BrAPISeason
import org.brapi.v2.model.core.BrAPIStudy
import org.brapi.v2.model.core.BrAPITrial
import java.io.File

/**
 * Type parameters
 * @param T: The BrAPI model
 * @param U: The BrAPI query parameter type
 * @param V: The BrAPI response type
 */
abstract class BrapiFilterActivity<T, U : BrAPIQueryParams, V> : ThemedActivity(),
    CoroutineScope by MainScope() {

    companion object {

        const val PREFIX = "com.fieldbook.tracker.activities.brapi.importer.filters"

        fun getIntent(activity: Activity): Intent {
            return Intent(activity, BrapiFilterActivity::class.java)
        }
    }

    /**
     * Lazy-instantiate the brapi service, but cancel the activity if its v1
     */
    protected val brapiService by lazy {
        BrAPIServiceFactory.getBrAPIService(this).also { service ->
            if (service is BrAPIServiceV1) {
                launch(Dispatchers.Main) {
                    Toast.makeText(
                        this@BrapiFilterActivity,
                        "BrAPI V1 is not compatible.",
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        }
    }

    protected lateinit var fetchDescriptionTv: TextView
    protected lateinit var paginationManager: BrapiPaginationManager
    protected lateinit var recyclerView: RecyclerView

    protected var cache = mutableListOf<CheckboxListAdapter.Model>()

    protected lateinit var applyTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchEditText: AutoCompleteTextView

    protected var isQuerying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_brapi_importer)

        setupToolbar()

        initUi()

//        //check if queried data exists, if not, query
//        externalCacheDir?.let {
//            File(it, "$filterName.json").delete()
//        }

        restoreModels()
    }

    protected val intentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->

            restoreModels()

        }

    protected fun restoreModels() {

        progressBar.visibility = View.VISIBLE

        launch(Dispatchers.IO) {
            val storedModels = getStoredModels()
            if (storedModels.isEmpty()) {
                performQueryWithProgress()
            } else {
                withContext(Dispatchers.Main) {
                    cacheAndSubmitItems(storedModels)
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_filter_brapi, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.action_check_all -> {
                if (cache.isNotEmpty()) {
                    val allChecked = cache.all { it.checked }
                    cache.forEach { it.checked = !allChecked }
                    submitAdapterItems(cache)
                    (recyclerView.adapter)?.notifyItemRangeChanged(0, cache.size)
                }
                return true
            }

            R.id.action_reset_cache -> {
                resetStorageCache()
            }

            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun resetStorageCache() {

        externalCacheDir?.let { cacheDir ->
            val file = File(cacheDir, jsonFileName())
            if (file.exists()) {
                file.delete()
                cache.clear()
                submitAdapterItems(cache)
                performQueryWithProgress()
            }
        }
    }

    private fun jsonFileName() = "$filterName.json"

    protected fun getStoredModels(): List<T> {

        externalCacheDir?.let { cacheDir ->
            val file = File(cacheDir, jsonFileName())
            if (file.exists()) {
                val json = file.readText()
                return Gson().fromJson(json, getTypeToken())
            }
        }
        return listOf()
    }

    private fun saveCacheToFile(models: List<T>) {

        externalCacheDir?.let { cacheDir ->
            val file = File(cacheDir, jsonFileName())
            val json = Gson().toJson(models, getTypeToken())
            file.writeText(json)
        }
    }

    private fun setupSearch(models: List<CheckboxListAdapter.Model>) {

        val searchBar = findViewById<SearchBar>(R.id.brapi_importer_sb)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            models.map { it.label }
        )

        searchEditText = searchBar.editText

        searchEditText.threshold = 1

        searchEditText.setAdapter(adapter)

        searchEditText.onItemClickListener =
            AdapterView.OnItemClickListener { parent, _, position, _ ->
                val selected = parent?.getItemAtPosition(position).toString()
                searchEditText.setText(selected)
            }

        //TODO typing will remove progress bar when queryingChato
        searchEditText.addTextChangedListener { text ->

            searchJob?.cancel()

            searchJob = launch(Dispatchers.IO) {

                val searchModels = filterBySearchText(cache)

                withContext(Dispatchers.Main) {

                    submitAdapterItems(searchModels)
                }
            }
        }
    }

    private var searchJob: Job? = null

    private fun filterBySearchText(models: List<CheckboxListAdapter.Model>): List<CheckboxListAdapter.Model> {

        val searchText = searchEditText.text.toString()

        return if (searchText.isNotBlank()) models.filter { model ->
            model.id.contains(searchText, ignoreCase = true) ||
                    model.label.contains(searchText, ignoreCase = true) ||
                    model.subLabel.contains(searchText, ignoreCase = true)
        } else models
    }

    private fun setupToolbar() {

        setSupportActionBar(findViewById(R.id.act_brapi_importer_tb))

        supportActionBar?.title =
            getString(R.string.act_brapi_filter_by_title, getString(titleResId))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

    }

    override fun onResume() {
        super.onResume()
        if (::paginationManager.isInitialized) paginationManager.reset()
    }

    private fun initUi() {

        paginationManager = BrapiPaginationManager(this)

        fetchDescriptionTv = findViewById(R.id.brapi_importer_fetch_data_tv)
        recyclerView = findViewById(R.id.brapi_importer_rv)
        applyTextView = findViewById(R.id.act_brapi_import_tv)
        progressBar = findViewById(R.id.brapi_importer_pb)

        applyTextView.text = getString(R.string.act_brapi_filter_apply)

        setupRecyclerView()
    }

    private fun setupRecyclerView() {

        recyclerView.adapter = CheckboxListAdapter { checked, position ->
            cache[position].checked = checked
            submitAdapterItems(cache)
            applyTextView.visibility = if (showNextButton()) View.VISIBLE else View.GONE
        }
    }

    private fun List<CheckboxListAdapter.Model>.copy() =
        listOf(*this.toTypedArray())

    private fun setupApplyFilterView() {

        applyTextView.visibility = showNextButton().let { show ->
            if (show) View.VISIBLE else View.GONE
        }

        applyTextView.setOnClickListener {
            if (this is BrapiStudyFilterActivity) {

                //todo limit one selection
                val studyToImport = cache.first { it.checked }

                intentLauncher.launch(BrapiStudyImportActivity.getIntent(this).also { intent ->
                    intent.putExtra(BrapiStudyImportActivity.EXTRA_STUDY_IDS, studyToImport.id)
                    intent.putExtra(BrapiStudyImportActivity.EXTRA_STUDY_NAME, studyToImport.label)
                    intent.putExtra(BrapiStudyImportActivity.EXTRA_STUDY_LOCATION, studyToImport.subLabel)
                })
            } else {
                saveFilter()
            }

            finish()
        }
    }

    private fun submitAdapterItems(models: List<CheckboxListAdapter.Model>) {

        launch(Dispatchers.Main) {

            setupSearch(models)

            toggleProgressBar(View.GONE)

            recyclerView.visibility = View.VISIBLE

            setupApplyFilterView()

            (recyclerView.adapter as CheckboxListAdapter).submitList(filterBySearchText(models).copy())

        }
    }

    /**
     * Save checked items to preferences
     **/
    private fun saveFilter() {

        //get ids and names of selected programs from the adapter
        val selected = cache.filter { p -> p.checked }

        val gsonString = Gson().toJson(selected)

        prefs.edit().putString(
            filterName,
            gsonString
        ).apply()
    }

    private fun getIds(filterName: String) =
        BrapiFilterTypeAdapter.toModelList(prefs, filterName).map { it.id }

    protected fun cacheAndSubmitItems(models: List<T>) {

        val uiModels = models.filterByPreferences().mapToUiModel().map {
            it.checked = it.id in getIds(filterName)
            it
        }

        cache.clear()

        cache.addAll(uiModels)

        saveCacheToFile(models)

        submitAdapterItems(cache)

    }

    protected fun loadFilter() = BrapiFilterTypeAdapter.toModelList(prefs, filterName)

    protected fun toggleProgressBar(visibility: Int) {
        launch(Dispatchers.Main) {
            fetchDescriptionTv.visibility = visibility
            progressBar.visibility = visibility
        }
    }

    protected fun performQueryWithProgress() {

        launch(Dispatchers.Main) {

            toggleProgressBar(View.VISIBLE)

            withContext(Dispatchers.IO) {

                queryAll().collect { models ->

                    cacheAndSubmitItems(models.second)
                }
            }

            toggleProgressBar(View.INVISIBLE)

        }
    }

    protected fun showErrorCode(failCode: Int): Void? {

        val errorString = getString(R.string.brapi_filter_error, filterName)

        launch(Dispatchers.Main) {
            Toast.makeText(
                this@BrapiFilterActivity,
                "$errorString $failCode",
                Toast.LENGTH_SHORT
            ).show()
        }
        return null
    }

    /**
     * Filter name to be used as preferences or intent extras key
     */
    abstract val filterName: String

    /**
     * Title to be displayed at top of screen
     */
    abstract val titleResId: Int

    /**
     * Make the necessary REST calls to the respective brapi endpoints
     */
    open fun List<T?>.mapToUiModel(): List<CheckboxListAdapter.Model> = listOf()

    /**
     * implement function to display 'next' bottom toolbar
     */
    open fun showNextButton(): Boolean = true

    open fun getTypeToken() =
        TypeToken.getParameterized(List::class.java, BrAPIStudy::class.java).type

    open fun List<T>.filterByPreferences(): List<T> = this

    abstract fun getQueryParams(): U

    protected fun BrAPIResponse<*>?.validateResponse(onValidated: (BrAPIPagination, BrAPIResponseResult<*>) -> Unit): Void? {

        if (this != null) {

            metadata?.pagination?.let { pagination ->

                if (result != null && result is BrAPIResponseResult<*>) {

                    val result = result as? BrAPIResponseResult<*>

                    if (result?.data != null) {

                        onValidated(pagination, result)

                    }
                }
            }
        }

        return null
    }

    private suspend fun queryAll(): Flow<Pair<BrAPIPagination, List<T>>> = callbackFlow {

        try {

            //first query one page to get total count
            queryByPage(getQueryParams().also {
                it.page(0)
            }).collect { data ->

                if (data != null) {

                    //query all pages
                    val totalPages = data.first.totalPages
                    val totalCount = data.first.totalCount

                    //if we already received all data, send it back, otherwise query for all data
                    if (data.second.size == totalCount) {
                        trySend(data.first to data.second.mapNotNull { it })
                        //preprocessQueryResults(data.second.associateBy { it.trialDbId })
                    } else {
                        Log.d(TAG, "Expecting $totalCount")

                        queryByPage(getQueryParams().also {
                            //it.active("true")
                            it.pageSize(totalCount)
                        }).collect { allPages ->
                            if (allPages != null) {
                                trySend(data.first to allPages.second.mapNotNull { it })
                            }
                        }
                    }
                }
            }

            //TODO after try? test with crash
            awaitClose()

        } catch (e: ApiException) {
            e.printStackTrace()
        }
    }

    abstract suspend fun queryByPage(params: U): Flow<Pair<BrAPIPagination, List<T>>?>

}
