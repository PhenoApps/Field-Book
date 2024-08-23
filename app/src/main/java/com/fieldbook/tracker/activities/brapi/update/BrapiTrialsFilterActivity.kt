package com.fieldbook.tracker.activities.brapi.update

import android.content.Context
import android.content.Intent
import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.google.gson.reflect.TypeToken
import io.swagger.client.ApiException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.brapi.client.v2.model.queryParams.core.TrialQueryParams
import org.brapi.v2.model.core.BrAPITrial
import org.brapi.v2.model.core.response.BrAPITrialListResponse
import java.lang.reflect.Type

open class BrapiTrialsFilterActivity(override val titleResId: Int = R.string.brapi_filter_type_trial) :
    BrapiFilterActivity<BrAPITrial, TrialQueryParams, BrAPITrialListResponse>() {

    companion object {

        const val FILTER_NAME = "$PREFIX.trialDbIds"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiTrialsFilterActivity::class.java)
        }
    }

    override suspend fun queryByPage(params: TrialQueryParams) = callbackFlow {

        try {

            (brapiService as BrAPIServiceV2).fetchTrials(params, { response ->

                response.validateResponse { pagination, result ->

                    trySend(pagination to result.data.filterIsInstance<BrAPITrial>())

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

    override fun getQueryParams() = TrialQueryParams()

    override val filterName: String
        get() = FILTER_NAME

    override fun List<BrAPITrial?>.mapToUiModel() = filterNotNull().map { trial ->
        CheckboxListAdapter.Model(
            checked = trial.trialDbId in cache.map { it.id },
            id = trial.trialDbId,
            label = trial.trialName,
            subLabel = trial.trialDescription
        )
    }

    override fun getTypeToken(): Type =
        TypeToken.getParameterized(List::class.java, BrAPITrial::class.java).type

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menu?.findItem(R.id.action_check_all)?.isVisible = true
        menu?.findItem(R.id.action_reset_cache)?.isVisible = true
        menu?.findItem(R.id.action_brapi_filter)?.isVisible = false
        return true
    }
}
