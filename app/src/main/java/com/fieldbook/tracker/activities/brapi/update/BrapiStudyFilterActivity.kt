package com.fieldbook.tracker.activities.brapi.update

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
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
import io.swagger.client.ApiException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.brapi.client.v2.model.queryParams.core.StudyQueryParams
import org.brapi.v2.model.BrAPIPagination
import org.brapi.v2.model.core.BrAPIProgram
import org.brapi.v2.model.core.BrAPIStudy
import org.brapi.v2.model.core.BrAPITrial
import org.brapi.v2.model.core.response.BrAPIStudyListResponse
import java.io.File

class BrapiStudyFilterActivity(
    override val filterName: String = "$PREFIX.studies",
    override val titleResId: Int = R.string.brapi_studies_filter_title
) : BrapiFilterActivity<BrAPIStudy, StudyQueryParams, BrAPIStudyListResponse>() {

    enum class FilterChoice {
        PROGRAM,
        SEASON,
        TRIAL,
        CROP
    }

    private var numFilterBadge: BadgeDrawable? = null

    private var brapiFilterDialog: BrapiFilterBottomSheetDialog? = null

    override fun onResume() {
        super.onResume()
        resetChipsUi()
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

                    resetChipsUi()
                    
                    cacheAndSubmitItems(getStoredModels())
                }
            })

        setupMainToolbar()

        applyTextView.text = getString(R.string.act_brapi_filter_import)

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

    override fun List<BrAPIStudy>.filterByPreferences(): List<BrAPIStudy> {

        val programModels = getStoredProgramModels()
        val trialModels = getStoredTrialModels()

        val programDbIds = getIds(BrapiProgramFilterActivity.FILTER_NAME)
        val trialDbIds = getIds(BrapiTrialsFilterActivity.FILTER_NAME)
        val seasonDbIds = getIds(BrapiSeasonsFilterActivity.FILTER_NAME)
        val commonCropNames = getIds(BrapiCropsFilterActivity.FILTER_NAME)

        return this
//            .filter { study ->
//
//                if (programDbIds.isNotEmpty()) {
//
//                    trialModels.filter { it.programDbId in programDbIds }.any { it.trialDbId == study.trialDbId }
//
//                } else true
//            }
            .filter { if (trialDbIds.isNotEmpty()) it.trialDbId in trialDbIds else true }
            .filter { if (seasonDbIds.isNotEmpty()) it.seasons.any { it in seasonDbIds } else true }
            .filter { if (commonCropNames.isNotEmpty()) it.commonCropName in commonCropNames else true }
    }

    override fun showNextButton() = cache.any { it.checked }

    override suspend fun queryByPage(params: StudyQueryParams): Flow<Pair<BrAPIPagination, List<BrAPIStudy>>?> = callbackFlow {

        try {

            (brapiService as BrAPIServiceV2).fetchStudies(params, { response ->

                response.validateResponse { pagination, result ->

                    trySend(pagination to result.data.filterIsInstance<BrAPIStudy>())

                }

            }) { failCode ->

                trySend(null)

                toggleProgressBar(View.INVISIBLE)

                showErrorCode(failCode)
            }

            awaitClose()

        } catch (e: ApiException) {
            e.printStackTrace()
        }
    }

    override fun getQueryParams() = StudyQueryParams()

    override fun List<BrAPIStudy?>.mapToUiModel() = filterNotNull().map { study ->
        CheckboxListAdapter.Model(
            checked = false,
            id = study.studyDbId,
            label = study.studyName,
            subLabel = study.locationName
        )
    }

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
}
