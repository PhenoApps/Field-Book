package com.fieldbook.tracker.brapi.service.pheno

import com.fieldbook.tracker.brapi.service.BrapiV2ApiCallBack
import com.fieldbook.tracker.brapi.service.Fetcher
import com.fieldbook.tracker.brapi.service.core.ApiFailCallback
import com.fieldbook.tracker.brapi.service.core.ApiListSuccess
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.brapi.client.v2.model.exceptions.ApiException
import org.brapi.client.v2.model.queryParams.phenotype.VariableQueryParams
import org.brapi.client.v2.modules.phenotype.ObservationVariablesApi
import org.brapi.v2.model.pheno.BrAPIObservationVariable
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableListResponse

interface ObservationVariableService {

    fun fetchObservationVariables(
        params: VariableQueryParams,
        onSuccess: ApiListSuccess<BrAPIObservationVariableListResponse>,
        onFail: ApiFailCallback
    )

    fun fetchAll(
        params: VariableQueryParams
    ): Flow<Any>

    class Default(private val api: ObservationVariablesApi) : ObservationVariableService {

        override fun fetchObservationVariables(
            params: VariableQueryParams,
            onSuccess: ApiListSuccess<BrAPIObservationVariableListResponse>,
            onFail: ApiFailCallback
        ) {
            api.variablesGetAsync(
                params,
                object : BrapiV2ApiCallBack<BrAPIObservationVariableListResponse>() {
                    override fun onSuccess(
                        result: BrAPIObservationVariableListResponse?,
                        statusCode: Int,
                        responseHeaders: MutableMap<String, MutableList<String>>?
                    ) {
                        onSuccess(result)
                    }

                    override fun onFailure(
                        error: ApiException,
                        statusCode: Int,
                        responseHeaders: Map<String, List<String>>
                    ) {
                        onFail(error.code)
                    }
                })
        }

        /**
         * @param queryParams page and pageSize will be overwritten to query all data
         */
        override fun fetchAll(params: VariableQueryParams): Flow<Any> =
            channelFlow {

                Fetcher<BrAPIObservationVariable, VariableQueryParams, BrAPIObservationVariableListResponse>().fetchAll(
                    params,
                    api::variablesGetAsync
                ).collect { models ->

                    trySend(models)

                }

                awaitClose()
            }
    }
}