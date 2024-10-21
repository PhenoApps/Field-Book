package com.fieldbook.tracker.brapi.service.core

import com.fieldbook.tracker.brapi.service.BrapiV2ApiCallBack
import com.fieldbook.tracker.brapi.service.Fetcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.brapi.client.v2.model.exceptions.ApiException
import org.brapi.client.v2.model.queryParams.core.TrialQueryParams
import org.brapi.client.v2.modules.core.TrialsApi
import org.brapi.v2.model.core.BrAPITrial
import org.brapi.v2.model.core.response.BrAPITrialListResponse

interface TrialService {

    fun fetchTrials(
        params: TrialQueryParams,
        onSuccess: ApiListSuccess<BrAPITrialListResponse>,
        onFail: ApiFailCallback
    )

    fun fetchAll(params: TrialQueryParams): Flow<Any>

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

        override fun fetchAll(params: TrialQueryParams): Flow<Any> =
            channelFlow {

                Fetcher<BrAPITrial, TrialQueryParams, BrAPITrialListResponse>().fetchAll(
                    params,
                    api::trialsGetAsync
                )
                    .collect { models ->

                        if (models == null) {
                            close()
                            return@collect
                        }

                        trySend(models)

                }

                awaitClose()
            }
    }
}