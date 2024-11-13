package com.fieldbook.tracker.activities.brapi.io.filter.filterer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.io.BrapiCacheModel
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiCropsFilterActivity
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterCache
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiSubFilterListActivity
import com.fieldbook.tracker.activities.brapi.io.BrapiTraitImporterActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiTrialsFilterActivity
import com.fieldbook.tracker.activities.brapi.io.TrialStudyModel
import com.fieldbook.tracker.activities.brapi.io.mapper.DataTypes
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.formats.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brapi.client.v2.model.queryParams.phenotype.VariableQueryParams
import org.brapi.v2.model.core.BrAPIStudy
import org.brapi.v2.model.pheno.BrAPIObservationVariable

class BrapiTraitFilterActivity(
    override val defaultRootFilterKey: String = FILTERER_KEY,
    override val filterName: String = "traits",
    override val titleResId: Int = R.string.brapi_traits_filter_title
) : BrapiSubFilterListActivity<BrAPIStudy>() {

    companion object {
        const val FILTER_NAME = "traits"
        const val FILTERER_KEY = "com.fieldbook.tracker.activities.brapi.io.filters.traits."
        fun getIntent(context: Context) = Intent(context, BrapiTraitFilterActivity::class.java)
    }

    enum class FilterChoice {
        TRIAL,
        STUDY,
        CROP
    }

    private var queryVariablesJob: Job? = null

    private var queried = false

    override fun BrapiCacheModel.filterByPreferences(): BrapiCacheModel {

        val studyDbIds = getIds("${defaultRootFilterKey}${BrapiStudyFilterActivity.FILTER_NAME}")
        val trialDbIds = getIds("${defaultRootFilterKey}${BrapiTrialsFilterActivity.FILTER_NAME}")
        val commonCropNames = getIds("${defaultRootFilterKey}${BrapiCropsFilterActivity.FILTER_NAME}")

        val observationVariableIds = this.studies
            .filter { if (studyDbIds.isNotEmpty()) it.study.studyDbId in studyDbIds else true }
            .filter { if (trialDbIds.isNotEmpty()) it.trialDbId in trialDbIds else true }
            .map { it.study.observationVariableDbIds ?: listOf() }.flatten()

        println(observationVariableIds.size)
        val filteredVars = this.variables.values
            .filter { if (observationVariableIds.isNotEmpty()) it.observationVariableDbId in observationVariableIds else true }
            .filter { if (commonCropNames.isNotEmpty()) it.commonCropName in commonCropNames else true}
            .associateBy { it.observationVariableDbId }
            .toMutableMap()

        return this.also {
            it.variables = filteredVars
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

    override fun BrapiCacheModel.mapToUiModel() =
        variables.values
            .filter { it.observationVariableDbId != null }
            .map { model ->
                CheckboxListAdapter.Model(
                    checked = false,
                    id = model.observationVariableDbId,
                    label = model.synonyms?.firstOrNull() ?: model.observationVariableName ?: model.observationVariableDbId,
                    subLabel = "${model.commonCropName ?: ""} ${model.observationVariableDbId ?: ""}"
                ).also {
                    model.scale?.dataType?.name?.let { dataType ->
                        Formats.findTrait(DataTypes.convertBrAPIDataType(dataType))?.iconDrawableResourceId?.let { icon ->
                            it.iconResId = icon
                        }
                    }
                }
            }.toList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chipGroup.visibility = View.VISIBLE

        setupMainToolbar()

        prefs.edit().remove(filterName).apply()

        importTextView.text = getString(R.string.act_brapi_filter_import)

        fetchDescriptionTv.text = getString(R.string.act_brapi_list_filter_loading_variables)

    }

    override fun onFinishButtonClicked() {
        saveFilter()
        BrapiTraitImporterActivity.getIntent(this).also {
           intentLauncher.launch(it.also {
               it.putStringArrayListExtra(BrapiTraitImporterActivity.EXTRA_TRAIT_DB_ID,
                   ArrayList(cache.filter { it.checked }.map { it.id })
               )
           })
        }
        finish()
    }

    override suspend fun loadData() {
        //super.loadData()

        if (!queried) {

            withContext(Dispatchers.Main) {
                fetchDescriptionTv.text = getString(R.string.act_brapi_list_filter_loading_variables)
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 0
            }

            queryVariablesJob = queryVariables()
            queryVariablesJob?.join()
        }
    }

    private suspend fun queryVariables() = launch(Dispatchers.IO) {

        val variables = arrayListOf<BrAPIObservationVariable>()

        try {

            var count = 0

            queried = true

            (brapiService as BrAPIServiceV2).observationVariableService.fetchAll(
                VariableQueryParams()
            )
                .catch {
                    onApiException()
                    queryVariablesJob?.cancel()
                }
                .collect {

                    var (totalCount, models) = it as Pair<*, *>
                    totalCount = totalCount as Int
                    models = models as List<*>
                    models = models.map { m -> m as BrAPIObservationVariable }

                    count += (models as List<*>).size

                    variables.addAll(models)

                    withContext(Dispatchers.Main) {
                        setProgress(variables.size, totalCount)
                        if (variables.size == totalCount || totalCount < 512) {
                            BrapiFilterCache.saveVariables(this@BrapiTraitFilterActivity, variables)
                            restoreModels()
                            cancel()
                        }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun hasData(): Boolean {
        return BrapiFilterCache.getStoredModels(this).variables.isNotEmpty() or queried
    }

    override fun showNextButton() = cache.any { it.checked }

    private fun setupMainToolbar() {

        supportActionBar?.title = getString(R.string.import_brapi_title)

        //enable home button as back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

    }

    //add menu to toolbar
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_filter_brapi, menu)
        menu?.findItem(R.id.action_check_all)?.isVisible = false
        menu?.findItem(R.id.action_reset_cache)?.isVisible = true
        menu?.findItem(R.id.action_brapi_filter)?.isVisible = true
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        } else if (item.itemId == R.id.action_brapi_filter) {
            showFilterChoiceDialog()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showFilterChoiceDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_brapi_filter_choices_title)
            .setItems(
                arrayOf(
                    getString(R.string.brapi_filter_type_trial),
                    getString(R.string.brapi_filter_type_study),
                    getString(R.string.brapi_filter_type_crop)
                )
            ) { _, which ->
                intentLauncher.launch(
                    when (which) {
                        FilterChoice.TRIAL.ordinal -> BrapiTrialsFilterActivity.getIntent(this)
                        FilterChoice.STUDY.ordinal -> BrapiStudyFilterActivity.getIntent(this).also {
                            it.putExtra(BrapiStudyFilterActivity.EXTRA_MODE, "filter")
                        }
                        else -> BrapiCropsFilterActivity.getIntent(this)
                    }.also {
                        it.putExtra(EXTRA_FILTER_ROOT, defaultRootFilterKey)
                    }
                )
            }
            .show()
    }
}
