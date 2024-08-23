package com.fieldbook.tracker.brapi.service.core

import com.fieldbook.tracker.brapi.service.BrapiV2ApiCallBack
import org.brapi.client.v2.model.exceptions.ApiException
import org.brapi.client.v2.model.queryParams.core.SeasonQueryParams
import org.brapi.client.v2.modules.core.SeasonsApi
import org.brapi.v2.model.core.response.BrAPISeasonListResponse

interface SeasonService {
    fun fetchSeasons(params: SeasonQueryParams, onSuccess: ApiListSuccess<BrAPISeasonListResponse>, onFail: ApiFailCallback)

    class Default(private val api: SeasonsApi) : SeasonService {
        override fun fetchSeasons(
            params: SeasonQueryParams,
            onSuccess: ApiListSuccess<BrAPISeasonListResponse>,
            onFail: ApiFailCallback
        ) {
            api.seasonsGetAsync(params, object : BrapiV2ApiCallBack<BrAPISeasonListResponse>() {
                override fun onSuccess(result: BrAPISeasonListResponse?, i: Int, map: MutableMap<String, MutableList<String>>?) {
                    onSuccess(result)
                }

                override fun onFailure(error: ApiException, i: Int, map: Map<String, List<String>>) {
                    onFail(error.code)
                }
            })
        }
    }
}