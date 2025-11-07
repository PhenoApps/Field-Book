package com.fieldbook.tracker.activities.brapi.io.sync

import android.content.Context
import com.fieldbook.tracker.brapi.model.FieldBookImage
import com.fieldbook.tracker.brapi.model.Observation
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * This file is a collection of helper suspend functions that access the BrAPI service.
 * To be used with Kotlin Coroutines.
 */


suspend fun BrAPIService.awaitPostImageMetaData(image: FieldBookImage): FieldBookImage =
    suspendCoroutine { continuation ->
        this.postImageMetaData(
            image,
            { resultImage ->
                resultImage?.let { continuation.resume(it) } ?: continuation.resumeWithException(
                    IllegalStateException("postImageMetaData returned null")
                )
                null
            },
            { errorCode ->
                continuation.resumeWithException(BrapiException(errorCode))
                null
            }
        )
    }

suspend fun BrAPIService.awaitPutImageContent(image: FieldBookImage): FieldBookImage =
    suspendCoroutine { continuation ->
        this.putImageContent(
            image,
            { resultImage ->
                resultImage?.let { continuation.resume(it) } ?: continuation.resumeWithException(
                    IllegalStateException("putImageContent returned null")
                )
                null
            },
            { errorCode ->
                continuation.resumeWithException(BrapiException(errorCode))
                null
            }
        )
    }

suspend fun BrAPIService.awaitPutImage(image: FieldBookImage): FieldBookImage =
    suspendCoroutine { continuation ->
        this.putImage(
            image,
            { resultImage ->
                resultImage?.let { continuation.resume(it) } ?: continuation.resumeWithException(
                    IllegalStateException("putImage returned null")
                )
                null
            },
            { errorCode ->
                continuation.resumeWithException(BrapiException(errorCode))
                null
            }
        )
    }

suspend fun BrAPIService.awaitCreateObservations(
    context: Context,
    observations: List<Observation>,
    onChunkCompleted: (MutableList<Observation>) -> Unit,
    onChunkFailed: (Int, List<Observation>) -> Unit,
): Unit = suspendCoroutine { continuation ->
    val chunkSize = BrAPIService.getChunkSize(context)
    createObservationsChunked(
        chunkSize,
        observations,
        { chunk, _, _, done ->
            onChunkCompleted(chunk)
            if (done) {
                continuation.resume(Unit)
            }
        },
        { errorCode, chunk, done ->
            onChunkFailed(errorCode, chunk)
            if (done) {
                continuation.resume(Unit)
            }
            null
        }
    )
}

suspend fun BrAPIService.awaitUpdateObservations(
    context: Context,
    observations: List<Observation>,
    onChunkCompleted: (MutableList<Observation>) -> Unit,
    onChunkFailed: (Int, List<Observation>) -> Unit,
): Unit = suspendCoroutine { continuation ->
    val chunkSize = BrAPIService.getChunkSize(context)
    updateObservationsChunked(
        chunkSize,
        observations,
        { chunk, _, _, done ->
            onChunkCompleted(chunk)
            if (done) {
                continuation.resume(Unit)
            }
        },
        { errorCode, failedChunk, done ->
            onChunkFailed(errorCode, failedChunk)
            if (done) {
                continuation.resume(Unit)
            }
            null
        }
    )
}

/**
 * Fetches all pages of observations for a given study and variables in parallel,
 * respecting a concurrency limit.
 *
 * @param brapiStudyId The BrAPI database ID for the study.
 * @param variableDbIds A list of variable IDs to filter by.
 * @param pageSize The number of items to fetch per page.
 * @param concurrencyLimit The maximum number of pages to fetch simultaneously.
 * @return A list containing all observations fetched from all pages.
 */
suspend fun BrAPIService.awaitGetObservations(
    brapiStudyId: String,
    variableDbIds: List<String>,
    paginationManager: BrapiPaginationManager,
    initialPages: List<Observation>,
    concurrencyLimit: Int = 5,
    onPageCompleted: suspend (Int, List<Observation>) -> Unit,
): List<Observation> = coroutineScope {

    val allObservations = ConcurrentLinkedQueue(initialPages)
    val semaphore = Semaphore(concurrencyLimit)
    val deferredJobs = (1 until paginationManager.totalPages).map { pageIndex ->
        async(Dispatchers.IO) {
            semaphore.withPermit {
                val pageResult = awaitGetSingleObservationPage(
                    brapiStudyId,
                    variableDbIds,
                    paginationManager.also {
                        it.setNewPage(pageIndex)
                    })
                onPageCompleted(pageIndex, pageResult)
                allObservations.addAll(pageResult)
            }
        }
    }

    deferredJobs.awaitAll()

    allObservations.toList()
}

/**
 * A helper suspend function to fetch a single page of observations.
 * This wraps the original callback-based function.
 */
suspend fun BrAPIService.awaitGetSingleObservationPage(
    brapiStudyId: String,
    variableDbIds: List<String>,
    paginationManager: BrapiPaginationManager
): List<Observation> = suspendCancellableCoroutine { continuation ->
    getObservations(
        brapiStudyId, variableDbIds, paginationManager,
        { result ->
            if (continuation.isActive) {
                continuation.resume(result)
            }
            null
        },
        { errorCode ->
            if (continuation.isActive) {
                continuation.resumeWithException(BrapiException(errorCode))
            }
            null
        }
    )

    continuation.invokeOnCancellation {

    }
}