package com.fieldbook.tracker.brapi.service.pheno

import com.fieldbook.tracker.brapi.service.BrapiV2ApiCallBack
import com.fieldbook.tracker.brapi.service.Fetcher
import com.fieldbook.tracker.brapi.service.core.ApiFailCallback
import com.fieldbook.tracker.brapi.service.core.ApiListSuccess
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.brapi.client.v2.model.exceptions.ApiException
import org.brapi.client.v2.model.queryParams.phenotype.ObservationUnitQueryParams
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi
import org.brapi.v2.model.pheno.BrAPIObservationUnit
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitListResponse

interface ObservationUnitService {

    fun fetchObservationUnits(
        params: ObservationUnitQueryParams,
        onSuccess: ApiListSuccess<BrAPIObservationUnitListResponse>,
        onFail: ApiFailCallback
    )

    fun fetchAll(
        params: ObservationUnitQueryParams
    ): Flow<Any>

    class Default(private val api: ObservationUnitsApi) : ObservationUnitService {

        override fun fetchObservationUnits(
            params: ObservationUnitQueryParams,
            onSuccess: ApiListSuccess<BrAPIObservationUnitListResponse>,
            onFail: ApiFailCallback
        ) {
            api.observationunitsGetAsync(
                params,
                object : BrapiV2ApiCallBack<BrAPIObservationUnitListResponse>() {
                    override fun onSuccess(
                        result: BrAPIObservationUnitListResponse?,
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
        override fun fetchAll(params: ObservationUnitQueryParams): Flow<Any> =
            channelFlow {

                Fetcher<BrAPIObservationUnit, ObservationUnitQueryParams, BrAPIObservationUnitListResponse>().fetchAll(
                    params,
                    api::observationunitsGetAsync
                ).collect { models ->

                    trySend(models)

                }

                awaitClose()
            }
    }
}