package com.fieldbook.tracker.activities.brapi.io.filter

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.io.BrapiCacheModel
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterCache
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterTypeAdapter
import com.fieldbook.tracker.activities.brapi.io.BrapiStudyImportActivity
import com.fieldbook.tracker.activities.brapi.io.TrialStudyModel
import com.fieldbook.tracker.activities.brapi.io.filter.filterer.BrapiStudyFilterActivity
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrAPIServiceV1
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager
import com.fieldbook.tracker.preferences.GeneralKeys
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brapi.client.v2.model.queryParams.core.StudyQueryParams
import org.brapi.client.v2.model.queryParams.core.TrialQueryParams
import org.brapi.v2.model.core.BrAPIStudy
import org.brapi.v2.model.core.BrAPITrial

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

    private var numFilterBadge: BadgeDrawable? = null

    private var trialModels = mutableListOf<BrAPITrial>()

    private var queryStudiesJob: Job? = null
    private var queryTrialsJob: Job? = null

    protected lateinit var paginationManager: BrapiPaginationManager

    protected lateinit var chipGroup: ChipGroup

    /**
     * Filter name to be used as preferences or intent extras key
     */
    abstract val filterName: String

    abstract fun BrapiCacheModel.mapToUiModel(): List<CheckboxListAdapter.Model>

    /**
     * By default pass the same list.
     * Only affects data in the study list activity, uses shared preferences saved from other activities
     * to filter the list
     */
    open fun BrapiCacheModel.filterByPreferences(): BrapiCacheModel = this

    open fun List<CheckboxListAdapter.Model>.filterBySearchTextPreferences(): List<CheckboxListAdapter.Model> = this

    protected val intentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            restoreModels()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        paginationManager = BrapiPaginationManager(this)

        chipGroup = findViewById(R.id.act_list_filter_cg)

        restoreModels()

        if (intent?.hasExtra(EXTRA_FILTER_ROOT) == true) {
            filterer = intent?.getStringExtra(EXTRA_FILTER_ROOT) ?: ""
        }
    }

    private fun createChip(styleResId: Int, label: String, id: String) =
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
            searchText?.forEach { text ->
                val chip = createChip(R.style.FourthChipTheme, text, text)
                chip.setOnCloseIconClickListener {
                    val currentTexts = prefs.getStringSet("${filterName}${GeneralKeys.LIST_FILTER_TEXTS}", setOf())?.toMutableSet()
                    currentTexts?.remove(text)
                    prefs.edit().putStringSet("${filterName}${GeneralKeys.LIST_FILTER_TEXTS}", currentTexts).apply()
                    chipGroup.removeView(chip)
                    restoreModels()
                }
                chipGroup.addView(chip)
            }
        }
    }

    private fun clearFilters() {
        chipGroup.removeAllViews()
        BrapiFilterCache.clearPreferences(this@BrapiListFilterActivity, defaultRootFilterKey)
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

    fun restoreModels() {

        progressBar.visibility = View.VISIBLE

        launch(Dispatchers.IO) {
            if (!hasData()) {
                launch(Dispatchers.Main) {
                    loadData()
                }
            } else {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    loadStorageItems(BrapiFilterCache.getStoredModels(this@BrapiListFilterActivity))
                }
            }
        }
    }

    open suspend fun loadData() {

        try {
            toggleProgressBar(View.VISIBLE)

            progressBar.visibility = View.VISIBLE

            queryTrialsJob = queryTrials()
            queryTrialsJob?.join()

            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0

            queryStudiesJob = queryStudies()
            queryStudiesJob?.join()

            toggleProgressBar(View.INVISIBLE)

            restoreModels()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun queryStudies() = launch(Dispatchers.IO) {

        val modelCache = arrayListOf<BrAPIStudy>()

        val pageSize = prefs.getString(GeneralKeys.BRAPI_PAGE_SIZE, "512")?.toInt() ?: 512

        (brapiService as BrAPIServiceV2).studyService.fetchAll(
            StudyQueryParams().also {
                it.pageSize(pageSize)
            }
        )
            .catch {
                onApiException()
                queryStudiesJob?.cancel()
            }
            .collect {

                var (totalCount, models) = it as Pair<*, *>
                totalCount = totalCount as Int
                models = models as List<*>
                models = models.map { m -> m as BrAPIStudy }
                modelCache.addAll(models)

                withContext(Dispatchers.Main) {
                    setProgress(modelCache.size, totalCount)
                    if (modelCache.size == totalCount || totalCount < pageSize) {
                        progressBar.visibility = View.GONE
                        fetchDescriptionTv.visibility = View.GONE
                        saveCacheToFile(modelCache as List<BrAPIStudy>, trialModels)
                        queryStudiesJob?.cancel()
                    }
                }
        }
    }

    protected fun onApiException() {
        launch(Dispatchers.Main) {
            Toast.makeText(
                this@BrapiListFilterActivity,
                getString(R.string.act_brapi_list_api_exception),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    private suspend fun queryTrials() = async(Dispatchers.IO) {

        val pageSize = prefs.getString(GeneralKeys.BRAPI_PAGE_SIZE, "512")?.toInt() ?: 512

        (brapiService as BrAPIServiceV2).trialService.fetchAll(
            TrialQueryParams().also {
                it.pageSize(pageSize)
            })

            .catch {
                onApiException()
                cancel()
                queryTrialsJob?.cancel()
            }
            .collect { it ->

                var (total, models) = it as Pair<*, *>
                total = total as Int
                models = models as List<*>
                models = models.map { it as BrAPITrial }

                trialModels.addAll(models)

                withContext(Dispatchers.Main) {
                    setProgress(trialModels.size, total)
                }

                if (total == trialModels.size || total < pageSize) {
                    queryTrialsJob?.cancel()
                }
            }
    }

    override fun onFinishButtonClicked() {
        if (this is BrapiStudyFilterActivity) {

            val models = associateProgramStudy()
            val programDbIds = models.map { it.programDbId }.distinct()

            if (isFilterMode) {
                saveFilter()
                finish()
                return
            }

            if (programDbIds.size > 1) {
                Toast.makeText(
                    this,
                    getString(R.string.act_brapi_list_filter_error_multiple_programs),
                    Toast.LENGTH_SHORT
                ).show()
                return
            } else if (programDbIds.isNotEmpty()) {
                intentLauncher.launch(BrapiStudyImportActivity.getIntent(this).also { intent ->
                    intent.putExtra(
                        BrapiStudyImportActivity.EXTRA_STUDY_DB_IDS,
                        associateProgramStudy().map { it.study.studyDbId }.toTypedArray()
                    )
                    intent.putExtra(
                        BrapiStudyImportActivity.EXTRA_PROGRAM_DB_ID,
                        programDbIds.first()
                    )
                })
            }

        } else {
            saveFilter()
        }

        finish()
    }

    private fun associateProgramStudy(): List<TrialStudyModel> {

        val models = arrayListOf<TrialStudyModel>()

        val checkedIds = cache.filter { it.checked }.map { it.id }
        models.addAll(BrapiFilterCache.getStoredModels(this).studies.filter {
            it.study.studyDbId in checkedIds
        })

        return models
    }

    private fun loadFilter() = BrapiFilterTypeAdapter.toModelList(prefs, getFilterKey())

    /**
     * Save checked items to preferences
     **/
    protected fun saveFilter() {

        //get ids and names of selected programs from the adapter
        val selected = cache.filter { p -> p.checked }

        BrapiFilterTypeAdapter.saveFilter(prefs, getFilterKey(), selected)
    }

    protected fun getIds(filterName: String) =
        BrapiFilterTypeAdapter.toModelList(prefs, "$filterer$filterName").map { it.id }

    private fun loadStorageItems(models: BrapiCacheModel) {

        cache.clear()

        val uiModels = models
            .filterByPreferences()
            .mapToUiModel()
            .distinct()
            .filterBySearchTextPreferences()
            .persistCheckBoxes()
            .sortedBy { it.label }

        cache.addAll(uiModels.filter { it !in cache })

        submitAdapterItems(cache)

        resetChipsUi()

        resetFilterCountDisplay()

    }

    private fun resetFilterCountDisplay() {

        val numFilterLabel = resources.getQuantityString(R.plurals.act_brapi_list_filter, cache.size, cache.size)

        searchBar.editText.hint = getString(R.string.search_bar_hint, numFilterLabel)

        findViewById<MaterialToolbar>(R.id.act_list_filter_tb)
            .menu?.findItem(R.id.action_clear_filters)
            ?.setVisible(chipGroup.childCount > 0)
    }

    private fun List<CheckboxListAdapter.Model>.persistCheckBoxes(): List<CheckboxListAdapter.Model> {

        val selected = loadFilter()

        return map { model ->
            model.checked = selected.any { it.id == model.id }
            model
        }
    }

    private fun resetStorageCache() {

        BrapiFilterCache.delete(this)

        cache.clear()

        submitAdapterItems(cache)

        resetFilterCountDisplay()

        restoreModels()
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

    override fun onResume() {
        super.onResume()
        if (::paginationManager.isInitialized) paginationManager.reset()
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
                AlertDialog.Builder(this)
                    .setTitle(R.string.act_brapi_list_filter_reset_cache_title)
                    .setMessage(getString(R.string.act_brapi_list_filter_reset_cache_message))
                    .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        resetStorageCache()
                        dialog.dismiss()
                    }.show()
            }

            R.id.action_clear_filters -> {
                clearFilters()
            }

            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}