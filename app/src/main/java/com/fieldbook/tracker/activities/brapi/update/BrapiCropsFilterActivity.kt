package com.fieldbook.tracker.activities.brapi.update

import android.content.Context
import android.content.Intent
import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.brapi.client.v2.model.exceptions.ApiException
import org.brapi.client.v2.model.queryParams.core.BrAPIQueryParams
import org.brapi.client.v2.model.queryParams.core.ProgramQueryParams
import org.brapi.v2.model.BrAPIPagination
import org.brapi.v2.model.core.response.BrAPICommonCropNamesResponse
import java.lang.reflect.Type

open class BrapiCropsFilterActivity(override val titleResId: Int = R.string.brapi_filter_type_crop) :
    BrapiFilterActivity<String, BrAPIQueryParams, BrAPICommonCropNamesResponse>() {

    companion object {

        const val FILTER_NAME = "$PREFIX.cropDbIds"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiCropsFilterActivity::class.java)
        }
    }

    override val filterName: String
        get() = FILTER_NAME


    override suspend fun queryByPage(params: BrAPIQueryParams): Flow<Pair<BrAPIPagination, List<String>>?> = callbackFlow {

        try {

            (brapiService as BrAPIServiceV2).fetchCrops(paginationManager, { response ->

                response.validateResponse { pagination, result ->

                    trySend(pagination to result.data.filterIsInstance<String>())
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

    override fun getQueryParams(): BrAPIQueryParams = ProgramQueryParams()

    override fun getTypeToken(): Type =
        TypeToken.getParameterized(List::class.java, String::class.java).type

    override fun List<String?>.mapToUiModel() = filterNotNull().map { crop ->
        CheckboxListAdapter.Model(
            checked = false,
            id = crop,
            label = crop,
            subLabel = ""
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
