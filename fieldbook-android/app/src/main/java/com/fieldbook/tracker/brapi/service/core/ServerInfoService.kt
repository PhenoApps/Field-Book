package com.fieldbook.tracker.brapi.service.core

import com.fieldbook.tracker.brapi.service.BrapiV2ApiCallBack
import org.brapi.client.v2.model.exceptions.ApiException
import org.brapi.client.v2.modules.core.ServerInfoApi
import org.brapi.v2.model.core.response.BrAPIServerInfoResponse

interface ServerInfoService {

    fun fetchServerInfo(
        onSuccess: ApiListSuccess<BrAPIServerInfoResponse>,
        onFail: ApiFailCallback
    )

    class Default(private val api: ServerInfoApi) : ServerInfoService {

        override fun fetchServerInfo(
            onSuccess: ApiListSuccess<BrAPIServerInfoResponse>,
            onFail: ApiFailCallback
        ) {
            api.serverinfoGetAsync(null, object : BrapiV2ApiCallBack<BrAPIServerInfoResponse>() {
                override fun onSuccess(
                    result: BrAPIServerInfoResponse?,
                    statusCode: Int,
                    responseHeaders: MutableMap<String, MutableList<String>>?
                ) {
                    result?.let { onSuccess(it) }
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
    }
}