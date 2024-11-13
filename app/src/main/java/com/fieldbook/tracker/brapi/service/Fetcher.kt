package com.fieldbook.tracker.brapi.service

import android.util.Log
import com.fieldbook.tracker.brapi.service.core.ApiCall
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import org.brapi.client.v2.ApiCallback
import org.brapi.client.v2.model.exceptions.ApiException
import org.brapi.client.v2.model.queryParams.core.BrAPIQueryParams
import org.brapi.v2.model.BrAPIResponse
import org.brapi.v2.model.BrAPIResponseResult
import kotlin.reflect.KFunction2

/**
 * Converts BrAPI REST calls into cold flows
 * @param T the brapi query param sub class
 * @param R the brapi response
 */
class Fetcher<U, T : BrAPIQueryParams, R : BrAPIResponse<*>> {

    fun fetchAll(params: T, apiCall: KFunction2<T, ApiCallback<R>, Call>) = callbackFlow {

        try {

            val pageSize = 512

            //first step: get the first page to read pagination metadata to determine how many
            //page will need to be queried
            params.page(0)
            params.pageSize(pageSize)

            //callback for returning the data through the flow channel, called after pagination is found
            //callback for querying metadata about the api call, then it queries for all data
            val initialCallback = ApiCall<R>({

                Log.d("FETCH", "Checking metadata: ${it.metadata == null}")

                //only care about metadata, so grab its pagination total count and call the api for all data
                if (it.metadata != null) {

                    val totalCount = it.metadata.pagination.totalCount
                    val total = it.metadata.pagination.totalPages
                    var actualCount = 0

                    Log.d("FETCH", "Total count: $totalCount, Total pages: $total")

                    if (totalCount == 0) {
                        trySend(totalCount to emptyList())
                        return@ApiCall
                    }

                    for (i in 0 until total) {

                        params.page(i)
                        params.pageSize(pageSize)

                        Log.d("FETCH", "Calling page $i/$total with $pageSize items")
                        apiCall(params, ApiCall<R>({ response ->

                            if (response.metadata != null && response.result != null) {

                                (response.result as? BrAPIResponseResult<*>)?.data?.let { data ->

                                    Log.d("FETCH", "Received data ${data.size}")

                                    actualCount += data.size

                                    trySend(totalCount to data.mapNotNull { m -> m as? U })

                                    Log.d("FETCH", "Sent $actualCount/$totalCount models")

                                }
                            }

                        }) { e ->

                            e?.printStackTrace()

                        })
                    }
                }

            }) { e ->

                e?.printStackTrace()

                cancel(e?.message ?: "Unknown error", e)

            }

            apiCall(params, initialCallback)

            Log.d("FETCH", "Initial call made")

        } catch (e: Exception) {

            e.printStackTrace()

            cancel(e.message ?: "Unknown error")

            throw(e)
        }

        awaitClose()

    }
}