package com.fieldbook.tracker.brapi.service.core

import com.fieldbook.tracker.brapi.service.BrapiV2ApiCallBack
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
    }
}