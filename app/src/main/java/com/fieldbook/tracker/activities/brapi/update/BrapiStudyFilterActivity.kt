package com.fieldbook.tracker.activities.brapi.update

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.dialogs.BrapiFilterBottomSheetDialog
import com.fieldbook.tracker.preferences.GeneralKeys
import org.brapi.v2.model.core.BrAPIStudy

class BrapiStudyFilterActivity(
    override val filterName: String = "$PREFIX.studies",
    override val titleResId: Int = R.string.brapi_studies_filter_title
) : BrapiSubFilterListActivity<BrAPIStudy>() {

    companion object {
        fun getIntent(context: Context) = Intent(context, BrapiStudyFilterActivity::class.java)
    }

    enum class FilterChoice {
        PROGRAM,
        SEASON,
        TRIAL,
        CROP
    }

    private var brapiFilterDialog: BrapiFilterBottomSheetDialog? = null

    override fun List<TrialStudyModel>.filterByPreferences(): List<TrialStudyModel> {

        val programDbIds = getIds(BrapiProgramFilterActivity.FILTER_NAME)
        val trialDbIds = getIds(BrapiTrialsFilterActivity.FILTER_NAME)
        val seasonDbIds = getIds(BrapiSeasonsFilterActivity.FILTER_NAME)
        val commonCropNames = getIds(BrapiCropsFilterActivity.FILTER_NAME)

        return this
            .filter { if (programDbIds.isNotEmpty()) it.programDbId in programDbIds else true }
            .filter { if (trialDbIds.isNotEmpty()) it.trialDbId in trialDbIds else true }
            .filter { if (seasonDbIds.isNotEmpty()) it.study.seasons.any { s -> s in seasonDbIds } else true }
            .filter { if (commonCropNames.isNotEmpty()) it.study.commonCropName in commonCropNames else true }
    }

    //TODO should text be or or and?
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

    override fun List<TrialStudyModel>.mapToUiModel() = map { model ->
        CheckboxListAdapter.Model(
            checked = false,
            id = model.study.studyDbId,
            label = model.study.studyName,
            subLabel = model.study.locationName
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        brapiFilterDialog = BrapiFilterBottomSheetDialog(
            prefs,
            object : BrapiFilterBottomSheetDialog.OnFilterSelectedListener {
                override fun onFilterSelected(filter: Int) {

                    intentLauncher.launch(
                        when (filter) {
                            FilterChoice.PROGRAM.ordinal -> BrapiProgramFilterActivity.getIntent(this@BrapiStudyFilterActivity)
                            FilterChoice.SEASON.ordinal -> BrapiSeasonsFilterActivity.getIntent(this@BrapiStudyFilterActivity)
                            FilterChoice.CROP.ordinal -> BrapiCropsFilterActivity.getIntent(this@BrapiStudyFilterActivity)
                            else -> BrapiTrialsFilterActivity.getIntent(this@BrapiStudyFilterActivity)
                        }
                    )
                }

                override fun onDismiss() {

                    cache.clear()

                    recyclerView.adapter?.notifyDataSetChanged()

                    restoreModels()

                    resetChipsUi()

                    //performQueryWithProgress()
                }
            })

        setupMainToolbar()

        applyTextView.text = getString(R.string.act_brapi_filter_import)

    }

    private fun getIds(filterName: String) =
        BrapiFilterTypeAdapter.toModelList(prefs, filterName).map { it.id }

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

            if (brapiFilterDialog?.isVisible != true) {
                brapiFilterDialog?.show(supportFragmentManager, "filter")
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
