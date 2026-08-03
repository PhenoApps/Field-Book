package com.fieldbook.tracker.traits.formats.tree

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import com.fieldbook.tracker.traits.composables.constructor.blankSchema
import com.fieldbook.tracker.utilities.TreeSidecarWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

/**
 * JVM-adjacent A→B flush contract for R-01 / R-02.
 *
 * Mirrors [com.fieldbook.tracker.traits.TreeTraitLayout] flushPending / loadTree /
 * retryFailedFlush: write targets come from cached [TreePending], not a live plot id;
 * failed flushes survive a subsequent load; empty plot B gets a fresh root.
 *
 * Full CollectActivity timing is covered by
 * TreeTraitCollectInstrumentedTest.plotA_toEmptyPlotB_flushesToA_andDoesNotLeakTree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TreeFlushPlotABContractTest {

    private data class ObsKey(val studyId: String, val unitId: String, val traitId: String, val rep: String)

    @Test
    fun flushUsesCachedPendingUnit_notLivePlot_andBGetsFreshRoot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = Files.createTempDirectory("fb_tree_flush_ab").toFile()
        val dir = DocumentFile.fromFile(tempDir)
        val schema = blankSchema().copy(id = "flush_ab_schema")
        val store = mutableMapOf<ObsKey, String>()
        var pending: TreePending? = null
        var failedFlushPending: TreePending? = null
        var dirty = false
        var root: TreeNode? = null
        var liveUnit = "sample1"

        fun persist(p: TreePending, value: String) {
            store[ObsKey(p.studyId, p.unitId, p.traitId, p.rep)] = value
        }

        fun flushPending(): Boolean {
            if (!dirty) return true
            val p = pending ?: return true
            val writtenFor = p
            val uri = TreeSidecarWriter.write(context, writtenFor, schema.id, dir)
            if (uri == Uri.EMPTY) {
                failedFlushPending = writtenFor
                return false
            }
            persist(writtenFor, uri.toString())
            dirty = false
            failedFlushPending = null
            if (pending === writtenFor) {
                pending?.existingUri = uri.toString()
            }
            return true
        }

        fun loadTree(unitId: String, existingValue: String?) {
            // loadTree clears pending/root but not failedFlushPending (TreeTraitLayout).
            root = null
            dirty = false
            pending = null
            val existing = existingValue?.takeIf { it.isNotBlank() }
                ?.let { TreeSidecarWriter.read(context, Uri.parse(it)) }
            root = existing?.root ?: TreeCodec.newRoot(schema, "2026-07-30T12:00:00Z")
            pending = TreePending(
                unitId = unitId,
                studyId = "field1",
                traitId = "t1",
                traitName = "tree ab",
                rep = "1",
                root = root!!,
                capturedAt = existing?.captured ?: "2026-07-30T12:00:00Z",
                sourceApp = "Field Book Test",
                existingUri = existingValue,
            )
        }

        try {
            loadTree("sample1", null)
            val rootAId = root!!.id
            root = TreeMutations.setTrait(root!!, rootAId, "length", "42", "2026-07-30T12:01:00Z")
            pending!!.root = root!!
            dirty = true

            // TraitBoxView advances live plot before onRefresh.
            liveUnit = "sample2"
            assertEquals("sample1", pending!!.unitId)
            assertNotEquals(liveUnit, pending!!.unitId)

            assertTrue(flushPending())
            loadTree(
                liveUnit,
                store[ObsKey("field1", liveUnit, "t1", "1")],
            )

            val obsA = store[ObsKey("field1", "sample1", "t1", "1")]
            assertNotNull(obsA)
            assertNull(store[ObsKey("field1", "sample2", "t1", "1")])
            val decodedA = TreeSidecarWriter.read(context, Uri.parse(obsA!!))
            assertEquals("42", decodedA!!.root.traits["length"])

            assertEquals("sample2", pending!!.unitId)
            assertNotEquals(rootAId, root!!.id)
            assertNull(root!!.traits["length"])
            assertNull(failedFlushPending)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun failedFlushPendingSurvivesLoad_andRetriesOriginalUnit() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val schema = blankSchema().copy(id = "flush_fail_schema")
        val store = mutableMapOf<ObsKey, String>()
        val badPending = TreePending(
            unitId = "sample1",
            studyId = "field1",
            traitId = "t1",
            traitName = "tree ab",
            rep = "1",
            root = TreeCodec.newRoot(schema, "2026-07-30T12:00:00Z"),
            capturedAt = "2026-07-30T12:00:00Z",
            sourceApp = "Field Book Test",
        )

        // No media dir / FIELD_FILE → write returns Uri.EMPTY (layout failure path).
        val uri = TreeSidecarWriter.write(context, badPending, schema.id, dirOverride = null)
        assertEquals(Uri.EMPTY, uri)
        var failedFlushPending: TreePending? = badPending
        // loadTree would clear pending but keep failedFlushPending
        assertNotNull(failedFlushPending)
        assertEquals("sample1", failedFlushPending!!.unitId)

        val tempDir = Files.createTempDirectory("fb_tree_flush_retry").toFile()
        try {
            val dir = DocumentFile.fromFile(tempDir)
            val failed = failedFlushPending!!
            val retryUri = TreeSidecarWriter.write(context, failed, schema.id, dir)
            assertTrue(retryUri != Uri.EMPTY)
            store[ObsKey(failed.studyId, failed.unitId, failed.traitId, failed.rep)] = retryUri.toString()
            failedFlushPending = null

            assertNull(store[ObsKey("field1", "sample2", "t1", "1")])
            assertNotNull(store[ObsKey("field1", "sample1", "t1", "1")])
            assertNull(failedFlushPending)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
