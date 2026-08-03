package com.fieldbook.tracker.utilities

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import com.fieldbook.tracker.traits.formats.tree.TreeCodec
import com.fieldbook.tracker.traits.formats.tree.TreeMutations
import com.fieldbook.tracker.traits.formats.tree.TreePending
import com.fieldbook.tracker.traits.formats.tree.TreeSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the real tree-node photo copy + sidecar persistence path against temp files.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TreeSidecarWriterPhotoTest {

    @Test
    fun saveNodePhotoCopiesFile_and_writePersistsPortableRelativeValue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = Files.createTempDirectory("fb_tree_node_photo").toFile()
        val targetDir = DocumentFile.fromFile(tempDir)
        val source = File.createTempFile("tree_source", ".jpg")
        source.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        try {
            val relative = TreeSidecarWriter.saveNodePhoto(
                context = context,
                traitName = "soy tree-carrier",
                plotId = "PLOT 1/A",
                sourcePath = source.absolutePath,
                dirOverride = targetDir,
            )

            assertNotNull(relative)
            assertEquals(
                "soy tree-carrier",
                relative!!.substringBefore('/'),
            )
            assertTrue(relative.endsWith(".jpg"))
            assertFalse(relative.startsWith("content:"))

            val copiedName = relative.substringAfterLast('/')
            val copied = File(tempDir, copiedName)
            assertTrue(
                "persisted leaf must match an on-disk DocumentFile name",
                copied.exists(),
            )
            assertEquals(source.readBytes().toList(), copied.readBytes().toList())

            val found = TreeSidecarWriter.findMediaLeaf(targetDir, copiedName)
            assertNotNull(found)
            assertEquals(copiedName, found!!.name)

            val schema = photoSchema()
            val createdAt = "2026-07-29T09:00:00Z"
            var root = TreeCodec.newRoot(schema, createdAt)
            val branchRule = schema.typeOf("root")!!.allowedChildren.first()
            val (withBranch, branchId) = TreeMutations.addChild(root, root.id, branchRule, schema, createdAt)
            root = TreeMutations.setTrait(withBranch, branchId, "branch photo", relative, createdAt)

            val pending = TreePending(
                unitId = "PLOT 1/A",
                studyId = "study1",
                traitId = "t1",
                traitName = "soy tree-carrier",
                rep = "1",
                root = root,
                capturedAt = createdAt,
                sourceApp = "Field Book Test",
            )

            val sidecarUri = TreeSidecarWriter.write(
                context = context,
                pending = pending,
                schemaId = schema.id,
                dirOverride = targetDir,
            )

            assertTrue(sidecarUri != android.net.Uri.EMPTY)

            val writtenJson = File(sidecarUri.path!!).readText()
            assertTrue(writtenJson.contains(relative))
            assertFalse(writtenJson.contains(source.absolutePath))
            assertFalse(writtenJson.contains("content://"))

            val decoded = TreeSidecarWriter.read(context, sidecarUri)
            assertNotNull(decoded)
            val branch = decoded!!.root.children.single()
            assertEquals(relative, branch.traits["branch photo"])

            val mtgName = File(sidecarUri.path!!).name.removeSuffix(".json") + ".mtg"
            assertFalse(
                "JSON already carries mtg; companion .mtg must not be written",
                File(tempDir, mtgName).exists(),
            )
            assertFalse(File(tempDir, "$mtgName.txt").exists())
        } finally {
            source.delete()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun deleteReferencedMediaValue_usesFolderFromPortablePath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = Files.createTempDirectory("fb_tree_delete_folder").toFile()
        val mediaDir = File(tempDir, "soy tree-carrier").also { assertTrue(it.mkdirs()) }
        val leaf = "plot_node_del.jpg"
        val file = File(mediaDir, leaf).also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val docRoot = DocumentFile.fromFile(mediaDir)

        try {
            TreeSidecarWriter.deleteReferencedMediaValue(
                context = context,
                traitName = "branch photo",
                value = "soy tree-carrier/$leaf",
                dirOverride = docRoot,
            )
            assertFalse("media leaf must be deleted via path folder", file.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun findMediaLeaf_and_delete_tolerateDoubleExtension() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = Files.createTempDirectory("fb_tree_double_ext").toFile()
        val stored = "plot_node_x.jpg"
        val onDisk = File(tempDir, "$stored.jpg").also { it.writeBytes(byteArrayOf(4, 5)) }
        val doc = DocumentFile.fromFile(tempDir)
        try {
            assertNotNull(TreeSidecarWriter.findMediaLeaf(doc, stored))
            TreeSidecarWriter.deleteReferencedMediaValue(
                context = context,
                traitName = "ignored",
                value = "carrier/$stored",
                dirOverride = doc,
            )
            assertFalse(onDisk.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun photoSchema(): TreeSchema =
        TreeSchema(
            id = "tree_photo_v1",
            name = "Soy Tree Carrier",
            version = 1,
            rootType = "root",
            nodeTypes = listOf(
                com.fieldbook.tracker.traits.formats.tree.NodeTypeDef(
                    name = "root",
                    displayName = "Root",
                    cls = "R",
                    allowedChildren = listOf(
                        com.fieldbook.tracker.traits.formats.tree.ChildRule(
                            "branch",
                            com.fieldbook.tracker.traits.formats.tree.EdgeType.BEARS,
                            "Add Branch",
                        ),
                    ),
                ),
                com.fieldbook.tracker.traits.formats.tree.NodeTypeDef(
                    name = "branch",
                    displayName = "Branch",
                    cls = "B",
                    traitRefs = listOf(
                        com.fieldbook.tracker.traits.formats.tree.TraitRef("branch photo", order = 0),
                    ),
                ),
            ),
        )
}
