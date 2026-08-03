package com.fieldbook.tracker.utilities

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owner test for [CameraXFacade.await] fail-closed bind (09 Residual-risk / watchlist #5).
 *
 * Contract: provider get uses a 15s timeout; on timeout/failure [onBindReady] must not run
 * (callers treat unbound camera as failure — no UI freeze waiting forever).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraXFacadeAwaitFailClosedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun await_sourceContract_uses15sGetAndFailClosed() {
        val source = File(
            "src/main/java/com/fieldbook/tracker/utilities/CameraXFacade.kt",
        ).readText()
        val awaitBody = source
            .substringAfter("fun await(context: Context, onBindReady: () -> Unit)")
            .substringBefore("fun unbind()")

        assertTrue(
            "await must bound-get the provider with a 15s timeout",
            awaitBody.contains("get(15, java.util.concurrent.TimeUnit.SECONDS)") ||
                awaitBody.contains("get(15, TimeUnit.SECONDS)"),
        )
        assertTrue(
            "failure path must return without invoking onBindReady",
            awaitBody.contains("if (!ready)") &&
                awaitBody.contains("Do not invoke onBindReady") &&
                awaitBody.contains("return"),
        )
        // Success path is the only place onBindReady is scheduled.
        assertTrue(awaitBody.contains("addListener({ onBindReady() }"))
    }

    @Test
    fun await_providerGetTimeout_doesNotInvokeOnBindReady() {
        val facade = CameraXFacade(context)
        @Suppress("UNCHECKED_CAST")
        val future = mock(ListenableFuture::class.java) as ListenableFuture<ProcessCameraProvider>
        `when`(future.get(eq(15L), eq(TimeUnit.SECONDS)))
            .thenThrow(TimeoutException("simulated headless / broken HAL"))

        installCameraXInstance(facade, future)

        val onBindReady = AtomicBoolean(false)
        facade.await(context) { onBindReady.set(true) }

        assertFalse(
            "fail-closed: timeout must not invoke onBindReady",
            onBindReady.get(),
        )
        verify(future).get(15L, TimeUnit.SECONDS)
        verify(future, never()).addListener(any(), any())
    }

    @Test
    fun await_providerGetFailure_doesNotInvokeOnBindReady() {
        val facade = CameraXFacade(context)
        @Suppress("UNCHECKED_CAST")
        val future = mock(ListenableFuture::class.java) as ListenableFuture<ProcessCameraProvider>
        `when`(future.get(eq(15L), eq(TimeUnit.SECONDS)))
            .thenThrow(RuntimeException("provider unavailable"))

        installCameraXInstance(facade, future)

        val onBindReady = AtomicBoolean(false)
        facade.await(context) { onBindReady.set(true) }

        assertFalse(
            "fail-closed: get failure must not invoke onBindReady",
            onBindReady.get(),
        )
        verify(future).get(eq(15L), eq(TimeUnit.SECONDS))
        verify(future, never()).addListener(any(), any())
    }

    /**
     * Replace the Kotlin lazy delegate so await exercises a mocked [ListenableFuture]
     * without redesigning bind/unbind or touching CameraActivity.
     */
    private fun installCameraXInstance(
        facade: CameraXFacade,
        future: ListenableFuture<ProcessCameraProvider>,
    ) {
        val field = CameraXFacade::class.java.getDeclaredField("cameraXInstance\$delegate")
        field.isAccessible = true
        field.set(facade, lazyOf(future))
    }
}
