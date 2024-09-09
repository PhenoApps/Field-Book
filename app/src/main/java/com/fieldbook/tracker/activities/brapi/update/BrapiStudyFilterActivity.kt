package com.fieldbook.tracker.activities.brapi.update

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.annotation.OptIn
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.dialogs.BrapiFilterBottomSheetDialog
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brapi.client.v2.model.queryParams.core.StudyQueryParams
import org.brapi.client.v2.model.queryParams.core.TrialQueryParams
import org.brapi.v2.model.core.BrAPIProgram
import org.brapi.v2.model.core.BrAPIStudy
import org.brapi.v2.model.core.BrAPITrial
import java.io.File

class BrapiStudyFilterActivity(
    override val filterName: String = "$PREFIX.studies",
    override val titleResId: Int = R.string.brapi_studies_filter_title
) : BrapiListFilterActivity<BrAPIStudy>() {

    enum class FilterChoice {
        PROGRAM,
        SEASON,
        TRIAL,
        CROP
    }

    private var trialModels = mutableListOf<BrAPITrial>()

    private var numFilterBadge: BadgeDrawable? = null

    private var brapiFilterDialog: BrapiFilterBottomSheetDialog? = null

    override fun onResume() {
        super.onResume()
        resetChipsUi()
        loadData()
    }

    private var queryStudiesJob: Job? = null
    private fun loadData() {

        launch(Dispatchers.Main) {

            toggleProgressBar(View.VISIBLE)

            fetchDescriptionTv.text = "Loading trials, please wait."
            progressBar.visibility = View.VISIBLE

            queryTrialsJob = async(Dispatchers.IO) {

                var count = 0

                (brapiService as BrAPIServiceV2).trialService.fetchAll(TrialQueryParams())
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

                        if (total == trialModels.size) {
                            queryTrialsJob?.cancel()
                        }

                    }
            }

            queryTrialsJob?.join()

            fetchDescriptionTv.text = "Loading studies, please wait."
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0

            queryStudiesJob = launch(Dispatchers.IO) {

                var count = 0

                val modelCache = arrayListOf<TrialStudyModel>()

                loadListData().collect {

                    var (totalCount, models) = it as Pair<*, *>
                    totalCount = totalCount as Int
                    models = models as List<*>
                    models = (models as List<*>).map { it as BrAPIStudy }

                    count += (models as List<TrialStudyModel>).size

                    modelCache.addAll(models as List<TrialStudyModel>)

                    cacheAndSubmitItems(models as List<TrialStudyModel>)

                    Log.d("CacheSize", count.toString())
                    withContext(Dispatchers.Main) {
                        setProgress(count, totalCount)
                        if (count == totalCount) {
                            progressBar.visibility = View.GONE
                            fetchDescriptionTv.visibility = View.GONE
                            saveCacheToFile(modelCache as List<BrAPIStudy>, trialModels)
                            queryStudiesJob?.cancel()
                        }
                    }
                }

            }

            queryStudiesJob?.join()

            fetchDescriptionTv.text = "Complete"

            toggleProgressBar(View.INVISIBLE)

        }
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

                    resetChipsUi()

                    performQueryWithProgress()
                }
            })

        setupMainToolbar()

        applyTextView.text = getString(R.string.act_brapi_filter_import)

//        launch(Dispatchers.IO) {
//            queryAllTrials()
//        }
    }

    @OptIn(ExperimentalBadgeUtils::class)
    private fun resetChipsUi() {

        val toolbar = findViewById<MaterialToolbar>(R.id.act_brapi_importer_tb)

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

    private fun getStoredProgramModels(): List<BrAPIProgram> {

        externalCacheDir?.let { cacheDir ->
            val file = File(cacheDir, "${BrapiProgramFilterActivity.FILTER_NAME}.json")
            if (file.exists()) {
                val json = file.readText()
                return Gson().fromJson(json,
                    TypeToken.getParameterized(List::class.java, BrAPIProgram::class.java).type
                )
            }
        }
        return listOf()
    }

    private fun getStoredTrialModels(): List<BrAPITrial> {

        externalCacheDir?.let { cacheDir ->
            val file = File(cacheDir, "${BrapiTrialsFilterActivity.FILTER_NAME}.json")
            if (file.exists()) {
                val json = file.readText()
                return Gson().fromJson(json,
                    TypeToken.getParameterized(List::class.java, BrAPITrial::class.java).type
                )
            }
        }
        return listOf()
    }

    private fun getNumberOfFilters() =
        (BrapiFilterTypeAdapter.toModelList(prefs, BrapiProgramFilterActivity.FILTER_NAME) +
                BrapiFilterTypeAdapter.toModelList(prefs, BrapiTrialsFilterActivity.FILTER_NAME) +
                BrapiFilterTypeAdapter.toModelList(prefs, BrapiSeasonsFilterActivity.FILTER_NAME) +
                BrapiFilterTypeAdapter.toModelList(prefs, BrapiCropsFilterActivity.FILTER_NAME))
            .filter { it.checked }.size

    private fun getIds(filterName: String) =
        BrapiFilterTypeAdapter.toModelList(prefs, filterName).map { it.id }

//    override fun List<BrAPIStudy>.filterByPreferences(): List<BrAPIStudy> {
//
//        val programModels = getStoredProgramModels()
//        //val trialModels = getStoredTrialModels()
//
//        val programDbIds = getIds(BrapiProgramFilterActivity.FILTER_NAME)
//        val trialDbIds = getIds(BrapiTrialsFilterActivity.FILTER_NAME)
//        val seasonDbIds = getIds(BrapiSeasonsFilterActivity.FILTER_NAME)
//        val commonCropNames = getIds(BrapiCropsFilterActivity.FILTER_NAME)
//
//        return this
//            .filter { study ->
//
//                if (programDbIds.isNotEmpty()) {
//
//                    trialModels.filter { it.programDbId in programDbIds }.any { it.trialDbId == study.trialDbId }
//
//                } else true
//            }
//            .filter { if (trialDbIds.isNotEmpty()) it.trialDbId in trialDbIds else true }
//            .filter { if (seasonDbIds.isNotEmpty()) it.seasons.any { it in seasonDbIds } else true }
//            .filter { if (commonCropNames.isNotEmpty()) it.commonCropName in commonCropNames else true }
//    }

    override fun showNextButton() = cache.any { it.checked }

//    override fun getQueryParams() = StudyQueryParams()

//    override fun List<BrAPIStudy?>.mapToUiModel() = filterNotNull().map { study ->
//        CheckboxListAdapter.Model(
//            checked = false,
//            id = study.studyDbId,
//            label = study.studyName,
//            subLabel = study.locationName
//        )
//    }

    private fun setupMainToolbar() {

        supportActionBar?.title = getString(R.string.import_brapi_title)

        //enable home button as back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

    }

    //add menu to toolbar
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_filter_brapi, menu)
        menu?.findItem(R.id.action_check_all)?.isVisible = false
        menu?.findItem(R.id.action_reset_cache)?.isVisible = cache.isNotEmpty()
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

    var queryTrialsJob: Job? = null

    override suspend fun loadListData(): Flow<Any> = (brapiService as BrAPIServiceV2).studyService.fetchAll(
        StudyQueryParams())

    override fun List<BrAPIStudy?>.mapToUiModel() = filterNotNull().map { study ->
        CheckboxListAdapter.Model(
            checked = false,
            id = study.studyDbId,
            label = study.studyName,
            subLabel = study.locationName
        )
    }

}
