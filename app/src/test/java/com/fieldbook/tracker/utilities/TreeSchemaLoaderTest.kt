package com.fieldbook.tracker.utilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeSchemaLoaderTest {

    @Test
    fun schemaLeaf_usesFileNameForContentUri() {
        val resourceUri =
            "content://com.android.externalstorage.documents/tree/primary%3Afield/document/primary%3Afield%2Fresources%2Fsoy_arch_v1_2026-07-30-11-48-55.json"

        assertEquals(
            "soy_arch_v1_2026-07-30-11-48-55.json",
            TreeSchemaLoader.schemaLeaf(resourceUri),
        )
    }

    @Test
    fun schemaLeaf_keepsPlainLeafName() {
        assertEquals("soy_arch_v1.json", TreeSchemaLoader.schemaLeaf("soy_arch_v1.json"))
    }

    @Test
    fun preferredImportFileName_prefersSchemaLeafWhenPresent() {
        val resourceUri =
            "content://com.android.externalstorage.documents/tree/primary%3Afield/document/primary%3Afield%2Fresources%2Fsoy_arch_v1_2026-07-30-11-48-55.json"

        assertEquals(
            "soy_arch_v1_2026-07-30-11-48-55.json",
            TreeSchemaLoader.preferredImportFileName(resourceUri, "soy tree-carrier"),
        )
    }

    @Test
    fun preferredImportFileName_fallsBackToTraitName() {
        assertEquals(
            "soy tree-carrier_schema.trt",
            TreeSchemaLoader.preferredImportFileName(null, "soy tree-carrier"),
        )
    }

    @Test
    fun isReadOnlyAssetRef_detectsAssetPaths() {
        assertTrue(TreeSchemaLoader.isReadOnlyAssetRef("assets/trait/sample.json"))
        assertTrue(TreeSchemaLoader.isReadOnlyAssetRef("trait/tree_soy_arch_sample.trt"))
        assertFalse(TreeSchemaLoader.isReadOnlyAssetRef("soy_arch_v1.json"))
        assertFalse(
            TreeSchemaLoader.isReadOnlyAssetRef(
                "content://com.android.externalstorage.documents/tree/primary%3Afield/document/primary%3Afield%2Fresources%2Fsoy.json",
            ),
        )
    }
}
