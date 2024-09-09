package com.fieldbook.tracker.brapi.service

import android.util.Pair
import androidx.arch.core.util.Function
import org.brapi.v2.model.core.BrAPIProgram
import org.brapi.v2.model.core.BrAPIStudy
import org.brapi.v2.model.core.BrAPITrial
import org.brapi.v2.model.core.request.BrAPIProgramSearchRequest
import org.brapi.v2.model.core.request.BrAPIStudySearchRequest
import org.brapi.v2.model.core.request.BrAPITrialSearchRequest
import org.brapi.v2.model.core.response.BrAPIProgramListResponse
import org.brapi.v2.model.core.response.BrAPIStudyListResponse
import org.brapi.v2.model.core.response.BrAPITrialListResponse
import java.util.function.BiConsumer

//TODO documetnation
class Searcher(private val service: BrAPIServiceV2) : BrAPIServiceSearch {

    override fun searchStudies(
        body: BrAPIStudySearchRequest?,
        failFunction: Function<Int?, Void?>?
    ): Pair<Int, Map<String?, BrAPIStudy?>?> {

        val studyMapper =
            BiConsumer { data: List<*>, map: MutableMap<String?, BrAPIStudy?> ->
                data.forEach { item: Any? ->
                    if (item is BrAPIStudy) {
                        map[item.studyDbId] = item
                    }
                }
            }

        return service.executeBrapiSearchByPage<BrAPIStudySearchRequest, BrAPIStudyListResponse, BrAPIStudy>(
            { request: BrAPIStudySearchRequest? ->
                service.studiesApi.searchStudiesPost(
                    request
                )
            },  // Using lambda for explicit type
            { searchResultsDbId: String?, page: Int?, pageSize: Int? ->
                service.studiesApi.searchStudiesSearchResultsDbIdGet(
                    searchResultsDbId,
                    page,
                    pageSize
                )
            },  // Using lambda for explicit type
            body,
            studyMapper,
            failFunction
        )
    }
}