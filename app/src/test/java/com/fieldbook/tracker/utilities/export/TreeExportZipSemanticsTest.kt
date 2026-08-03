package com.fieldbook.tracker.utilities.export

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.dao.spectral.DeviceDao
import com.fieldbook.tracker.database.dao.spectral.ProtocolDao
import com.fieldbook.tracker.database.dao.spectral.SpectralDao
import com.fieldbook.tracker.database.dao.spectral.UriDao
import com.fieldbook.tracker.database.repository.SpectralRepository
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.tree.ChildRule
import com.fieldbook.tracker.traits.formats.tree.EdgeType
import com.fieldbook.tracker.traits.formats.tree.NodeTypeDef
import com.fieldbook.tracker.traits.formats.tree.TreeCodec
import com.fieldbook.tracker.traits.formats.tree.TreeFlattenExport
import com.fieldbook.tracker.traits.formats.tree.TreeMutations
import com.fieldbook.tracker.traits.formats.tree.TreePending
import com.fieldbook.tracker.traits.formats.tree.TreeSchema
import com.fieldbook.tracker.traits.formats.tree.find
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.ZipUtil
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TreeExportZipSemanticsTest {

    @Test
    fun zipContainsTreeSidecarJsonAndNodesCsv_portablePaths() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        val schema = podPhotoSchema()
        val createdAt = "2026-07-27T10:00:00Z"

        var root = TreeCodec.newRoot(schema, createdAt)
        val plantRule = schema.typeOf("plant")!!.allowedChildren.first()
        val podRule = schema.typeOf("internode")!!.allowedChildren.first()

        val (r1, internodeId) = TreeMutations.addChild(root, root.id, plantRule, schema, createdAt)
        root = r1
        val (r2, podId) = TreeMutations.addChild(root, internodeId, podRule, schema, createdAt)
        root = r2

        val photoUri =
            "content://com.fieldbook.tracker.fileprovider/tree/PLOT_1_node_2026-07-27-12-00-00-000.jpg"

        root = TreeMutations.setTrait(root, podId, "Seed count", "4", createdAt)
        // In the real app, this trait is on the pod node and is stored as the camera URI.
        root = TreeMutations.setTrait(root, podId, "Pod photo", photoUri, createdAt)

        val pending = TreePending(
            unitId = "PLOT_1",
            studyId = "1",
            traitId = "t1",
            traitName = "soy tree-carrier",
            rep = "1",
            root = root,
            capturedAt = createdAt,
            sourceApp = "Field Book Test",
        )

        val json = TreeCodec.encodeSidecar(schema.id, pending)
        val decoded = TreeCodec.decodeObservation(json)

        // Portableize check: no content:// URIs in JSON; folder matches ExportUtil media dir.
        val traitFolderName = FileUtil.sanitizeFileName("soy tree-carrier")
        assertEquals("soy tree-carrier", traitFolderName)
        assertTrue(json.contains("$traitFolderName/"))
        assertFalse(json.contains(photoUri))
        assertFalse(json.contains("content://"))

        val photoInJson = find(decoded.root, podId)!!.traits["Pod photo"]!!
        assertEquals(
            "$traitFolderName/PLOT_1_node_2026-07-27-12-00-00-000.jpg",
            photoInJson,
        )

        val csv = TreeFlattenExport.toCsv(decoded)

        // Build a local directory structure and zip it using ZipUtil (same helper ExportUtil uses).
        val tempRoot = Files.createTempDirectory("fb_tree_zip_semantics").toFile()
        val traitDir = File(tempRoot, traitFolderName)
        traitDir.mkdirs()

        val sidecarFile = File(traitDir, "PLOT_1_sidecar.json")
        sidecarFile.writeText(json)

        // nodes.csv is written to the export directory (zip root), not into trait media —
        // this unit test still validates CSV payload semantics via a local zip of the trait dir.
        val studyName = "study1"
        val nodesCsvName = "${studyName}_${traitFolderName}_nodes.csv"
        val nodesCsvFile = File(traitDir, nodesCsvName)
        nodesCsvFile.writeText(csv)

        val zipOut = ByteArrayOutputStream()
        ZipUtil.Companion.zip(ctx, arrayOf(DocumentFile.fromFile(traitDir)), zipOut)
        val zipBytes = zipOut.toByteArray()
        assertTrue(zipBytes.isNotEmpty())

        val zipEntries = readZipEntries(zipBytes)
        val jsonEntry = zipEntries.entries.firstOrNull { it.key.endsWith(".json") }
        val csvEntry = zipEntries.entries.firstOrNull { it.key.endsWith("_nodes.csv") }
        assertNotNull(jsonEntry)
        assertNotNull(csvEntry)

        val zippedJson = jsonEntry!!.value.toString(StandardCharsets.UTF_8)
        assertTrue(zippedJson.contains("$traitFolderName/"))
        assertFalse(zippedJson.contains("content://"))

        val zippedCsv = csvEntry!!.value.toString(StandardCharsets.UTF_8)
        assertEquals(csv, zippedCsv)
    }

    @Test
    fun valueProcessorFormatAdapter_treeDbUri_csvShowsFilenameOnly() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val database = mock(DataHelper::class.java)
        val spectralDao = SpectralDao(database)
        val protocolDao = ProtocolDao(database)
        val deviceDao = DeviceDao(database)
        val uriDao = UriDao(database)
        val spectralRepository = SpectralRepository(spectralDao, protocolDao, deviceDao, uriDao)
        val spectralProcessor = SpectralFileProcessor(database, spectralRepository)
        val processor = ValueProcessorFormatAdapter(ctx, spectralProcessor)

        val trait = TraitObject().apply {
            format = Formats.TREE_ARCHITECTURE.getDatabaseName()
        }

        // DB stores sidecar URI (same pattern as camera traits); CSV export shows basename.
        val existing = Files.createTempDirectory("fb_tree_existing_sidecar").toFile()
        val sidecar = File(existing, "sample1_soy_tree-carrier_2026-07-30-11-49-30-000.json")
        sidecar.writeText("""{"schemaId":"soy","unit":"sample1","trait":"soy tree-carrier","root":{}}""")
        val dbValue = sidecar.toURI().toString()
        assertTrue(dbValue.startsWith("file:"))

        val csvCell = processor.processValue(dbValue, trait)
        assertEquals("sample1_soy_tree-carrier_2026-07-30-11-49-30-000.json", csvCell)
        assertFalse(csvCell!!.contains("file:"))
        assertFalse(csvCell.contains("/"))

        ValueProcessorFormatAdapter.resetMissingSidecarTally()
        val missingFile = File(
            Files.createTempDirectory("fb_tree_missing_sidecar").toFile(),
            "missing_sidecar.json",
        )
        assertEquals(
            "${ValueProcessorFormatAdapter.MISSING_SIDECAR_PREFIX}missing_sidecar.json",
            processor.processValue(missingFile.toURI().toString(), trait),
        )
        assertEquals(1, ValueProcessorFormatAdapter.missingSidecarCount())
        // Same missing leaf counted once across repeated processValue calls (db + table export).
        processor.processValue(missingFile.toURI().toString(), trait)
        assertEquals(1, ValueProcessorFormatAdapter.missingSidecarCount())
    }

    private fun podPhotoSchema(): TreeSchema =
        TreeSchema(
            id = "soy_arch_v1",
            name = "Soybean architecture",
            version = 1,
            rootType = "plant",
            nodeTypes = listOf(
                NodeTypeDef(
                    name = "plant",
                    displayName = "Plant",
                    cls = "P",
                    allowedChildren = listOf(ChildRule("internode", EdgeType.PRECEDES, "Main")),
                ),
                NodeTypeDef(
                    name = "internode",
                    displayName = "Internode",
                    cls = "N",
                    allowedChildren = listOf(ChildRule("pod", EdgeType.BEARS, "Pod")),
                ),
                NodeTypeDef(
                    name = "pod",
                    displayName = "Pod",
                    cls = "C",
                ),
            ),
        )

    private fun readZipEntries(zipBytes: ByteArray): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val data = zis.readBytes()
                out[entry.name] = data
                zis.closeEntry()
            }
        }
        return out
    }
}

