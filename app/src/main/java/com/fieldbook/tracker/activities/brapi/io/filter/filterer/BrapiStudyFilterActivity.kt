package com.fieldbook.tracker.activities.brapi.io.filter.filterer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.io.BrapiCacheModel
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterCache
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterTypeAdapter
import com.fieldbook.tracker.activities.brapi.io.BrapiImportedLevels
import com.fieldbook.tracker.activities.brapi.io.BrapiStudyImportActivity
import com.fieldbook.tracker.activities.brapi.io.StudyListModel
import com.fieldbook.tracker.activities.brapi.io.TrialStudyModel
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiCropsFilterActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiProgramFilterActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiSeasonsFilterActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiSeasonsFilterActivity.Companion.filterByProgramAndTrial
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiStudyListsFilterActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiSubFilterListActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiTrialsFilterActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiTrialsFilterActivity.Companion.filterByProgram
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.preferences.GeneralKeys
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.brapi.client.v2.model.queryParams.core.ListQueryParams
import org.brapi.v2.model.core.BrAPIListSummary
import org.brapi.v2.model.core.BrAPIListTypes
import org.brapi.v2.model.core.BrAPIStudy
import javax.inject.Inject

@AndroidEntryPoint
class BrapiStudyFilterActivity(
    override val defaultRootFilterKey: String = FILTERER_KEY,
    override val filterName: String = "studies",
    override val titleResId: Int = R.string.brapi_studies_filter_title
) : BrapiSubFilterListActivity<BrAPIStudy>() {

    companion object {
        const val FILTER_NAME = "studies"
        const val FILTERER_KEY = "com.fieldbook.tracker.activities.brapi.io.filters.studies."
        const val EXTRA_MODE = "com.fieldbook.tracker.activities.brapi.io.filterer.BrapiStudyFilterActivity.EXTRA_MODE"
        private const val SHOW_IMPORTED_CHIP_ID = "com.fieldbook.tracker.activities.brapi.io.filterer.BrapiStudyFilterActivity.SHOW_IMPORTED"
        fun getIntent(context: Context) = Intent(context, BrapiStudyFilterActivity::class.java)
    }

    var isFilterMode = false

    enum class FilterChoice {
        PROGRAM,
        SEASON,
        TRIAL,
        CROP,
        LIST
    }

    //fetching the server's study lists, see onStorageItemsLoaded
    private var studyListsJob: Job? = null

    private val studyImportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val resultIntent = Intent()

                result.data?.extras?.let { resultIntent.putExtras(it) }

                setResult(RESULT_OK, resultIntent)
                finish()
            } else if (result.resultCode == RESULT_CANCELED) {
                restoreModels()
            }
        }

    @Inject
    lateinit var database: DataHelper

    override fun BrapiCacheModel.filterByPreferences(): BrapiCacheModel {

        val programDbIds = getIds("${defaultRootFilterKey}${BrapiProgramFilterActivity.FILTER_NAME}")
        val trialDbIds = getModels(BrapiTrialsFilterActivity.FILTER_NAME).filter { it.checked }.map { it.id }
        val seasonDbIds = getIds("${defaultRootFilterKey}${BrapiSeasonsFilterActivity.FILTER_NAME}")
        val commonCropNames = getIds("${defaultRootFilterKey}${BrapiCropsFilterActivity.FILTER_NAME}")
        val listDbIds = getIds("${defaultRootFilterKey}${BrapiStudyListsFilterActivity.FILTER_NAME}")

        val result = this.also {
            it.studies =
                it.studies.filterByLists(listDbIds, it.studyLists.orEmpty())
                    .filter { if (programDbIds.isNotEmpty()) it.programDbId in programDbIds else true }
                    .filter { if (trialDbIds.isNotEmpty()) it.trialDbId in trialDbIds else true }
                    .filter { if (seasonDbIds.isNotEmpty()) it.study.seasons.orEmpty().any { s -> s in seasonDbIds } else true }
                    .filter { if (commonCropNames.isNotEmpty()) it.study.commonCropName in commonCropNames else true }
        }

        return result
    }

    /**
     * The studies of the chosen lists, or every study if no list is chosen.
     * A list can hold studies the cache doesn't have, such as inactive ones. They're listed by id,
     * the import screen fetches their details. Their program, trial, season and crop aren't known,
     * so those filters hide them.
     */
    private fun List<TrialStudyModel>.filterByLists(
        listDbIds: List<String>,
        lists: List<StudyListModel>
    ): List<TrialStudyModel> {

        if (listDbIds.isEmpty()) return this

        val studyDbIds = lists.filter { it.listDbId in listDbIds }.flatMapTo(linkedSetOf()) { it.studyDbIds.orEmpty() }

        val cached = filter { it.study.studyDbId in studyDbIds }
        val cachedDbIds = cached.mapTo(hashSetOf()) { it.study.studyDbId }

        return cached + studyDbIds.filterNot { it in cachedDbIds }.map { id ->
            TrialStudyModel(BrAPIStudy().also {
                it.studyDbId = id
                it.studyName = id
            })
        }
    }

    /**
     * Fetches the server's study lists the first time the cache is shown, including caches saved
     * before lists were fetched. They're saved even if there are none, or the server doesn't support lists,
     * so they're only asked for again once the cache is reset.
     */
    override fun onStorageItemsLoaded() {

        if (isFilterMode || studyListsJob?.isActive == true) return

        studyListsJob = launch(Dispatchers.IO) {

            if (!hasData() || BrapiFilterCache.getStoredModels(this@BrapiStudyFilterActivity).studyLists != null) {
                return@launch
            }

            val lists = fetchStudyLists()

            //the cache may have been reset meanwhile
            ensureActive()

            BrapiFilterCache.saveStudyLists(this@BrapiStudyFilterActivity, lists)
        }
    }

    override fun onCacheReset() {
        studyListsJob?.cancel()
    }

    /**
     * @return the server's lists of studies, without their items, or empty if they couldn't be fetched
     */
    private suspend fun fetchStudyLists(): List<StudyListModel> {

        val service = brapiService as? BrAPIServiceV2 ?: return emptyList()

        return try {

            val lists = arrayListOf<BrAPIListSummary>()

            service.listService.fetchAll(ListQueryParams().also {
                it.listType(BrAPIListTypes.STUDIES)
                it.pageSize(BrAPIService.getPageSize(this))
            }).collect { page ->
                lists.addAll(((page as Pair<*, *>).second as List<*>).filterIsInstance<BrAPIListSummary>())
            }

            //servers that ignore the type filter send every list
            lists.filter { it.listDbId != null && (it.listType == null || it.listType == BrAPIListTypes.STUDIES) }
                .distinctBy { it.listDbId }
                .map { StudyListModel(it.listDbId, it.listName, it.listOwnerName, it.listSize) }

        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.e(TAG, "Failed to fetch study lists", e)
            emptyList()
        }
    }

    override fun List<CheckboxListAdapter.Model>.filterBySearchTextPreferences(): List<CheckboxListAdapter.Model> {

        val searchTexts = prefs.getStringSet("${filterName}${GeneralKeys.LIST_FILTER_TEXTS}", emptySet())

        return if (searchTexts?.isEmpty() != false) this else filter { model ->
            searchTexts.map { it.lowercase() }.all { tokens ->
                tokens in model.label.lowercase() || tokens in model.subLabel.lowercase() || tokens in model.id.lowercase()
            }
        }
    }

    override fun List<CheckboxListAdapter.Model>.filterExists(): List<CheckboxListAdapter.Model> {

        //fully imported studies can be listed so their levels can be refreshed from the import screen
        if (showImportedStudies) return this

        //studies stay listed until every observation level of their units has been imported
        val importedLevels = BrapiImportedLevels(database.allFieldObjects)
        return filterNot { importedLevels.isFullyImported(it.id) }
    }

    //an extra filter shown as a chip, not saved, cleared with the other filters
    private var showImportedStudies = false

    //shown or hidden directly, rebuilding the menu would leave the selection count badge on a reused toolbar view
    private var showImportedMenuItem: MenuItem? = null

    override fun addExtraFilterChips() {

        if (!showImportedStudies) return

        chipGroup.addView(
            createChip(R.style.FourthChipTheme, getString(R.string.brapi_filter_imported_studies), SHOW_IMPORTED_CHIP_ID).apply {
                setOnCloseIconClickListener { setShowImportedStudies(false) }
            }
        )
    }

    //clear filters reloads the list itself
    override fun clearExtraFilters() = updateShowImportedStudies(false)

    private fun setShowImportedStudies(show: Boolean) {
        updateShowImportedStudies(show)
        restoreModels()
    }

    private fun updateShowImportedStudies(show: Boolean) {

        if (showImportedStudies && !show) {
            //hidden studies would otherwise still be imported with the selection
            val importedLevels = BrapiImportedLevels(database.allFieldObjects)
            (recyclerView.adapter as CheckboxListAdapter).selected.removeAll { importedLevels.isFullyImported(it.id) }
        }

        showImportedStudies = show

        showImportedMenuItem?.isVisible = !show
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SHOW_IMPORTED_CHIP_ID, showImportedStudies)
    }

    override fun onSearchTextComplete(searchText: String) {

        //blank text would add an empty filter chip
        val text = searchText.trim()
        if (text.isEmpty()) return

        prefs.getStringSet("${filterName}${GeneralKeys.LIST_FILTER_TEXTS}", setOf())?.let { texts ->
            prefs.edit().putStringSet(
                "${filterName}${GeneralKeys.LIST_FILTER_TEXTS}",
                texts.plus(text)
            ).apply()
        }

        restoreModels()
    }

    override fun setupSearch(models: List<CheckboxListAdapter.Model>) {
        super.setupSearch(models)

        searchBar.editText.setOnEditorActionListener { v, actionId, _ ->

            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onSearchTextComplete(v.text.toString())
                searchBar.editText.text.clear()
                return@setOnEditorActionListener true
            }

            return@setOnEditorActionListener false
        }
    }

    override fun BrapiCacheModel.mapToUiModel() = studies.mapNotNull { model ->
        try {
            CheckboxListAdapter.Model(
                checked = false,
                id = model.study.studyDbId,
                label = model.study.studyName,
                subLabel = model.trialName ?: model.study.locationName ?: ""
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //kept across rotation, but not saved once the page is closed
        if (savedInstanceState?.getBoolean(SHOW_IMPORTED_CHIP_ID) == true) setShowImportedStudies(true)

        chipGroup.visibility = View.VISIBLE

        setupMainToolbar()

        isFilterMode = intent?.hasExtra(EXTRA_MODE) == true

        importTextView.text = getString(if (isFilterMode) R.string.act_brapi_study_filter else R.string.act_brapi_filter_import)

        fetchDescriptionTv.text = getString(R.string.act_brapi_list_filter_loading_studies)

        onBackPressedDispatcher.addCallback(this, standardBackCallback())
    }

    override fun showNextButton(): Boolean {
        return if (isFilterMode) true else (recyclerView.adapter as CheckboxListAdapter).selected.isNotEmpty()
    }

    private fun setupMainToolbar() {

        supportActionBar?.title = getString(R.string.import_brapi_field_title)

        //enable home button as back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

    }

    //add menu to toolbar
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_filter_brapi, menu)
        menu?.findItem(R.id.action_check_all)?.isVisible = true
        menu?.findItem(R.id.action_reset_cache)?.isVisible = !isFilterMode
        menu?.findItem(R.id.action_brapi_filter)?.isVisible = !isFilterMode
        //shown until the filter is on, its chip turns it off again
        showImportedMenuItem = menu?.findItem(R.id.action_show_imported)?.apply { isVisible = !showImportedStudies }
        selectionMenuItem = menu?.findItem(R.id.action_clear_selection)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed();
            return true
        } else if (item.itemId == R.id.action_brapi_filter) {
            showFilterChoiceDialog()
        } else if (item.itemId == R.id.action_show_imported) {
            setShowImportedStudies(true)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onFinishButtonClicked() {

        if (isFilterMode) {
            saveFilter()
            finish()
            return
        }

        val models = associateProgramStudy()

        //studies from a list that the cache doesn't have follow the cached ones, see filterByLists
        val studyDbIds = (models.map { it.study.studyDbId } + checkedIds()).distinct()

        if (studyDbIds.isNotEmpty()) {
            studyImportLauncher.launch(BrapiStudyImportActivity.getIntent(this).also { intent ->
                intent.putExtra(
                    BrapiStudyImportActivity.EXTRA_STUDY_DB_IDS,
                    studyDbIds.toTypedArray()
                )
                intent.putExtra(
                    BrapiStudyImportActivity.EXTRA_PROGRAM_DB_ID,
                    models.firstNotNullOfOrNull { it.programDbId }
                )
            })
        }
    }

    private fun checkedIds() = (recyclerView.adapter as CheckboxListAdapter).selected.map { it.id }

    private fun associateProgramStudy(): List<TrialStudyModel> {

        val models = arrayListOf<TrialStudyModel>()

        val checkedIds = checkedIds()
        models.addAll(BrapiFilterCache.getStoredModels(this).studies.filter {
            it.study.studyDbId in checkedIds
        })

        return models
    }

    private fun showFilterChoiceDialog() {

        val pids = BrapiFilterTypeAdapter.toModelList(prefs,
            "${defaultRootFilterKey}${BrapiProgramFilterActivity.FILTER_NAME}")
            .filter { it.checked }
            .map { it.id }

        val tids = BrapiFilterTypeAdapter.toModelList(prefs,
            "${defaultRootFilterKey}${BrapiTrialsFilterActivity.FILTER_NAME}")
            .filter { it.checked }
            .map { it.id }

        val seasons = BrapiFilterTypeAdapter.toModelList(prefs,
            "${defaultRootFilterKey}${BrapiSeasonsFilterActivity.FILTER_NAME}")
            .filter { it.checked }
            .map { it.id }

        val cacheModel = BrapiFilterCache.getStoredModels(this)
        val models = cacheModel.studies
        val programCount = models.mapNotNull { it.programDbId }.distinct().size
        val trialCount = models.filterByProgram(pids, seasons).mapNotNull { it.trialDbId }.distinct().size
        val seasonCount = models.filterByProgramAndTrial(pids, tids)
            .map { it.study.seasons ?: listOf() }.flatten().filterNotNull().distinct().size
        val cropCount = models.map { it.study.commonCropName }.distinct().size
        val listCount = cacheModel.studyLists.orEmpty().size

        val choices = listOfNotNull(
            FilterChoice.PROGRAM to getString(R.string.brapi_filter_type_program_count, "$programCount"),
            FilterChoice.SEASON to getString(R.string.brapi_filter_type_season_count, "$seasonCount"),
            FilterChoice.TRIAL to getString(R.string.brapi_filter_type_trial_count, "$trialCount"),
            FilterChoice.CROP to getString(R.string.brapi_filter_type_crop_count, "$cropCount"),
            //only offered when the server has lists of studies
            (FilterChoice.LIST to getString(R.string.brapi_filter_type_list_count, "$listCount")).takeIf { listCount > 0 }
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_brapi_filter_choices_title)
            .setItems(choices.map { it.second }.toTypedArray()) { _, which ->
                intentLauncher.launch(
                    when (choices[which].first) {
                        FilterChoice.PROGRAM -> BrapiProgramFilterActivity.getIntent(this)
                        FilterChoice.SEASON -> BrapiSeasonsFilterActivity.getIntent(this)
                        FilterChoice.CROP -> BrapiCropsFilterActivity.getIntent(this)
                        FilterChoice.LIST -> BrapiStudyListsFilterActivity.getIntent(this)
                        FilterChoice.TRIAL -> BrapiTrialsFilterActivity.getIntent(this)
                    }.also {
                        it.putExtra(EXTRA_FILTER_ROOT, defaultRootFilterKey)
                    }
                )
            }
            .show()
    }
}
