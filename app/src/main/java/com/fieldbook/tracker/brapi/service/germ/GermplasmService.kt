package com.fieldbook.tracker.brapi.service.germ

import com.fieldbook.tracker.brapi.service.BrapiV2ApiCallBack
import com.fieldbook.tracker.brapi.service.Fetcher
import com.fieldbook.tracker.brapi.service.core.ApiFailCallback
import com.fieldbook.tracker.brapi.service.core.ApiListSuccess
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.brapi.client.v2.model.exceptions.ApiException
import org.brapi.client.v2.model.queryParams.germplasm.GermplasmQueryParams
import org.brapi.client.v2.model.queryParams.phenotype.ObservationUnitQueryParams
import org.brapi.client.v2.model.queryParams.phenotype.VariableQueryParams
import org.brapi.client.v2.modules.germplasm.GermplasmApi
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi
import org.brapi.client.v2.modules.phenotype.ObservationVariablesApi
import org.brapi.v2.model.germ.BrAPIGermplasm
import org.brapi.v2.model.germ.response.BrAPIGermplasmListResponse
import org.brapi.v2.model.pheno.BrAPIObservationUnit
import org.brapi.v2.model.pheno.BrAPIObservationVariable
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitListResponse
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableListResponse

interface GermplasmService {

    fun fetchGermplasm(
        params: GermplasmQueryParams,
        onSuccess: ApiListSuccess<BrAPIGermplasmListResponse>,
        onFail: ApiFailCallback
    )

    fun fetchAll(
        params: GermplasmQueryParams
    ): Flow<Any>

    class Default(private val api: GermplasmApi) : GermplasmService {

        override fun fetchGermplasm(
            params: GermplasmQueryParams,
            onSuccess: ApiListSuccess<BrAPIGermplasmListResponse>,
            onFail: ApiFailCallback
        ) {
            api.germplasmGetAsync(
                params,
                object : BrapiV2ApiCallBack<BrAPIGermplasmListResponse>() {
                    override fun onSuccess(
                        result: BrAPIGermplasmListResponse?,
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
        override fun fetchAll(params: GermplasmQueryParams): Flow<Any> =
            channelFlow {

                Fetcher<BrAPIGermplasm, GermplasmQueryParams, BrAPIGermplasmListResponse>().fetchAll(
                    params,
                    api::germplasmGetAsync
                ).collect { models ->

                    trySend(models)

                }

                awaitClose()
            }
    }
}