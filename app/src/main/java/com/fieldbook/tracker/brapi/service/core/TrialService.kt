package com.fieldbook.tracker.brapi.service.core

import com.fieldbook.tracker.brapi.service.BrapiV2ApiCallBack
import org.brapi.client.v2.model.exceptions.ApiException
import org.brapi.client.v2.model.queryParams.core.TrialQueryParams
import org.brapi.client.v2.modules.core.TrialsApi
import org.brapi.v2.model.core.response.BrAPITrialListResponse

interface TrialService {

    fun fetchTrials(
        params: TrialQueryParams,
        onSuccess: ApiListSuccess<BrAPITrialListResponse>,
        onFail: ApiFailCallback
    )

    class Default(private val api: TrialsApi) : TrialService {

        override fun fetchTrials(
            params: TrialQueryParams,
            onSuccess: ApiListSuccess<BrAPITrialListResponse>,
            onFail: ApiFailCallback
        ) {
            api.trialsGetAsync(params, object : BrapiV2ApiCallBack<BrAPITrialListResponse>() {
                override fun onSuccess(
                    result: BrAPITrialListResponse?,
                    i: Int,
                    map: MutableMap<String, MutableList<String>>?
                ) {
                    onSuccess(result)
                }

                override fun onFailure(
                    error: ApiException,
                    i: Int,
                    map: Map<String, List<String>>
                ) {
                    onFail(error.code)
                }
            })
        }
    }
}