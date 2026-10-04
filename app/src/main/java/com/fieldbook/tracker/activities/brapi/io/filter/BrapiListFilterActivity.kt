package com.fieldbook.tracker.activities.brapi.io.filter

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.BrapiAuthActivity
import com.fieldbook.tracker.activities.brapi.io.BrapiCacheModel
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterCache
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterTypeAdapter
import com.fieldbook.tracker.activities.brapi.io.TrialStudyModel
import com.fieldbook.tracker.activities.brapi.io.filter.filterer.BrapiStudyFilterActivity
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrAPIServiceV1
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brapi.client.v2.model.queryParams.core.StudyQueryParams
import org.brapi.client.v2.model.queryParams.core.TrialQueryParams
import org.brapi.v2.model.core.BrAPIStudy
import org.brapi.v2.model.core.BrAPITrial
import androidx.core.content.edit
import androidx.core.view.isNotEmpty
import androidx.core.content.edit

/**
 * List Filter activity base class for BrAPI filter activities
 * Subclasses must implement mapToUiModel to convert their brapi objects to checkbox adapter models
 */
abstract class BrapiListFilterActivity<T> : ListFilterActivity() {

    companion object {

        const val EXTRA_FILTER_ROOT = "com.fieldbook.tracker.activities.brapi.io.extras.filter_root"

        fun getIntent(activity: Activity): Intent {
            return Intent(activity, BrapiListFilterActivity::class.java)
        }
    }

    open val defaultRootFilterKey: String = ""

    private var filterer: String = ""

    private var trialModels = mutableListOf<BrAPITrial>()

    private var queryStudiesJob: Job? = null
    private var queryTrialsJob: Job? = null

    //the restore or download in progress, and whether another was asked for meanwhile, see restoreModels
    private var restoreJob: Job? = null
    private var restoreAgain = false

    protected var selectionMenuItem: MenuItem? = null

    protected lateinit var paginationManager: BrapiPaginationManager

    protected lateinit var chipGroup: ChipGroup

    /**
     * Filter name to be used as preferences or intent extras key
     */
    abstract val filterName: String

    abstract fun BrapiCacheModel.mapToUiModel(): List<CheckboxListAdapter.Model?>

    abstract fun List<CheckboxListAdapter.Model>.filterExists(): List<CheckboxListAdapter.Model>

    /**
     * By default pass the same list.
     * Only affects data in the study list activity, uses shared preferences saved from other activities
     * to filter the list
     */
    open fun BrapiCacheModel.filterByPreferences(): BrapiCacheModel = this

    open fun List<CheckboxListAdapter.Model>.filterBySearchTextPreferences(): List<CheckboxListAdapter.Model> = this

