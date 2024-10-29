package com.fieldbook.tracker.activities.brapi.io

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.preferences.GeneralKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brapi.client.v2.model.queryParams.phenotype.VariableQueryParams
import org.brapi.v2.model.core.BrAPIStudy
import org.brapi.v2.model.core.BrAPITrial
import org.brapi.v2.model.pheno.BrAPIObservationVariable

class BrapiTraitFilterActivity(
    override val filterName: String = "$PREFIX.traits",
    override val titleResId: Int = R.string.brapi_traits_filter_title
) : BrapiSubFilterListActivity<BrAPIStudy>() {

    companion object {
        fun getIntent(context: Context) = Intent(context, BrapiTraitFilterActivity::class.java)
    }

    enum class FilterChoice {
        TRIAL,
        STUDY,
        CROP
    }

    private var queryVariablesJob: Job? = null

    override fun List<TrialStudyModel>.filterByPreferences(): List<TrialStudyModel> {

        val studyDbIds = getIds(BrapiStudyFilterActivity.FILTER_NAME)
        val trialDbIds = getIds(BrapiTrialsFilterActivity.FILTER_NAME)
        val commonCropNames = getIds(BrapiCropsFilterActivity.FILTER_NAME)

        return this
            .filter { if (studyDbIds.isNotEmpty()) it.study.studyDbId in studyDbIds else true }
            .filter { if (trialDbIds.isNotEmpty()) it.trialDbId in trialDbIds else true }
            .filter { if (commonCropNames.isNotEmpty()) it.study.commonCropName in commonCropNames else true }
    }

    override fun List<CheckboxListAdapter.Model>.filterBySearchTextPreferences(): List<CheckboxListAdapter.Model> {

        val searchTexts = prefs.getStringSet(GeneralKeys.LIST_FILTER_TEXTS, emptySet())

        return if (searchTexts?.isEmpty() != false) this else filter { model ->
            searchTexts.map { it.lowercase() }.all { tokens ->
                tokens in model.label.lowercase() || tokens in model.subLabel.lowercase() || tokens in model.id.lowercase()
            }
        }
    }

    override fun onSearchTextComplete(searchText: String) {

        prefs.getStringSet(GeneralKeys.LIST_FILTER_TEXTS, setOf())?.let { texts ->
            prefs.edit().putStringSet(
                GeneralKeys.LIST_FILTER_TEXTS,
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

    override fun List<TrialStudyModel>.mapToUiModel() =
        asSequence().filter { it?.variables != null }.flatMap { it.variables!! }
            .filter { it.observationVariableDbId != null }
            .map { model ->
                CheckboxListAdapter.Model(
                    checked = false,
                    id = model.observationVariableDbId,
                    label = model.observationVariableName ?: model.observationVariableDbId,
                    subLabel = "${model.commonCropName ?: ""} ${model.observationVariableDbId ?: ""}"
                )
            }.toList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chipGroup.visibility = View.VISIBLE

        setupMainToolbar()

        prefs.edit().remove(filterName).apply()

        importTextView.text = getString(R.string.act_brapi_filter_import)

        fetchDescriptionTv.text = getString(R.string.act_brapi_list_filter_loading_variables)
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0

        launch {
            queryVariablesJob = queryVariables()
            queryVariablesJob?.join()
        }
    }

    private suspend fun queryVariablesForStudy(studyDbId: String? = null) {
        var count = 0

        val modelCache = arrayListOf<BrAPIObservationVariable>()

        (brapiService as BrAPIServiceV2).observationVariableService.fetchAll(
            VariableQueryParams().also {
                it.studyDbId(studyDbId)
            }
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

                modelCache.addAll(models)

                val uiModels = models.map { m ->
                    CheckboxListAdapter.Model(
                        checked = false,
                        id = m.observationVariableDbId,
                        label = m.observationVariableName ?: m.observationVariableDbId,
                        subLabel = m.commonCropName ?: m.observationVariableDbId
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
                        //progressBar.visibility = View.GONE
                        //fetchDescriptionTv.visibility = View.GONE
                        studyDbId?.let { id ->
                            BrapiFilterCache.saveVariablesToStudy(this@BrapiTraitFilterActivity, id, models)
                        }
                        //queryVariablesJob?.cancel()
                    }
                }
            }
    }

    private suspend fun queryVariables() = launch(Dispatchers.IO) {

        val storedModels = BrapiFilterCache.getStoredModels(this@BrapiTraitFilterActivity)
        if (storedModels.isEmpty()) {
            queryVariablesForStudy()
        } else storedModels.forEach {
            if (it.variables == null) {
                queryVariablesForStudy(it.study.studyDbId)
            }
        }

        withContext(Dispatchers.Main) {
            restoreModels()
        }
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
                    }
                )
            }
            .show()
    }
}
