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
import org.brapi.client.v2.model.queryParams.core.SeasonQueryParams
import org.brapi.v2.model.core.BrAPISeason
import org.brapi.v2.model.core.response.BrAPISeasonListResponse
import java.lang.reflect.Type

open class BrapiSeasonsFilterActivity(override val titleResId: Int = R.string.brapi_filter_type_season) :
    BrapiFilterActivity<BrAPISeason, SeasonQueryParams, BrAPISeasonListResponse>() {

    companion object {

        const val FILTER_NAME = "$PREFIX.seasonDbIds"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiSeasonsFilterActivity::class.java)
        }
    }

    override suspend fun queryByPage(params: SeasonQueryParams) = callbackFlow {

        try {

            (brapiService as BrAPIServiceV2).fetchSeasons(params, { response ->

                response.validateResponse { pagination, result ->

                    trySend(pagination to result.data.filterIsInstance<BrAPISeason>())

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

    override fun getQueryParams() = SeasonQueryParams()

    override val filterName: String
        get() = FILTER_NAME

    override fun getTypeToken(): Type =
        TypeToken.getParameterized(List::class.java, BrAPISeason::class.java).type

    override fun List<BrAPISeason?>.mapToUiModel(): List<CheckboxListAdapter.Model> =
        filterNotNull().map { season ->
            CheckboxListAdapter.Model(
                checked = false,
                id = season.seasonDbId,
                label = season.seasonName ?: season.seasonDbId,
                subLabel = season.year?.toString() ?: String()
            )
        }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menu?.findItem(R.id.action_check_all)?.isVisible = true
        menu?.findItem(R.id.action_reset_cache)?.isVisible = true
        menu?.findItem(R.id.action_brapi_filter)?.isVisible = false
        return true
    }
}
