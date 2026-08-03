package com.fieldbook.tracker.traits.composables.collect

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import com.fieldbook.tracker.utilities.TreeSidecarWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Regresses portable-path leaf + SAF double-extension preview bugs:
 * `toRelative("folder/file.jpg")` keeps the slash, and
 * `createFile("image/jpeg", "x.jpg")` may yield on-disk `x.jpg.jpg`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NodePhotoPreviewResolveTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun setField(field: String) {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(com.fieldbook.tracker.preferences.GeneralKeys.FIELD_FILE, field)
            .apply()
    }

    @Test
    fun resolve_portableTraitFolderPath_findsAppFilesLeaf() {
        val field = "field1"
        val folder = "soy tree-carrier"
        val leaf = "sample1_node_2026-07-27.jpg"
        val dir = File(context.getExternalFilesDir(null), "plot_data/$field/$folder")
        assertTrue(dir.mkdirs() || dir.isDirectory)
        val file = File(dir, leaf)
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))

        setField(field)

        val uri = resolveNodePhotoUri(context, "$folder/$leaf", traitName = "branch photo")
        assertNotNull("must resolve portable folder/file path", uri)
        assertTrue(uri!!.contains(leaf) || uri.startsWith("file:"))
    }

    @Test
    fun resolve_basenameOnly_usesTraitNameFolder() {
        val field = "field1"
        val folder = "soy tree-carrier"
        val leaf = "sample1_node_only.jpg"
        val dir = File(context.getExternalFilesDir(null), "plot_data/$field/$folder")
        assertTrue(dir.mkdirs() || dir.isDirectory)
        File(dir, leaf).writeBytes(byteArrayOf(1, 2, 3))

        setField(field)

        val uri = resolveNodePhotoUri(context, leaf, traitName = folder)
        assertNotNull(uri)
    }

    @Test
    fun resolve_doubleExtensionLeaf_findsJpgJpgOnDisk() {
        val field = "field1"
        val folder = "Тест сои"
        val storedLeaf = "plotA_node_2026-08-03.jpg"
        val onDisk = "$storedLeaf.jpg" // SAF image/jpeg + displayName quirk
        val dir = File(context.getExternalFilesDir(null), "plot_data/$field/$folder")
        assertTrue(dir.mkdirs() || dir.isDirectory)
        File(dir, onDisk).writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        setField(field)

        val uri = resolveNodePhotoUri(context, "$folder/$storedLeaf", traitName = "branch photo")
        assertNotNull("must resolve SAF double-extension leaf", uri)
        assertTrue(uri!!.contains(onDisk) || uri.contains(storedLeaf))
    }

    @Test
    fun resolve_basenameWithWrongStudyTrait_walksPlotData() {
        val field = "field1"
        val treeFolder = "soy tree-carrier"
        val leaf = "walk_node_basename.jpg"
        val dir = File(context.getExternalFilesDir(null), "plot_data/$field/$treeFolder")
        assertTrue(dir.mkdirs() || dir.isDirectory)
        File(dir, leaf).writeBytes(byteArrayOf(9, 9, 9))
        setField(field)

        // Preview host passes study trait name; media lives under tree trait folder.
        val uri = resolveNodePhotoUri(context, leaf, traitName = "branch photo")
        assertNotNull("must walk plot_data when study folder is wrong", uri)
    }

    @Test
    fun findMediaLeaf_matchesDoubleExtension() {
        val temp = File(context.cacheDir, "find_media_leaf_${System.nanoTime()}").also {
            assertTrue(it.mkdirs())
        }
        try {
            val leaf = "unit_node_1.jpg"
            File(temp, "$leaf.jpg").writeBytes(byteArrayOf(1))
            val doc = DocumentFile.fromFile(temp)
            val found = TreeSidecarWriter.findMediaLeaf(doc, leaf)
            assertNotNull(found)
            assertEquals("$leaf.jpg", found!!.name)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun resolve_blank_returnsNull() {
        assertNull(resolveNodePhotoUri(context, "", traitName = "x"))
    }

    @Test
    fun resolve_contentUri_passthrough() {
        val content = "content://com.fieldbook/tree/sample1_node_1.jpg"
        assertEquals(content, resolveNodePhotoUri(context, content))
    }
}
