package com.fieldbook.tracker.activities.brapi.update

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager
import com.fieldbook.tracker.preferences.GeneralKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

abstract class BrapiFilterActivity: ThemedActivity(), CoroutineScope by MainScope() {

    companion object {
        fun getIntent(activity: Activity): Intent {
            return Intent(activity, BrapiFilterActivity::class.java)
        }
    }

    enum class BrapiType {
        PROGRAM,
        TRIAL,
        SEASON,
        CROP
    }

    data class BrapiFilter(
        var type: BrapiType,
        var value: String
    )

    protected val brapiService by lazy {
        BrAPIServiceFactory.getBrAPIService(this)
    }

    protected var cache = mutableListOf<CheckboxListAdapter.Model>()

    protected lateinit var paginationManager: BrapiPaginationManager

    protected lateinit var serverTextView: TextView
    protected lateinit var titleFilterTextView: TextView
    protected lateinit var recyclerView: RecyclerView
    protected lateinit var nextFilterTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_brapi_filter)

        setupToolbar()

        initUi()

        setupNextFilterTextView()

        query()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(Activity.RESULT_OK, Intent().apply {
                saveFilterData()
            })
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupToolbar() {

        setSupportActionBar(findViewById(R.id.act_brapi_filter_tb))

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

    }

    private fun initUi() {

        paginationManager = BrapiPaginationManager(this)

        serverTextView = findViewById(R.id.act_brapi_filter_server_tv)
        titleFilterTextView = findViewById(R.id.act_brapi_filter_title_tv)
        recyclerView = findViewById(R.id.act_brapi_filter_rv)
        nextFilterTextView = findViewById(R.id.act_brapi_filter_next_tv)

        setupServerTextView()
        setupRecyclerView()


    }

    private fun setupServerTextView() {
        serverTextView.text = PreferenceManager.getDefaultSharedPreferences(this)
            .getString(GeneralKeys.BRAPI_DISPLAY_NAME, "");
    }

    private fun setupRecyclerView() {

        recyclerView.adapter = CheckboxListAdapter(object : CheckboxListAdapter.Listener {
            override fun onItemClicked(checked: Boolean, position: Int) {
                cache[position].checked = checked
                submitAdapterItems(cache)
            }
        })

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                val lm = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItem = lm.findLastCompletelyVisibleItemPosition()
                val totalItems = lm.itemCount
                if (lastVisibleItem == totalItems - 1 && paginationManager.page < paginationManager.totalPages - 1) {
                    Toast.makeText(this@BrapiFilterActivity, "Need to update adapter with new pages", Toast.LENGTH_SHORT).show()
                    paginationManager.setNewPage(R.id.next)
                    query()
                }
            }
        })
    }

    private fun setupNextFilterTextView() {

    }

    private fun List<CheckboxListAdapter.Model>.copy() =
        listOf(*this.toTypedArray())

    private fun submitAdapterItems(models: List<CheckboxListAdapter.Model>) {

        launch(Dispatchers.Main) {

            (recyclerView.adapter as CheckboxListAdapter).submitList(models.copy())

        }
    }

    protected fun cacheAndSubmitItems(models: List<CheckboxListAdapter.Model>) {

        cache.addAll(models)

        submitAdapterItems(cache)

    }

    abstract fun getChosenFilter(): BrapiFilter

    abstract fun loadFilter(): BrapiFilter

    abstract fun Intent.saveFilterData(): BrapiFilter

    abstract fun query()

}
