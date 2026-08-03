package com.fieldbook.tracker.objects

import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.utilities.TreeSchemaLoader
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraitJsonEmbeddedSchemaTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun treeTraitJson_serializesEmbeddedSchema() {
        val trait = TraitObject().apply {
            name = "soy tree-carrier"
            alias = name
            synonyms = listOf(name)
            format = Formats.TREE_ARCHITECTURE.getDatabaseName()
            resourceFile = "content://schemas/soy_tree.json"
        }
        val schema = """{"id":"soy_tree","rootType":"root","nodeTypes":[]}"""

        val encoded = Json.encodeToString(TraitJson.serializer(), trait.toTraitJson(embeddedSchema = schema))
        val decoded = Json.decodeFromString(TraitJson.serializer(), encoded)

        assertEquals(schema, decoded.embeddedSchema)
        assertTrue(encoded.contains("embeddedSchema"))
        // Schema body is a JSON string field, so keys appear escaped in the .trt payload.
        assertTrue(encoded.contains("rootType"))
        assertTrue(decoded.embeddedSchema!!.contains("\"rootType\""))
        assertTrue(decoded.embeddedSchema!!.contains("\"nodeTypes\""))
    }

    @Test
    fun legacyUriOnlyTrt_hasResourceFileButNoEmbeddedSchema() {
        val trt = loadAttachedLegacyExport()
        val wrapper = json.decodeFromString(TraitImportFile.serializer(), trt)

        val tree = wrapper.traits.single { it.format == Formats.TREE_ARCHITECTURE.getDatabaseName() }
        assertEquals("Архитектура сои", tree.name)
        assertNull(tree.embeddedSchema)

        val resourceUri = tree.attributes?.get("resourceFile")?.jsonPrimitive?.content
        assertNotNull(resourceUri)
        assertTrue(resourceUri!!.startsWith("content://"))
        assertTrue(resourceUri.contains("soy_arch_v1_2026-07-30-11-48-55.json"))

        // Without embeddedSchema, import cannot rewrite a local schema leaf from the bundle alone.
        val recoveredLeaf = TreeSchemaLoader.preferredImportFileName(resourceUri, tree.name)
        assertEquals("soy_arch_v1_2026-07-30-11-48-55.json", recoveredLeaf)
        // The .trt itself still carries only the dead device URI — no schema body to restore.
        assertFalse(trt.contains("embeddedSchema"))
        assertFalse(trt.contains("\"rootType\""))
    }

    @Test
    fun embeddedSchema_roundTripsIndependentlyOfOriginalResourceUri() {
        val schemaBody =
            """{"id":"soy_tree_carrier_v1","name":"soy tree-carrier","version":1,"rootType":"root","nodeTypes":[]}"""
        val deadDeviceUri =
            "content://com.android.externalstorage.documents/tree/primary%3Afield/document/primary%3Afield%2Fresources%2Fsoy_arch_v1.json"

        val exported = TraitObject().apply {
            name = "soy tree-carrier"
            alias = name
            synonyms = listOf(name)
            format = Formats.TREE_ARCHITECTURE.getDatabaseName()
            resourceFile = deadDeviceUri
        }.toTraitJson(embeddedSchema = schemaBody)

        val bundle = json.encodeToString(
            TraitImportFile.serializer(),
            TraitImportFile(listOf(exported)),
        )
        val imported = json.decodeFromString(TraitImportFile.serializer(), bundle).traits.single()

        assertEquals(schemaBody, imported.embeddedSchema)
        assertTrue(bundle.contains("embeddedSchema"))
        // Import path uses embedded body + preferred leaf; original SAF URI need not resolve.
        assertEquals(
            "soy_arch_v1.json",
            TreeSchemaLoader.preferredImportFileName(deadDeviceUri, "soy tree-carrier"),
        )
        val trait = TraitObject.fromJson(imported, maxPosition = 0, originalFileName = "export.trt")
        assertEquals(Formats.TREE_ARCHITECTURE.getDatabaseName(), trait.format)
        assertEquals(deadDeviceUri, trait.resourceFile)
        assertNotNull(imported.embeddedSchema)
    }

    private fun loadAttachedLegacyExport(): String {
        val candidates = listOf(
            File("trait-tree/trait_export_2026-07-30-11-49-30.trt"),
            File("../trait-tree/trait_export_2026-07-30-11-49-30.trt"),
            File("/root/development/Field-Book/trait-tree/trait_export_2026-07-30-11-49-30.trt"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Missing trait-tree/trait_export_2026-07-30-11-49-30.trt fixture")
        return file.readText()
    }
}
