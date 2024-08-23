package com.fieldbook.tracker.brapi.service.core

import com.fieldbook.tracker.brapi.service.BrapiV2ApiCallBack
import org.brapi.client.v2.model.exceptions.ApiException
import org.brapi.client.v2.model.queryParams.core.StudyQueryParams
import org.brapi.client.v2.modules.core.StudiesApi
import org.brapi.v2.model.core.response.BrAPIStudyListResponse

interface StudyService {

    fun fetchStudies(
        params: StudyQueryParams,
        onSuccess: ApiListSuccess<BrAPIStudyListResponse>,
        onFail: ApiFailCallback
    )

    class Default(private val api: StudiesApi) : StudyService {

        override fun fetchStudies(
            params: StudyQueryParams,
            onSuccess: ApiListSuccess<BrAPIStudyListResponse>,
            onFail: ApiFailCallback
        ) {
            api.studiesGetAsync(params, object : BrapiV2ApiCallBack<BrAPIStudyListResponse>() {
                override fun onSuccess(
                    result: BrAPIStudyListResponse?,
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