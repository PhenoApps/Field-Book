package com.fieldbook.tracker.utilities.camera

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Session capture callback that tracks auto-exposure and auto-white-balance, so locks are only
 * engaged once the camera has settled, and the settled values can be read back.
 *
 * Several frames are always in flight, so results are judged by the request that produced them:
 * frames requested before a lock was released (or before it was engaged) are ignored.
 */
class ThreeAStateMonitor : CameraCaptureSession.CaptureCallback() {

    @Volatile private var lastResult: TotalCaptureResult? = null

    private val waiters = CopyOnWriteArrayList<Waiter>()

    private val main = Handler(Looper.getMainLooper())

    private abstract class Waiter {
        val done = AtomicBoolean(false)
        abstract fun matches(request: CaptureRequest, result: CaptureResult): Boolean
        abstract fun deliver(result: TotalCaptureResult?)
    }

    override fun onCaptureCompleted(
        session: CameraCaptureSession,
        request: CaptureRequest,
        result: TotalCaptureResult
    ) {
        lastResult = result
        waiters.forEach { if (it.matches(request, result)) fire(it, result) }
    }

    /**
     * Runs [onReady] on the main thread once AE and/or AWB have converged on frames captured
     * with those locks released, or after [timeoutMs] if they never report convergence.
     */
    fun awaitConverged(needAe: Boolean, needAwb: Boolean, timeoutMs: Long = 1500, onReady: () -> Unit) {

        val waiter = object : Waiter() {

            override fun matches(request: CaptureRequest, result: CaptureResult): Boolean {

                val aeReady = !needAe || (isAutoAndUnlocked(request, CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_OFF, CaptureRequest.CONTROL_AE_LOCK)
                        && result.get(CaptureResult.CONTROL_AE_STATE) in AE_CONVERGED)

                val awbReady = !needAwb || (isAutoAndUnlocked(request, CaptureRequest.CONTROL_AWB_MODE,
                    CameraMetadata.CONTROL_AWB_MODE_OFF, CaptureRequest.CONTROL_AWB_LOCK)
                        && result.get(CaptureResult.CONTROL_AWB_STATE) in AWB_CONVERGED)

                return aeReady && awbReady
            }

            override fun deliver(result: TotalCaptureResult?) = onReady()
        }

        register(waiter, timeoutMs)
    }

    /**
     * Delivers the first frame captured with the requested locks engaged, so the values the camera
     * locked to can be read. Falls back to the latest frame after [timeoutMs].
     */
    fun awaitLockedFrame(needAe: Boolean, needAwb: Boolean, timeoutMs: Long = 1000, onFrame: (TotalCaptureResult?) -> Unit) {

        val waiter = object : Waiter() {

            override fun matches(request: CaptureRequest, result: CaptureResult): Boolean {
                val aeLocked = !needAe || request.get(CaptureRequest.CONTROL_AE_LOCK) == true
                val awbLocked = !needAwb || request.get(CaptureRequest.CONTROL_AWB_LOCK) == true
                return aeLocked && awbLocked
            }

            override fun deliver(result: TotalCaptureResult?) = onFrame(result)
        }

        register(waiter, timeoutMs)
    }

    /**
     * Drop any pending waiters, e.g. when the camera is unbound or re-metered.
     */
    fun cancel() {
        waiters.forEach { it.done.set(true) }
        waiters.clear()
    }

    private fun register(waiter: Waiter, timeoutMs: Long) {
        waiters += waiter
        main.postDelayed({ fire(waiter, lastResult) }, timeoutMs)
    }

    private fun fire(waiter: Waiter, result: TotalCaptureResult?) {
        if (waiter.done.compareAndSet(false, true)) {
            waiters -= waiter
            main.post { waiter.deliver(result) }
        }
    }

    private fun isAutoAndUnlocked(
        request: CaptureRequest,
        modeKey: CaptureRequest.Key<Int>,
        offMode: Int,
        lockKey: CaptureRequest.Key<Boolean>
    ) = request.get(modeKey) != offMode && request.get(lockKey) != true

    companion object {

        // a null state counts as converged since LEGACY devices may not report it
        private val AE_CONVERGED = setOf(
            null,
            CaptureResult.CONTROL_AE_STATE_CONVERGED,
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
        )

        private val AWB_CONVERGED = setOf(
            null,
            CaptureResult.CONTROL_AWB_STATE_CONVERGED
        )
    }
}
