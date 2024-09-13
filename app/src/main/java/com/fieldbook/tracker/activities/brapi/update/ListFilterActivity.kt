package com.fieldbook.tracker.activities.brapi.update

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.views.SearchBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * TODO erase local cache when server changes, or use directory structure
 * Type parameters
 * param T the list data type that will be converted into the CheckboxAdapter Model
 */
abstract class ListFilterActivity : ThemedActivity(),
    CoroutineScope by MainScope() {

    companion object {

        fun getIntent(activity: Activity): Intent {
            return Intent(activity, ListFilterActivity::class.java)
        }
    }

    protected val searchModels: ArrayList<String> = arrayListOf()
    protected var searchJob: Job? = null

    protected lateinit var fetchDescriptionTv: TextView
    protected lateinit var recyclerView: RecyclerView

    protected var cache = mutableListOf<CheckboxListAdapter.Model>()

    protected lateinit var applyTextView: TextView
    protected lateinit var progressBar: ProgressBar
    protected lateinit var searchBar: SearchBar

    abstract fun onFinishButtonClicked()

    abstract fun setupSearch(models: List<CheckboxListAdapter.Model>)

    open fun onSearchTextComplete(searchText: String) = Unit

    /**
     * implement function to display 'next' bottom toolbar
     */
    open fun showNextButton(): Boolean = true

    /**
     * Title to be displayed at top of screen
     */
    abstract val titleResId: Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_brapi_importer)

        setupToolbar()

        initUi()

    }

    open fun List<CheckboxListAdapter.Model>.filterBySearchText(): List<CheckboxListAdapter.Model> {

        val searchText = searchBar.editText.text.toString()

        return if (searchText.isNotBlank()) filter { model ->
            model.id.contains(searchText, ignoreCase = true) ||
                    model.label.contains(searchText, ignoreCase = true) ||
                    model.subLabel.contains(searchText, ignoreCase = true)
        } else this
    }

    private fun setupToolbar() {

        setSupportActionBar(findViewById(R.id.act_brapi_importer_tb))

        supportActionBar?.title =
            getString(R.string.act_brapi_filter_by_title, getString(titleResId))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

    }

    private fun initUi() {

        fetchDescriptionTv = findViewById(R.id.brapi_importer_fetch_data_tv)
        recyclerView = findViewById(R.id.brapi_importer_rv)
        applyTextView = findViewById(R.id.act_brapi_import_tv)
        progressBar = findViewById(R.id.brapi_importer_pb)
        searchBar = findViewById(R.id.brapi_importer_sb)

        applyTextView.text = getString(R.string.act_brapi_filter_apply)

        setupRecyclerView()

    }

    protected fun setProgress(progress: Int, progressMax: Int) {
        progressBar.isIndeterminate = false
        progressBar.progress = progress
        progressBar.max = progressMax
    }

    private fun setupRecyclerView() {

        recyclerView.adapter = CheckboxListAdapter { checked, position ->
            cache[position].checked = checked
            //submitAdapterItems(cache)
            applyTextView.visibility = if (showNextButton()) View.VISIBLE else View.GONE
        }
    }

    fun List<CheckboxListAdapter.Model>.copy() =
        listOf(*this.toTypedArray())

    private fun setupApplyFilterView() {

        applyTextView.visibility = showNextButton().let { show ->
            if (show) View.VISIBLE else View.GONE
        }

        applyTextView.setOnClickListener {
            onFinishButtonClicked()
            finish()
        }
    }

    protected fun submitAdapterItems(models: List<CheckboxListAdapter.Model>) {

        launch(Dispatchers.Main) {

            setupSearch(models)

            recyclerView.visibility = View.VISIBLE

            setupApplyFilterView()

            (recyclerView.adapter as CheckboxListAdapter).submitList(models.filterBySearchText())

            recyclerView.adapter?.notifyDataSetChanged()

            fetchDescriptionTv.text =
                resources.getQuantityString(R.plurals.act_brapi_list_filter, models.size, models.size)

        }
    }

    protected fun toggleProgressBar(visibility: Int) {
        launch(Dispatchers.Main) {
            //fetchDescriptionTv.visibility = visibility
            progressBar.visibility = visibility
        }
    }
}
