package com.fieldbook.tracker.brapi.service

import android.util.Log
import com.fieldbook.tracker.brapi.service.core.ApiCall
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import org.brapi.client.v2.ApiCallback
import org.brapi.client.v2.model.queryParams.core.BrAPIQueryParams
import org.brapi.v2.model.BrAPIResponse
import org.brapi.v2.model.BrAPIResponseResult
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

    private fun R.data(): List<U> =
        (result as? BrAPIResponseResult<*>)?.data?.mapNotNull {
            @Suppress("UNCHECKED_CAST")
            it as? U
        } ?: emptyList()

    fun fetchAll(params: T, apiCall: KFunction2<T, ApiCallback<R>, Call>) = callbackFlow {

        try {

            //first step: get the first page, its data is kept and its pagination metadata
            //determines how many remaining pages need to be queried
            params.page(0)

            val initialCallback = ApiCall<R>({ first ->

                val pagination = first.metadata?.pagination
                val totalCount = pagination?.totalCount ?: 0
                val totalPages = pagination?.totalPages ?: 1

                Log.d("FETCH", "Total count: $totalCount, Total pages: $totalPages")

                trySend(totalCount to first.data())

                if (totalPages <= 1) {
                    close()
                    return@ApiCall
                }

                val remaining = AtomicInteger(totalPages - 1)
                val failed = AtomicBoolean(false)

                for (i in 1 until totalPages) {

                    params.page(i)

                    Log.d("FETCH", "Calling page $i/$totalPages with ${params.pageSize()} items")

                    apiCall(params, ApiCall<R>({ response ->

                        trySend(totalCount to response.data())

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

    }.buffer(Channel.UNLIMITED)
}
