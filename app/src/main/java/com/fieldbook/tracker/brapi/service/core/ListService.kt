package com.fieldbook.tracker.brapi.service.core

import android.util.Log
import com.fieldbook.tracker.activities.brapi.io.sync.BrapiException
import com.fieldbook.tracker.brapi.service.BrapiV2ApiCallBack
import com.fieldbook.tracker.brapi.service.Fetcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.brapi.client.v2.model.exceptions.ApiException
import org.brapi.client.v2.model.queryParams.core.ListQueryParams
import org.brapi.client.v2.modules.core.ListsApi
import org.brapi.v2.model.core.BrAPIListSummary
import org.brapi.v2.model.core.response.BrAPIListDetails
import org.brapi.v2.model.core.response.BrAPIListsListResponse
import org.brapi.v2.model.core.response.BrAPIListsSingleResponse
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ListService {

    /**
     * Summaries of the server's lists, without their items, see fetchListDetails
     */
    fun fetchAll(params: ListQueryParams): Flow<Any>

    /**
     * The list with its items, the ids of whatever the list holds, such as the studyDbIds of a studies list.
     * The client can't page a list's items, so a list longer than the server's default page size is cut short.
     */
    suspend fun fetchListDetails(listDbId: String): BrAPIListDetails

    class Default(private val api: ListsApi) : ListService {

        companion object {
            private const val TAG = "ListService"
        }

        override fun fetchAll(params: ListQueryParams): Flow<Any> =
            Fetcher<BrAPIListSummary, ListQueryParams, BrAPIListsListResponse>().fetchAll(
                params,
                api::listsGetAsync
            )

        override suspend fun fetchListDetails(listDbId: String): BrAPIListDetails =
            suspendCancellableCoroutine { continuation ->

                try {
                    val call = api.listsListDbIdGetAsync(
                        listDbId,
                        object : BrapiV2ApiCallBack<BrAPIListsSingleResponse>() {
                            override fun onSuccess(
                                result: BrAPIListsSingleResponse?,
                                statusCode: Int,
                                responseHeaders: MutableMap<String, MutableList<String>>?
                            ) {
                                if (!continuation.isActive) return

                                val details = result?.result

                                if (details == null) {
                                    continuation.resumeWithException(BrapiException(statusCode))
                                    return
                                }

                                val total = result?.metadata?.pagination?.totalCount
                                val received = details.data?.size ?: 0
                                if (total != null && total > received) {
                                    Log.w(TAG, "List $listDbId has $total items, only $received were sent")
                                }

                                continuation.resume(details)
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

                    continuation.invokeOnCancellation { call.cancel() }

                } catch (e: ApiException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(BrapiException(e.code))
                    }
                }
            }
    }
}
