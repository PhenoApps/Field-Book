package com.fieldbook.tracker.activities.brapi.io.filter.filterer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.io.BrapiCacheModel
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterCache
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterTypeAdapter
import com.fieldbook.tracker.activities.brapi.io.BrapiStudyImportActivity
import com.fieldbook.tracker.activities.brapi.io.TrialStudyModel
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiCropsFilterActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiProgramFilterActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiSeasonsFilterActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiSeasonsFilterActivity.Companion.filterByProgramAndTrial
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiSubFilterListActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiTrialsFilterActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiTrialsFilterActivity.Companion.filterByProgram
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.preferences.GeneralKeys
import dagger.hilt.android.AndroidEntryPoint
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
        fun getIntent(context: Context) = Intent(context, BrapiStudyFilterActivity::class.java)
    }

    var isFilterMode = false

    enum class FilterChoice {
        PROGRAM,
        SEASON,
        TRIAL,
        CROP
    }

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

        val result = this.also {
            it.studies =
                it.studies.filter { if (programDbIds.isNotEmpty()) it.programDbId in programDbIds else true }
                    .filter { if (trialDbIds.isNotEmpty()) it.trialDbId in trialDbIds else true }
                    .filter { if (seasonDbIds.isNotEmpty()) it.study.seasons.any { s -> s in seasonDbIds } else true }
                    .filter { if (commonCropNames.isNotEmpty()) it.study.commonCropName in commonCropNames else true }
        }

        return result
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
        val brapiIds = database.allFieldObjects.filter { it.studyId >= 0 }.map { it.studyDbId }
        return filter { it.id !in brapiIds }
    }

    override fun onSearchTextComplete(searchText: String) {

        prefs.getStringSet("${filterName}${GeneralKeys.LIST_FILTER_TEXTS}", setOf())?.let { texts ->
            prefs.edit().putStringSet(
                "${filterName}${GeneralKeys.LIST_FILTER_TEXTS}",
                texts.plus(searchText)
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

    override fun BrapiCacheModel.mapToUiModel() = studies.map { model ->
        CheckboxListAdapter.Model(
            checked = false,
            id = model.study.studyDbId,
            label = model.study.studyName,
            subLabel = model.trialName ?: model.study.locationName ?: ""
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        menu?.findItem(R.id.action_check_all)?.isVisible = isFilterMode
        menu?.findItem(R.id.action_reset_cache)?.isVisible = !isFilterMode
        menu?.findItem(R.id.action_brapi_filter)?.isVisible = !isFilterMode
        selectionMenuItem = menu?.findItem(R.id.action_clear_selection)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed();
            return true
        } else if (item.itemId == R.id.action_brapi_filter) {
            showFilterChoiceDialog()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onFinishButtonClicked() {

        val models = associateProgramStudy()
        val programDbIds = models.map { it.programDbId }.distinct()

        if (isFilterMode) {
            saveFilter()
            finish()
            return
        }

        if (programDbIds.isNotEmpty()) {
            studyImportLauncher.launch(BrapiStudyImportActivity.getIntent(this).also { intent ->
                intent.putExtra(
                    BrapiStudyImportActivity.EXTRA_STUDY_DB_IDS,
                    models.map { it.study.studyDbId }.toTypedArray()
                )
                intent.putExtra(
                    BrapiStudyImportActivity.EXTRA_PROGRAM_DB_ID,
                    programDbIds.first()
                )
            })
        }
    }

    private fun associateProgramStudy(): List<TrialStudyModel> {

        val models = arrayListOf<TrialStudyModel>()

        val checkedIds = (recyclerView.adapter as CheckboxListAdapter).selected.map { it.id }
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

        val models = BrapiFilterCache.getStoredModels(this).studies
        val programCount = models.mapNotNull { it.programDbId }.distinct().size
        val trialCount = models.filterByProgram(pids, seasons).mapNotNull { it.trialDbId }.distinct().size
        val seasonCount = models.filterByProgramAndTrial(pids, tids)
            .map { it.study.seasons ?: listOf() }.flatten().filterNotNull().distinct().size
        val cropCount = models.map { it.study.commonCropName }.distinct().size

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_brapi_filter_choices_title)
            .setItems(
                arrayOf(
                    getString(R.string.brapi_filter_type_program_count, "$programCount"),
                    getString(R.string.brapi_filter_type_season_count, "$seasonCount"),
                    getString(R.string.brapi_filter_type_trial_count, "$trialCount"),
                    getString(R.string.brapi_filter_type_crop_count, "$cropCount")
                )
            ) { _, which ->
                intentLauncher.launch(
                    when (which) {
                        FilterChoice.PROGRAM.ordinal -> BrapiProgramFilterActivity.getIntent(this)
                        FilterChoice.SEASON.ordinal -> BrapiSeasonsFilterActivity.getIntent(this)
                        FilterChoice.CROP.ordinal -> BrapiCropsFilterActivity.getIntent(this)
                        else -> BrapiTrialsFilterActivity.getIntent(this)
                    }.also {
                        it.putExtra(EXTRA_FILTER_ROOT, defaultRootFilterKey)
                    }
                )
            }
            .show()
    }
}
