package com.fieldbook.tracker.brapi.service.core

import com.fieldbook.tracker.brapi.service.BrapiV2ApiCallBack
import com.fieldbook.tracker.brapi.service.Fetcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.brapi.client.v2.model.exceptions.ApiException
import org.brapi.client.v2.model.queryParams.core.ProgramQueryParams
import org.brapi.client.v2.modules.core.ProgramsApi
import org.brapi.v2.model.core.response.BrAPIProgramListResponse

interface ProgramService {

    fun fetchPrograms(
        params: ProgramQueryParams,
        onSuccess: ApiListSuccess<BrAPIProgramListResponse>,
        onFail: ApiFailCallback
    )

    fun fetchAll(params: ProgramQueryParams): Flow<Any>

    class Default(private val api: ProgramsApi) : ProgramService {

        override fun fetchPrograms(
            params: ProgramQueryParams,
            onSuccess: ApiListSuccess<BrAPIProgramListResponse>,
            onFail: ApiFailCallback
        ) {
            api.programsGetAsync(params, object : BrapiV2ApiCallBack<BrAPIProgramListResponse>() {
                override fun onSuccess(
                    result: BrAPIProgramListResponse?,
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

        override fun fetchAll(params: ProgramQueryParams): Flow<Any> =
            channelFlow {

                Fetcher<ProgramQueryParams, BrAPIProgramListResponse>().fetchAll(
                    params,
                    api::programsGetAsync
                ).collect { models ->

                    trySend(models)

                }

                awaitClose()
            }
    }
}