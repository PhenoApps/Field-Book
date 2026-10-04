package com.fieldbook.tracker.brapi.service

import android.util.Log
import com.fieldbook.tracker.brapi.service.core.ApiCall
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import org.brapi.client.v2.ApiCallback
import org.brapi.client.v2.model.queryParams.core.BrAPIQueryParams
import org.brapi.v2.model.BrAPIResponse
import org.brapi.v2.model.BrAPIResponseResult
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KFunction2

/**
 * Converts BrAPI REST calls into cold flows
 * Emits (totalCount to models) for each page and completes once every page has responded,
 * or fails with the first page error.
 * @param T the brapi query param sub class
 * @param R the brapi response
 */
class Fetcher<U, T : BrAPIQueryParams, R : BrAPIResponse<*>> {

    //the client passes null for a response without a body, such as a 204, despite the type
    private fun R?.data(): List<U> =
        (this?.result as? BrAPIResponseResult<*>)?.data?.mapNotNull {
            @Suppress("UNCHECKED_CAST")
            it as? U
        } ?: emptyList()

    fun fetchAll(params: T, apiCall: KFunction2<T, ApiCallback<R>, Call>) = callbackFlow {

        //every request made, cancelled when the flow ends early, after a failed page or when the collector stops
        val calls = ConcurrentLinkedQueue<Call>()

        try {

            //first step: get the first page, its data is kept and its pagination metadata
            //determines how many remaining pages need to be queried
            params.page(0)

            val initialCallback = ApiCall<R>({ response ->

                @Suppress("USELESS_CAST")
                val first = response as R?

                val data = first.data()
                val pagination = first?.metadata?.pagination
                val totalCount = pagination?.totalCount ?: 0

                //some servers leave out totalPages, it's worked out from the total
                //and the page size the server used, which may not be the one asked for
                val pageSize = pagination?.pageSize?.takeIf { it > 0 } ?: data.size.takeIf { it > 0 } ?: 1
                val totalPages = pagination?.totalPages ?: ((totalCount + pageSize - 1) / pageSize)

                Log.d("FETCH", "Total count: $totalCount, Total pages: $totalPages")

                trySend(totalCount to data)

                if (totalPages <= 1) {
                    close()
                    return@ApiCall
                }

                val remaining = AtomicInteger(totalPages - 1)
                val failed = AtomicBoolean(false)

                for (i in 1 until totalPages) {

                    params.page(i)

                    Log.d("FETCH", "Calling page $i/$totalPages with ${params.pageSize()} items")

                    calls += apiCall(params, ApiCall<R>({ pageResponse ->

                        @Suppress("USELESS_CAST")
                        trySend(totalCount to (pageResponse as R?).data())

                        if (remaining.decrementAndGet() == 0 && !failed.get()) {
                            close()
                        }

                    }) { e ->

                        Log.e("FETCH", "Failed to fetch page $i/$totalPages", e)

                        if (failed.compareAndSet(false, true)) {
                            close(e ?: IllegalStateException("Failed to fetch page $i"))
                        }
                    })
                }

            }) { e ->

                Log.e("FETCH", "Failed to fetch the first page", e)

                //closed with the error rather than cancelled, so collectors get the server's error code
                close(e ?: IllegalStateException("Failed to fetch the first page"))
            }

            calls += apiCall(params, initialCallback)

            Log.d("FETCH", "Initial call made")

        } catch (e: Exception) {

            e.printStackTrace()

            close(e)
        }

        //requests that already finished ignore the cancel
        awaitClose { calls.forEach { it.cancel() } }

    }.buffer(Channel.UNLIMITED)
}
