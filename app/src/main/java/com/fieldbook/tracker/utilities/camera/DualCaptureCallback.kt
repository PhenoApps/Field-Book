package com.fieldbook.tracker.utilities.camera

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Saved-image callback for simultaneous RAW + JPEG capture.
 *
 * Waits for [expected] results (saves or errors) and then runs [onComplete] exactly once.
 * If fewer results arrive, [onComplete] still runs [timeoutMs] after the first one,
 * so a missing RAW result never blocks the JPEG from being saved.
 * Callers should check which output files actually exist.
 */
class DualCaptureCallback(
    private val expected: Int = 2,
    private val timeoutMs: Long = 3000,
    private val onComplete: () -> Unit
) : ImageCapture.OnImageSavedCallback {

    companion object {
        private const val TAG = "DualCaptureCallback"
    }

    private val results = AtomicInteger(0)

    private val done = AtomicBoolean(false)

    private val main = Handler(Looper.getMainLooper())

    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
        Log.d(TAG, "onImageSaved: ${outputFileResults.savedUri}")
        onResult()
    }

    override fun onError(exception: ImageCaptureException) {
        Log.e(TAG, "Capture error", exception)
        onResult()
    }

    private fun onResult() {

        val count = results.incrementAndGet()

        Log.d(TAG, "Received $count of $expected capture results")

        if (count >= expected) {
            complete()
        } else if (count == 1) {
            main.postDelayed({
                if (!done.get()) Log.w(TAG, "Timed out waiting for capture results ($count of $expected)")
                complete()
            }, timeoutMs)
        }
    }

    private fun complete() {
        if (done.compareAndSet(false, true)) {
            onComplete()
        }
    }
}
