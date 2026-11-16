package com.fieldbook.tracker.activities.brapi.io.filter

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.io.BrapiCacheModel
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterCache
import com.fieldbook.tracker.activities.brapi.io.StudyListModel
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Filters the study list to the studies of the chosen server lists.
 * Only offered when the server has lists of studies, see BrapiStudyFilterActivity.fetchStudyLists.
 * Applying the filter fetches the chosen lists' items, so the study list shows what each list holds now.
 */
class BrapiStudyListsFilterActivity(override val titleResId: Int = R.string.brapi_filter_type_list) :
    BrapiSubFilterListActivity<StudyListModel>() {

    companion object {

        const val FILTER_NAME = "studyListDbIds"

        private const val TAG = "BrapiStudyListsFilter"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiStudyListsFilterActivity::class.java)
        }
    }

    override val filterName: String
        get() = FILTER_NAME

    //fetching the checked lists' items, the filter is saved once they arrive
    private var applyJob: Job? = null

    override fun BrapiCacheModel.mapToUiModel() = studyLists.orEmpty().map { list ->
        CheckboxListAdapter.Model(
            checked = false,
            id = list.listDbId,
            label = list.listName ?: list.listDbId,
            subLabel = listOfNotNull(
                list.listSize?.let { resources.getQuantityString(R.plurals.brapi_filter_list_study_count, it, it) },
                truncatedLabel(list),
                list.listOwnerName
            ).joinToString(" · ")
        )
    }

    /**
     * "N of M loaded" for a list whose items were fetched but cut short by the server, so its filter hides some studies
     */
    private fun truncatedLabel(list: StudyListModel): String? {
        val loaded = list.studyDbIds?.size ?: return null
        val total = list.totalItems ?: return null
        return getString(R.string.brapi_filter_list_truncated, loaded, total)
    }

    override fun onFinishButtonClicked() = applyFilter()

    /**
     * Fetches the items of the checked lists, then saves the filter and closes.
     * If a list can't be fetched the screen stays open, so the user can try again or go back.
     */
    private fun applyFilter() {

        if (applyJob?.isActive == true) return

        val listDbIds = (recyclerView.adapter as CheckboxListAdapter).selected.map { it.id }

        progressBar.visibility = View.VISIBLE
        importTextView.isEnabled = false

        applyJob = launch {

            val items = withContext(Dispatchers.IO) { fetchItems(listDbIds) }

            if (items == null) {

                progressBar.visibility = View.GONE
                importTextView.isEnabled = true

                Toast.makeText(
                    this@BrapiStudyListsFilterActivity,
                    R.string.act_brapi_study_lists_filter_failed,
                    Toast.LENGTH_LONG
                ).show()

                return@launch
            }

            val lists = withContext(Dispatchers.IO) {

                val context = this@BrapiStudyListsFilterActivity

                val stored = BrapiFilterCache.getStoredModels(context).studyLists.orEmpty()

                stored.map { list ->
                    items[list.listDbId]?.let { list.copy(studyDbIds = it.first, totalItems = it.second) } ?: list
                }.also { BrapiFilterCache.saveStudyLists(context, it) }
            }

            //the filter hides the studies a cut short list didn't send, say so
            val truncated = lists.filter { it.listDbId in items && truncatedLabel(it) != null }

            if (truncated.isNotEmpty()) {
                Toast.makeText(
                    this@BrapiStudyListsFilterActivity,
                    truncated.joinToString("\n") { "${it.listName ?: it.listDbId}: ${truncatedLabel(it)}" },
                    Toast.LENGTH_LONG
                ).show()
            }

            saveFilter()
            finish()
        }
    }

    /**
     * @return the studyDbIds of each list and the total the server has if it cut the list short,
     * or null if any list couldn't be fetched
     */
    private suspend fun fetchItems(listDbIds: List<String>): Map<String, Pair<List<String>, Int?>>? {

        val service = brapiService as? BrAPIServiceV2 ?: return null

        return try {
            coroutineScope {
                listDbIds.map { id ->
                    async {
                        val list = service.listService.fetchListDetails(id)
                        val sent = list.details.data.orEmpty().filterNotNull()
                        //the total is only kept when the server sent fewer items than it has
                        id to (sent.distinct() to list.total?.takeIf { it > sent.size })
                    }
                }.awaitAll().toMap()
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.e(TAG, "Failed to fetch study list items", e)
            null
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        //the base saves the filter and closes, the lists' items are needed first
        if (item.itemId == android.R.id.home) {
            applyFilter()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_filter_brapi, menu)
        menu?.findItem(R.id.action_check_all)?.isVisible = true
        menu?.findItem(R.id.action_reset_cache)?.isVisible = false
        menu?.findItem(R.id.action_brapi_filter)?.isVisible = false
        return true
    }
}
