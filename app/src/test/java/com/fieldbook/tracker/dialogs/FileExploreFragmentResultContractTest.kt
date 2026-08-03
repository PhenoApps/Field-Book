package com.fieldbook.tracker.dialogs

import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentResultListener
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.functions.Function1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Owner test for Avoidable matrix seam: [FileExploreDialogFragment] fragment-result
 * contract used by tree schema file-pick ([com.fieldbook.tracker.traits.formats.parameters.TreeResourceFileParameter]).
 *
 * Non-tree [com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter] still relies on
 * [FileExploreDialogFragment.setOnFileSelectedListener] without a request key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileExploreFragmentResultContractTest {

    private lateinit var activity: FragmentActivity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    }

    @Test
    fun companions_argResultRequestKeyAndResultUri_exist() {
        assertEquals("resultRequestKey", FileExploreDialogFragment.ARG_RESULT_REQUEST_KEY)
        assertEquals("uri", FileExploreDialogFragment.RESULT_URI)
    }

    @Test
    fun sourceContract_nonBlankKeyGatesFragmentResult_listenerAlwaysInvoked() {
        val selectBranch = selectionBranchSource()

        assertTrue(
            "non-blank request key must publish fragment result with RESULT_URI",
            selectBranch.contains("arguments?.getString(ARG_RESULT_REQUEST_KEY)") &&
                selectBranch.contains("takeIf { it.isNotBlank() }") &&
                selectBranch.contains("parentFragmentManager.setFragmentResult") &&
                selectBranch.contains("putString(RESULT_URI, file.uri.toString())"),
        )
        assertTrue(
            "listener must still fire on select (ResourceFileParameter path)",
            selectBranch.contains("onFileSelectedListener?.invoke(file.uri)"),
        )
        // Blank / missing key: takeIf gate skips setFragmentResult; listener path remains.
        val gateThenResult = selectBranch.indexOf("takeIf { it.isNotBlank() }")
        val resultCall = selectBranch.indexOf("parentFragmentManager.setFragmentResult")
        assertTrue(gateThenResult >= 0 && resultCall > gateThenResult)
    }

    @Test
    fun nonBlankKey_setsFragmentResult_andListenerStillFires() {
        val requestKey = "tree.schema.file"
        val expectedUri = Uri.parse("content://test/resources/schema.json")
        val fragment = showFragment(requestKey)

        val resultUri = AtomicReference<String?>(null)
        val resultLatch = CountDownLatch(1)
        activity.supportFragmentManager.setFragmentResultListener(
            requestKey,
            activity,
            FragmentResultListener { _, bundle ->
                resultUri.set(bundle.getString(FileExploreDialogFragment.RESULT_URI))
                resultLatch.countDown()
            },
        )

        val listenerUri = AtomicReference<Uri?>(null)
        val listenerLatch = CountDownLatch(1)
        fragment.setOnFileSelectedListener { uri ->
            listenerUri.set(uri)
            listenerLatch.countDown()
        }

        invokeSelectFile(fragment, expectedUri)

        assertTrue("listener must fire", listenerLatch.await(3, TimeUnit.SECONDS))
        idleMain()
        assertTrue("fragment result must fire", resultLatch.await(3, TimeUnit.SECONDS))
        assertEquals(expectedUri.toString(), resultUri.get())
        assertEquals(expectedUri, listenerUri.get())
    }

    @Test
    fun blankKey_doesNotSetFragmentResult_listenerStillFires() {
        assertSelectWithoutFragmentResult(requestKey = "   ")
    }

    @Test
    fun missingKey_doesNotSetFragmentResult_listenerStillFires() {
        assertSelectWithoutFragmentResult(requestKey = null)
    }

    private fun assertSelectWithoutFragmentResult(requestKey: String?) {
        val expectedUri = Uri.parse("content://test/resources/photo.jpg")
        val fragment = showFragment(requestKey)

        val unexpectedResult = AtomicReference<Bundle?>(null)
        activity.supportFragmentManager.setFragmentResultListener(
            "should.never.fire",
            activity,
        ) { _, bundle -> unexpectedResult.set(bundle) }
        // Listen on the provided key (incl. whitespace-only) — must not publish when blank/gated.
        if (requestKey != null) {
            activity.supportFragmentManager.setFragmentResultListener(
                requestKey,
                activity,
            ) { _, bundle -> unexpectedResult.set(bundle) }
        }

        val listenerUri = AtomicReference<Uri?>(null)
        val listenerLatch = CountDownLatch(1)
        fragment.setOnFileSelectedListener { uri ->
            listenerUri.set(uri)
            listenerLatch.countDown()
        }

        invokeSelectFile(fragment, expectedUri)

        assertTrue("listener must fire without request key", listenerLatch.await(3, TimeUnit.SECONDS))
        idleMain()
        assertNull("blank/missing key must not publish fragment result", unexpectedResult.get())
        assertEquals(expectedUri, listenerUri.get())
    }

    private fun showFragment(requestKey: String?): FileExploreDialogFragment {
        // Embed without Dialog/Compose: AppTheme → Hilt ViewModel on plain FragmentActivity.
        val fragment = FileExploreDialogFragment().apply {
            arguments = Bundle().apply {
                if (requestKey != null) {
                    putString(FileExploreDialogFragment.ARG_RESULT_REQUEST_KEY, requestKey)
                }
                putString("dialogTitle", "test")
            }
            showsDialog = false
        }
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "FileExploreFragmentResultContractTest")
            .commitNow()
        idleMain()
        assertNull(fragment.dialog)
        assertTrue(fragment.isAdded)
        return fragment
    }

    private fun invokeSelectFile(fragment: FileExploreDialogFragment, uri: Uri) {
        val doc = mock(DocumentFile::class.java)
        `when`(doc.exists()).thenReturn(true)
        `when`(doc.isDirectory).thenReturn(false)
        `when`(doc.uri).thenReturn(uri)

        val item = FileExploreDialogFragment.FileItem(
            name = "picked.json",
            isDirectory = false,
            icon = 0,
            documentFile = doc,
        )

        val method = FileExploreDialogFragment::class.java.getDeclaredMethod(
            "handleItemClick",
            FileExploreDialogFragment.FileItem::class.java,
            Function1::class.java,
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val onComplete = { _: List<FileExploreDialogFragment.FileItem> -> } as Function1<
            List<FileExploreDialogFragment.FileItem>,
            Unit,
        >
        method.invoke(fragment, item, onComplete)
        // handleItemClick hops IO → Main; drain both.
        Thread.sleep(50)
        idleMain()
        Thread.sleep(50)
        idleMain()
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun selectionBranchSource(): String {
        val source = File(
            "src/main/java/com/fieldbook/tracker/dialogs/FileExploreDialogFragment.kt",
        ).readText()
        return source
            .substringAfter("else -> { // selecting a file")
            .substringBefore("private fun getFileExtension")
    }
}
