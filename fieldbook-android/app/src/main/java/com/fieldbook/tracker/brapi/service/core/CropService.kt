package com.fieldbook.tracker.brapi.service.core

import com.fieldbook.tracker.brapi.service.BrapiPaginationManager
import com.fieldbook.tracker.brapi.service.BrapiV2ApiCallBack
import org.brapi.client.v2.model.exceptions.ApiException
import org.brapi.client.v2.modules.core.CommonCropNamesApi
import org.brapi.v2.model.core.response.BrAPICommonCropNamesResponse

interface CropService {
    fun fetchCrops(paginationManager: BrapiPaginationManager, onSuccess: (BrAPICommonCropNamesResponse) -> Void?, onFail: (Int) -> Void?)

    class Default(private val api: CommonCropNamesApi) : CropService {
        override fun fetchCrops(
            paginationManager: BrapiPaginationManager,
            onSuccess: (BrAPICommonCropNamesResponse) -> Void?,
            onFail: (Int) -> Void?
        ) {

            api.commoncropnamesGetAsync(paginationManager.page, paginationManager.pageSize, object : BrapiV2ApiCallBack<BrAPICommonCropNamesResponse>() {
                override fun onSuccess(response: BrAPICommonCropNamesResponse, i: Int, map: Map<String, List<String>>) {

                    if (response.metadata != null && response.metadata.pagination != null) {
                        if (response.metadata.pagination.totalPages != null) {
                            paginationManager.totalPages = response.metadata.pagination.totalPages
                        }
                    }

                    onSuccess(response)
                }

                override fun onFailure(
                    error: ApiException?,
                    i: Int,
                    map: MutableMap<String, MutableList<String>>?
                ) {
                    super.onFailure(error, i, map)
                    onFail(error?.code ?: 0)
                }
            })
        }
    }
}