    protected val intentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) finish()
            else restoreModels()
        }

    protected val brapiService: BrAPIService by lazy {
        BrAPIServiceFactory.getBrAPIService(this).also { service ->
            if (service is BrAPIServiceV1) {
                launch(Dispatchers.Main) {
                    Toast.makeText(
                        this@BrapiListFilterActivity,
                        getString(R.string.brapi_v1_is_not_compatible),
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        }
    }

    override fun showNextButton(): Boolean {
        return cache.any { it.checked }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        paginationManager = BrapiPaginationManager(this)

        chipGroup = findViewById(R.id.act_list_filter_cg)

        restoreModels()

        if (intent?.hasExtra(EXTRA_FILTER_ROOT) == true) {
            filterer = intent?.getStringExtra(EXTRA_FILTER_ROOT) ?: ""
        }
    }

    protected fun createChip(styleResId: Int, label: String, id: String) =
        Chip(ContextThemeWrapper(this, styleResId)).apply {
            text = label
            tag = id
            closeIcon = AppCompatResources.getDrawable(this@BrapiListFilterActivity, R.drawable.close)
            isCloseIconVisible = true
            isCheckable = false
        }

    private fun resetChipsUi() {

        if (filterer.isEmpty()) {

            chipGroup.removeAllViews()

            //add new children that are loaded from the program filter activity
            for (f in listOf(
                BrapiProgramFilterActivity.FILTER_NAME,
                BrapiTrialsFilterActivity.FILTER_NAME,
                BrapiSeasonsFilterActivity.FILTER_NAME,
                BrapiCropsFilterActivity.FILTER_NAME,
                BrapiStudyFilterActivity.FILTER_NAME
            )) {
                resetFilterChips(f)
            }

            val searchText = prefs.getStringSet("${filterName}${GeneralKeys.LIST_FILTER_TEXTS}", setOf())
            //blank filters saved before they were blocked match everything, so they aren't shown
            searchText?.filter { it.isNotBlank() }?.forEach { text ->
                val chip = createChip(R.style.FourthChipTheme, text, text)
                chip.setOnCloseIconClickListener {
                    val currentTexts = prefs.getStringSet("${filterName}${GeneralKeys.LIST_FILTER_TEXTS}", setOf())?.toMutableSet()
                    currentTexts?.remove(text)
                    prefs.edit {
                        putStringSet(
                            "${filterName}${GeneralKeys.LIST_FILTER_TEXTS}",
                            currentTexts
                        )
                    }
                    chipGroup.removeView(chip)
                    restoreModels()
                }
                chipGroup.addView(chip)
            }

            addExtraFilterChips()
        }
    }

    /**
     * Adds chips for filters a subclass keeps itself, called after the saved filter chips are added.
     */
    protected open fun addExtraFilterChips() = Unit

    /**
     * Resets filters a subclass keeps itself, called when all filters are cleared.
     */
    protected open fun clearExtraFilters() = Unit

    private fun clearFilters() {
        chipGroup.removeAllViews()
        BrapiFilterCache.clearPreferences(this@BrapiListFilterActivity, defaultRootFilterKey)
        clearExtraFilters()
        restoreModels()
    }

    private fun getFilterKey() = "$filterer$filterName"

    private fun resetFilterChips(filterName: String) {

        BrapiFilterTypeAdapter.toModelList(prefs, "$defaultRootFilterKey$filterName")
            .forEach { model ->

                val chip = createChip(
                    when (filterName) {
                        BrapiProgramFilterActivity.FILTER_NAME -> R.style.FirstChipTheme
                        BrapiSeasonsFilterActivity.FILTER_NAME -> R.style.SecondChipTheme
                        BrapiTrialsFilterActivity.FILTER_NAME -> R.style.ThirdChipTheme
                        else -> R.style.FourthChipTheme
                    }, model.label, model.id
                )

                chip.setOnCloseIconClickListener {

                    deleteFilterId("$defaultRootFilterKey$filterName", model.id)
                }

                chipGroup.addView(chip)
            }
    }

    private fun deleteFilterId(filterName: String, id: String) {

        chipGroup.removeAllViews()

        BrapiFilterTypeAdapter.deleteFilterId(prefs, filterName, id)

        restoreModels()
    }

    open fun hasData(): Boolean {
        return BrapiFilterCache.getStoredModels(this@BrapiListFilterActivity).studies.isNotEmpty()
    }

    /**
     * Shows the cached models, downloading them first if there's no cache.
     * Runs one at a time: two downloads at once would share the trial list and cancel each other's jobs.
     * A call while one is running restores again once it finishes, with the filters as they are then.
     * Called on the main thread.
     */
    fun restoreModels() {

        progressBar.visibility = View.VISIBLE

        if (restoreJob?.isActive == true) {
            restoreAgain = true
            return
        }

        restoreJob = launch(Dispatchers.IO) {
            if (!hasData()) {
                withContext(Dispatchers.Main) {
                    //downloading every trial and study can take a long time on a large server
                    setKeepScreenOn(true)
                    try {
                        loadData()
                    } finally {
                        setKeepScreenOn(false)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    loadStorageItems(BrapiFilterCache.getStoredModels(this@BrapiListFilterActivity))
                }
            }
        }.also { job ->
            job.invokeOnCompletion { cause ->
                //not after the activity's scope is cancelled, the launch below would do nothing anyway
                if (cause == null) launch(Dispatchers.Main) {
                    if (restoreAgain) {
                        restoreAgain = false
                        restoreModels()
                    }
                }
            }
        }
    }

    open suspend fun loadData() {

        try {
            trialModels.clear()

            progressBar.visibility = View.VISIBLE

            queryTrialsJob = queryTrials()
            queryTrialsJob?.join()

            queryStudiesJob = queryStudies()
            queryStudiesJob?.join()

            //gone rather than invisible, so the search bar moves back up under the toolbar
            toggleProgressBar(View.GONE)

            //show what was just downloaded, restoreModels would download again if the server has no studies
            loadStorageItems(BrapiFilterCache.getStoredModels(this@BrapiListFilterActivity))

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun queryStudies() = launch(Dispatchers.IO) {

        val modelCache = arrayListOf<BrAPIStudy>()

        val pageSize = BrAPIService.getPageSize(this@BrapiListFilterActivity)

        if (brapiService is BrAPIServiceV1)
            return@launch

        var failed = false

        (brapiService as BrAPIServiceV2).studyService.fetchAll(
            StudyQueryParams().also {
                it.pageSize(pageSize)
                it.active("true")
            }
        )
            .catch { e ->
                failed = true
                onApiException(e)
                queryStudiesJob?.cancel()
            }
            .collect {

                val models = (it as Pair<*, *>).second as List<*>
                modelCache.addAll(models.map { m -> m as BrAPIStudy })
        }

        //the flow completes once every page has responded, so save whatever arrived even if it doesn't
        //match the server's total count, a failed page ends the flow with an error instead
        if (failed) return@launch

        withContext(Dispatchers.Main) {
            progressBar.visibility = View.GONE
            fetchDescriptionTv.visibility = View.GONE
        }

        saveCacheToFile(modelCache, trialModels)
    }

    protected fun onApiException(e: Throwable? = null) {
        val httpCode = try {
            (e as? org.brapi.client.v2.model.exceptions.ApiException)?.code
                ?: (e?.cause as? org.brapi.client.v2.model.exceptions.ApiException)?.code
                ?: 0
        } catch (_: Exception) { 0 }

        launch(Dispatchers.Main) {
            if (httpCode == 401) {
                BrAPIService.handleConnectionError(this@BrapiListFilterActivity, httpCode)
                AlertDialog.Builder(this@BrapiListFilterActivity, R.style.AppAlertDialog)
                    .setTitle(R.string.brapi_auth_required_title)
                    .setMessage(R.string.brapi_auth_required_message)
                    .setPositiveButton(R.string.brapi_save_authorize) { _, _ ->
                        val intent = Intent(this@BrapiListFilterActivity, BrapiAuthActivity::class.java).apply {
                            putExtra(BrapiAuthActivity.EXTRA_SERVER_URL, prefs.getString(PreferenceKeys.BRAPI_BASE_URL, ""))
                            putExtra(BrapiAuthActivity.EXTRA_OIDC_URL, prefs.getString(PreferenceKeys.BRAPI_OIDC_URL, ""))
                            putExtra(BrapiAuthActivity.EXTRA_OIDC_FLOW, prefs.getString(PreferenceKeys.BRAPI_OIDC_FLOW, ""))
                            putExtra(BrapiAuthActivity.EXTRA_OIDC_CLIENT_ID, prefs.getString(PreferenceKeys.BRAPI_OIDC_CLIENT_ID, ""))
                            putExtra(BrapiAuthActivity.EXTRA_OIDC_SCOPE, prefs.getString(PreferenceKeys.BRAPI_OIDC_SCOPE, ""))
                        }
                        startActivity(intent)
                        finish()
                    }
                    .setNegativeButton(R.string.dialog_cancel) { _, _ -> finish() }
                    .show()
            } else {
                Toast.makeText(
                    this@BrapiListFilterActivity,
                    getString(R.string.act_brapi_list_api_exception),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private suspend fun queryTrials() = async(Dispatchers.IO) {

        val pageSize = BrAPIService.getPageSize(this@BrapiListFilterActivity)

        if (brapiService is BrAPIServiceV1)
            return@async

        (brapiService as BrAPIServiceV2).trialService.fetchAll(
            TrialQueryParams().also {
                it.pageSize(pageSize)
                it.active("true")
            })

            .catch { e ->
                onApiException(e)
                cancel()
                queryTrialsJob?.cancel()
            }
            .collect { it ->

                var (total, models) = it as Pair<*, *>
                total = total as Int
                models = models as List<*>
                models = models.map { it as BrAPITrial }

                trialModels.addAll(models)

                if (total == trialModels.size || total < pageSize) {
                    queryTrialsJob?.cancel()
                }
            }
    }

    override fun onFinishButtonClicked() {
        saveFilter()
        finish()
    }

    private fun loadFilter() = BrapiFilterTypeAdapter.toModelList(prefs, getFilterKey())

    /**
     * Save checked items to preferences
     **/
    protected fun saveFilter() {

        //get ids and names of selected programs from the adapter
        BrapiFilterTypeAdapter.saveFilter(prefs, getFilterKey(),
            (recyclerView.adapter as CheckboxListAdapter).selected)
    }

    protected fun getCheckedModels(filterName: String) =
        BrapiFilterTypeAdapter.toModelList(prefs, "$filterer$filterName").filter { it.checked }

    protected fun getModels(filterName: String) =
        BrapiFilterTypeAdapter.toModelList(prefs, "$filterer$filterName")

    protected fun getIds(filterName: String) = getModels(filterName).map { it.id }

    private fun loadStorageItems(models: BrapiCacheModel, deselect: Boolean = false) {

        cache.clear()

        if (deselect) {
            (recyclerView.adapter as CheckboxListAdapter).selected.clear()
            BrapiFilterTypeAdapter.saveFilter(prefs, getFilterKey(), listOf())
        }

        val uiModels = models
            .filterByPreferences()
            .mapToUiModel()
            .filterNotNull()
            .distinct()
            .filterBySearchTextPreferences()
            .filterExists()
            .persistCheckBoxes()
            .sortedBy { it.label }

        cache.addAll(uiModels.filter { it !in cache })

        submitAdapterItems(cache)

        resetChipsUi()

        resetFilterCountDisplay()

        resetSelectionCountDisplay()
    }

    private fun resetFilterCountDisplay() {

        val numFilterLabel = resources.getQuantityString(R.plurals.act_brapi_list_filter, cache.size, cache.size)

        searchBar.editText.hint = getString(R.string.search_bar_hint, numFilterLabel)

        findViewById<MaterialToolbar>(R.id.act_list_filter_tb)
            .menu?.findItem(R.id.action_clear_filters)
            ?.isVisible = chipGroup.isNotEmpty()
    }

    private fun List<CheckboxListAdapter.Model>.persistCheckBoxes(): List<CheckboxListAdapter.Model> {

        val selected = (loadFilter() + (recyclerView.adapter as CheckboxListAdapter).selected).distinct()

        return map { model ->
            model.checked = selected.any { it.id == model.id }
            model
        }
    }

    private fun resetStorageCache() {

        BrapiFilterCache.delete(this)

        cache.clear()

        //the count is of the deleted cache, loadStorageItems shows the new one once the reload finishes
        searchBar.editText.hint = null

        // Filters refer to programs, trials and studies of the old cache, so they're cleared with it.
        // Don't clear the adapter here — keep showing the previous list while the progress bar
        // indicates loading. clearFilters() calls restoreModels(), which reloads from the server
        // and updates everything (adapter + hint) once the fresh data arrives.
        clearFilters()
    }

    private fun saveCacheToFile(models: List<BrAPIStudy>, trials: List<BrAPITrial>) {

        val studyTrials = models.map {

            val model = TrialStudyModel(it)

            trials.firstOrNull { trial -> trial.trialDbId == it.trialDbId }?.let { trial ->

                model.trialDbId = trial.trialDbId
                model.trialName = trial.trialName
                model.programDbId = trial.programDbId
                model.programName = trial.programName
            }

            model
        }

        BrapiFilterCache.saveStudyTrialsToFile(this, studyTrials)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menu?.findItem(R.id.action_check_all)?.isVisible = true
        menu?.findItem(R.id.action_reset_cache)?.isVisible = false
        menu?.findItem(R.id.action_brapi_filter)?.isVisible = false
        selectionMenuItem = menu?.findItem(R.id.action_clear_selection)
        return true
    }

    override fun onResume() {
        super.onResume()
        if (::paginationManager.isInitialized) paginationManager.reset()
    }

    /**
     * Checks every item in the list as currently filtered and searched, or unchecks them if they're all checked.
     * The adapter's selection is updated directly, since rows only update it as they're bound and
     * off-screen rows would otherwise be missed.
     */
    private fun toggleAllVisible() {

        val adapter = recyclerView.adapter as? CheckboxListAdapter ?: return
        val visible = adapter.currentList

        if (visible.isEmpty()) return

        val check = !visible.all { it.checked }

        visible.forEach { model ->
            model.checked = check
            if (check) {
                if (model !in adapter.selected) adapter.selected.add(model)
            } else {
                adapter.selected.remove(model)
            }
        }

        adapter.notifyItemRangeChanged(0, visible.size)

        importTextView.visibility = if (showNextButton()) View.VISIBLE else View.GONE
        resetSelectionCountDisplay()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.action_check_all -> {
                toggleAllVisible()
                return true
            }

            R.id.action_reset_cache -> {
                AlertDialog.Builder(this, R.style.AppAlertDialog)
                    .setTitle(R.string.act_brapi_list_filter_reset_cache_title)
                    .setMessage(getString(R.string.act_brapi_list_filter_reset_cache_message))
                    .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        resetStorageCache()
                        dialog.dismiss()
                    }.show()
            }

            R.id.action_clear_selection -> {
                AlertDialog.Builder(this, R.style.AppAlertDialog)
                    .setTitle(R.string.act_brapi_list_filter_reset_cache_title)
                    .setMessage(getString(R.string.act_brapi_list_filter_reset_selection_message))
                    .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        loadStorageItems(BrapiFilterCache.getStoredModels(this@BrapiListFilterActivity), deselect = true)
                        dialog.dismiss()
                    }.show()
            }

            R.id.action_clear_filters -> {
                clearFilters()
            }

            android.R.id.home -> {
                saveFilter()
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}