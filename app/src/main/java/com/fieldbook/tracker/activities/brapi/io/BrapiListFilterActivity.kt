package com.fieldbook.tracker.activities.brapi.io

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.content.res.AppCompatResources
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrAPIServiceV1
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager
import com.fieldbook.tracker.preferences.GeneralKeys
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.cancel
import kotlinx.coroutines.flow.catch
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

        const val PREFIX = "com.fieldbook.tracker.activities.filters"

        fun getIntent(activity: Activity): Intent {
            return Intent(activity, BrapiListFilterActivity::class.java)
        }
    }

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

    abstract fun List<TrialStudyModel>.mapToUiModel(): List<CheckboxListAdapter.Model>

    /**
     * By default pass the same list.
     * Only affects data in the study list activity, uses shared preferences saved from other activities
     * to filter the list
     */
    open fun List<TrialStudyModel>.filterByPreferences(): List<TrialStudyModel> = this

    open fun List<CheckboxListAdapter.Model>.filterBySearchTextPreferences(): List<CheckboxListAdapter.Model> = this

    protected val intentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            restoreModels()
        }

    private val brapiService: BrAPIService by lazy {
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

        BrapiFilterCache.checkClearCache(this)
    }


    @OptIn(ExperimentalBadgeUtils::class)
    protected fun resetChipToolbarCount() {

        val toolbar = findViewById<MaterialToolbar>(R.id.act_list_filter_tb)

        if (numFilterBadge != null) {
            BadgeUtils.detachBadgeDrawable(numFilterBadge, toolbar, R.id.action_brapi_filter)
        }

        val numFilters = getNumberOfFilters()

        if (numFilters > 0) {

            numFilterBadge = BadgeDrawable.create(this).apply {
                isVisible = true
                number = getNumberOfFilters()
                horizontalOffset = 16
                maxNumber = 9
            }.also {
                BadgeUtils.attachBadgeDrawable(it, toolbar, R.id.action_brapi_filter)
            }
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

    @OptIn(ExperimentalBadgeUtils::class)
    private fun resetChipsUi() {

        chipGroup.removeAllViews()

        //add new children that are loaded from the program filter activity
        for (f in listOf(
            BrapiProgramFilterActivity.FILTER_NAME,
            BrapiTrialsFilterActivity.FILTER_NAME,
            BrapiSeasonsFilterActivity.FILTER_NAME,
            BrapiCropsFilterActivity.FILTER_NAME
        )) {
            resetFilterChips(f)
        }

        val searchText = prefs.getStringSet(GeneralKeys.LIST_FILTER_TEXTS, setOf())
        searchText?.forEach { text ->
            val chip = createChip(R.style.FourthChipTheme, text, text)
            chip.setOnCloseIconClickListener {
                val currentTexts = prefs.getStringSet(GeneralKeys.LIST_FILTER_TEXTS, setOf())?.toMutableSet()
                currentTexts?.remove(text)
                prefs.edit().putStringSet(GeneralKeys.LIST_FILTER_TEXTS, currentTexts).apply()
                chipGroup.removeView(chip)
                restoreModels()
            }
            chipGroup.addView(chip)
        }

        val numFilterLabel = resources.getQuantityString(R.plurals.act_brapi_list_filter, cache.size, cache.size)

        createChip(R.style.FourthChipStyle, numFilterLabel, numFilterLabel).also {
            it.isCloseIconVisible = false
            it.chipIcon = AppCompatResources.getDrawable(this, R.drawable.delete_sweep)
            it.setOnClickListener {
                chipGroup.removeAllViews()
                BrapiFilterCache.clearPreferences(this@BrapiListFilterActivity)
                restoreModels()
            }
            chipGroup.addView(it)
        }

        //resetChipToolbarCount()
        //clearFilterButton.visibility = if (filtersChipGroup.childCount > 0) View.VISIBLE else View.GONE
        //subtitleTv.visibility = if (filtersChipGroup.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun resetFilterChips(filterName: String) {

        BrapiFilterTypeAdapter.toModelList(prefs, filterName)
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

                    deleteFilterId(filterName, model.id)
                }

                chipGroup.addView(chip)
            }
    }

    private fun deleteFilterId(filterName: String, id: String) {

        chipGroup.removeAllViews()

        BrapiFilterTypeAdapter.deleteFilterId(prefs, filterName, id)

        restoreModels()
    }

    fun restoreModels() {

        progressBar.visibility = View.VISIBLE

        launch(Dispatchers.IO) {
            val storedModels = BrapiFilterCache.getStoredModels(this@BrapiListFilterActivity)
            if (storedModels.isEmpty()) {
                loadData()
            } else {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    loadStorageItems(storedModels)
                }
            }
        }
    }

    private fun getNumberOfFilters() =
        (BrapiFilterTypeAdapter.toModelList(prefs, BrapiProgramFilterActivity.FILTER_NAME) +
                BrapiFilterTypeAdapter.toModelList(prefs, BrapiTrialsFilterActivity.FILTER_NAME) +
                BrapiFilterTypeAdapter.toModelList(prefs, BrapiSeasonsFilterActivity.FILTER_NAME) +
                BrapiFilterTypeAdapter.toModelList(prefs, BrapiCropsFilterActivity.FILTER_NAME))
            .filter { it.checked }.size +
                (prefs.getStringSet(GeneralKeys.LIST_FILTER_TEXTS, setOf())?.size ?: 0)

    private suspend fun loadData() {

        launch(Dispatchers.Main) {

            toggleProgressBar(View.VISIBLE)

            fetchDescriptionTv.text = getString(R.string.act_brapi_list_filter_loading_trials)
            progressBar.visibility = View.VISIBLE

            queryTrials()
            queryTrialsJob?.join()

            fetchDescriptionTv.text = getString(R.string.act_brapi_list_filter_loading_studies)
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0

            queryStudiesJob = queryStudies()
            queryStudiesJob?.join()

            fetchDescriptionTv.text = getString(R.string.act_brapi_list_filter_loading_complete)

            toggleProgressBar(View.INVISIBLE)

        }
    }

    private suspend fun queryStudies() = launch(Dispatchers.IO) {

        var count = 0

        val modelCache = arrayListOf<BrAPIStudy>()

        (brapiService as BrAPIServiceV2).studyService.fetchAll(
            StudyQueryParams()
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

            count += (models as List<*>).size

            modelCache.addAll(models)

            val uiModels = models.map { m ->
                CheckboxListAdapter.Model(
                    checked = false,
                    id = m.studyDbId,
                    label = m.studyName,
                    subLabel = m.locationName ?: ""
                )
            }

            uiModels.sortedBy { m -> m.id }.forEach { model ->
                if (model !in cache) {
                    cache.add(model)
                }
            }

            submitAdapterItems(cache)

            withContext(Dispatchers.Main) {
                setProgress(count, totalCount)
                if (count == totalCount || totalCount < 512) {
                    progressBar.visibility = View.GONE
                    fetchDescriptionTv.visibility = View.GONE
                    saveCacheToFile(modelCache as List<BrAPIStudy>, trialModels)
                    queryStudiesJob?.cancel()
                }
            }
        }
    }

    private fun onApiException() {
        launch(Dispatchers.Main) {
            Toast.makeText(
                this@BrapiListFilterActivity,
                getString(R.string.act_brapi_list_api_exception),
                Toast.LENGTH_SHORT
            ).show()
        }
        finish()
    }

    private suspend fun queryTrials() {

        queryTrialsJob = async(Dispatchers.IO) {

            var count = 0

            (brapiService as BrAPIServiceV2).trialService.fetchAll(TrialQueryParams())
                .catch {
                    onApiException()
                    queryTrialsJob?.cancel()
                }
                .collect { it ->

                    var (total, models) = it as Pair<*, *>
                    total = total as Int
                    models = models as List<*>
                    models = models.map { it as BrAPITrial }

                    models.forEach { model ->
                        if (model !in trialModels) {
                            trialModels.add(model)

                            Log.d("TRIAL", model.trialName)

                            count++
                        }
                    }

                    withContext(Dispatchers.Main) {
                        setProgress(count, total)
                    }

                    if (total == trialModels.size || total < 512) {
                        queryTrialsJob?.cancel()
                    }
                }
        }
    }

    override fun onFinishButtonClicked() {
        if (this is BrapiStudyFilterActivity) {

            val models = associateProgramStudy()
            val programDbIds = models.map { it.programDbId }.distinct()

            if (programDbIds.size > 1) {
                Toast.makeText(
                    this,
                    getString(R.string.act_brapi_list_filter_error_multiple_programs),
                    Toast.LENGTH_SHORT
                ).show()
                return
            } else if (programDbIds.isNotEmpty()) {
                intentLauncher.launch(BrapiStudyImportActivity.getIntent(this).also { intent ->
                    intent.putExtra(BrapiStudyImportActivity.EXTRA_STUDY_DB_IDS,
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
        models.addAll(BrapiFilterCache.getStoredModels(this).filter {
            it.study.studyDbId in checkedIds
        })

        return models
    }

    private fun setupApplyFilterView() {

        importTextView.visibility = showNextButton().let { show ->
            if (show) View.VISIBLE else View.GONE
        }

        importTextView.setOnClickListener {
            if (this is BrapiStudyFilterActivity) {

                val models = associateProgramStudy()
                val programDbIds = models.map { it.programDbId }.distinct()

                if (programDbIds.size > 1) {
                    Toast.makeText(
                        this,
                        getString(R.string.act_brapi_list_filter_error_multiple_programs),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                } else if (programDbIds.isNotEmpty()) {
                    intentLauncher.launch(BrapiStudyImportActivity.getIntent(this).also { intent ->
                        intent.putExtra(BrapiStudyImportActivity.EXTRA_STUDY_DB_IDS,
                            models.map { it.study.studyDbId }.toTypedArray()
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
    }

    private fun loadFilter() = BrapiFilterTypeAdapter.toModelList(prefs, filterName)

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

    protected fun getIds(filterName: String) =
        BrapiFilterTypeAdapter.toModelList(prefs, filterName).map { it.id }

    private fun loadStorageItems(models: List<TrialStudyModel>) {

        cache.clear()

        val uiModels = models
            .filterByPreferences()
            .mapToUiModel()
            .distinct()
            .filterBySearchTextPreferences()
            .persistCheckBoxes()
            .sortedBy { it.label }

        cache.addAll(uiModels)

        submitAdapterItems(cache)

        resetChipsUi()

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

        BrapiFilterCache.saveToStorage(this, studyTrials)
    }

    override fun onResume() {
        super.onResume()
        if (::paginationManager.isInitialized) paginationManager.reset()
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
}