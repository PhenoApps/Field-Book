package com.fieldbook.tracker.brapi.service.pheno

import com.fieldbook.tracker.brapi.service.BrapiV2ApiCallBack
import com.fieldbook.tracker.activities.brapi.io.sync.BrapiException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.brapi.client.v2.model.exceptions.ApiException
import org.brapi.client.v2.model.queryParams.phenotype.ObservationQueryParams
import org.brapi.client.v2.modules.phenotype.ObservationsApi
import org.brapi.v2.model.pheno.BrAPIObservation
import org.brapi.v2.model.pheno.response.BrAPIObservationListResponse
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ObservationService {

    /**
     * @param total the server's count of observations matching the query, null if it didn't report one
     * @param sample the first matching observation, so callers can check the server applied their filters
     */
    data class Count(val total: Int?, val sample: BrAPIObservation?)

    /**
     * Requests a single observation to read the total from the pagination metadata,
     * rather than downloading every observation to count them.
     *
     * @param params page and pageSize will be overwritten
     */
    suspend fun count(params: ObservationQueryParams): Count

    class Default(private val api: ObservationsApi) : ObservationService {

        override suspend fun count(params: ObservationQueryParams): Count =
            suspendCancellableCoroutine { continuation ->

                params.page(0).pageSize(1)

                try {
                    val call = api.observationsGetAsync(
                        params,
                        object : BrapiV2ApiCallBack<BrAPIObservationListResponse>() {
                            override fun onSuccess(
                                result: BrAPIObservationListResponse?,
                                statusCode: Int,
                                responseHeaders: MutableMap<String, MutableList<String>>?
                            ) {
                                if (continuation.isActive) {
                                    continuation.resume(
                                        Count(
                                            total = result?.metadata?.pagination?.totalCount,
                                            sample = result?.result?.data?.firstOrNull()
                                        )
                                    )
                                }
                            }

                            override fun onFailure(
                                error: ApiException,
                                statusCode: Int,
                                responseHeaders: Map<String, List<String>>?
                            ) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(BrapiException(error.code))
                                }
                            }
                        })

                    //a count that's no longer needed shouldn't hold a connection until the server answers
                    continuation.invokeOnCancellation { call.cancel() }

                } catch (e: ApiException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(BrapiException(e.code))
                    }
                }
            }
    }
}
