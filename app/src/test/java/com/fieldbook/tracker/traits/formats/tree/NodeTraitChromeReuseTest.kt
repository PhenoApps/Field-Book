package com.fieldbook.tracker.traits.formats.tree

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Proves node fields reuse real Field Book trait layout XMLs (not parallel *_node_host copies).
 */
class NodeTraitChromeReuseTest {

    private val layoutDir = File("src/main/res/layout")

    @Test
    fun photoChrome_usesTreePhotoLayout_cameraFab() {
        val xml = File(layoutDir, "trait_tree_photo.xml").readText()
        assertTrue(xml.contains("android:id=\"@+id/capture\""))
        assertTrue(xml.contains("android:id=\"@+id/tree_node_photo_settings\""))
        assertTrue(xml.contains("FloatingActionButton"))
        assertTrue(xml.contains("@drawable/ic_trait_camera"))
        assertFalse(xml.contains("Capture photo", ignoreCase = true))
        assertFalse("photo chrome must not be Hilt PhotoTraitLayout root", xml.contains("PhotoTraitLayout"))
        // Collect main layout stays on disk; nodes must not use it (see nodeTraitField test).
        assertTrue(File(layoutDir, "trait_photo.xml").exists())
        assertFalse(File(layoutDir, "trait_photo_node_host.xml").exists())
    }

    @Test
    fun dateLayout_exposesNativeDateFabs() {
        val xml = File(layoutDir, "trait_date.xml").readText()
        listOf(
            "minusDateBtn",
            "addDateBtn",
            "enterBtn",
            "trait_date_calendar_visibility_btn",
            "datePreviewText",
        ).forEach { id ->
            assertTrue("missing @$id", xml.contains("@+id/$id"))
        }
        assertTrue(xml.contains("FloatingActionButton"))
        assertFalse(File(layoutDir, "trait_date_node_host.xml").exists())
    }

    @Test
    fun numericLayout_exposesKeypadButtons() {
        val xml = File(layoutDir, "trait_numeric.xml").readText()
        (1..16).forEach { n ->
            assertTrue("missing k$n", xml.contains("@+id/k$n"))
        }
        assertTrue(xml.contains("android:text=\"1\""))
        assertTrue(xml.contains("android:text=\"0\""))
        assertFalse(File(layoutDir, "trait_numeric_node_host.xml").exists())
    }

    @Test
    fun treeTraitLayout_launchesCameraActivity() {
        val source = File("src/main/java/com/fieldbook/tracker/traits/TreeTraitLayout.kt").readText()
        assertTrue(source.contains("CameraActivity::class.java"))
        assertTrue(source.contains("CameraActivity.MODE_PHOTO"))
        assertTrue(source.contains("fun requestNodePhoto"))
        assertTrue(source.contains("fun showNodePhotoSettings"))
        assertTrue(source.contains("CameraTraitSettingsView"))
        assertTrue(source.contains("GeneralKeys.CAMERA_SYSTEM"))
        assertTrue(source.contains("ACTION_IMAGE_CAPTURE") || source.contains("MediaStore.ACTION_IMAGE_CAPTURE"))
        // CameraActivity.onCreate finishes early without these extras
        assertTrue(source.contains("CameraActivity.EXTRA_STUDY_ID"))
        assertTrue(source.contains("CameraActivity.EXTRA_OBS_UNIT"))
        assertTrue(source.contains("CameraActivity.EXTRA_TRAIT_ID"))
        assertTrue(source.contains("CameraActivity.EXTRA_SKIP_SAVE"))
        assertTrue(source.contains("CameraActivity.EXTRA_LAUNCHED_FOR_PHOTO_TRAIT"))
        assertTrue(source.contains("REQUEST_TREE_NODE_PHOTO"))
        assertTrue(source.contains("pendingPhotoUnitId"))
        assertTrue(source.contains("checkNodePictureLimit") || source.contains("traits_create_photo_maximum"))
        assertFalse(source.contains("Capture photo", ignoreCase = true))
    }

    @Test
    fun cameraActivity_photoResultIntent_matchesShutterContract() {
        val source = File("src/main/java/com/fieldbook/tracker/activities/CameraActivity.kt").readText()
        assertTrue(source.contains("fun finishWithCapturedPhoto"))
        assertTrue(source.contains("fun photoResultIntent"))
        assertTrue(source.contains("EXTRA_LAUNCHED_FOR_PHOTO_TRAIT"))
        // skip_save returns cache temp path (caller owns storage)
        assertTrue(source.contains("if (skipSaveFlag)"))
        assertTrue(source.contains("tmp.absolutePath"))
    }

    @Test
    fun collectActivity_forwardsTreeNodePhotoResult() {
        val source = File(
            "src/main/java/com/fieldbook/tracker/activities/CollectActivity.java",
        ).readText()
        assertTrue(source.contains("TreeTraitLayout.REQUEST_TREE_NODE_PHOTO"))
        assertTrue(source.contains("handleNodePhotoResult"))
        // Cancel path must clear pending so a stale capture cannot apply later
        assertTrue(
            source.contains("handleNodePhotoResult(null)") ||
                source.contains("handleNodePhotoResult(null);"),
        )
    }

    @Test
    fun nodeTraitField_hostsRealLayouts_viaFactoryAndSession() {
        val source = File(
            "src/main/java/com/fieldbook/tracker/traits/composables/collect/NodeTraitField.kt",
        ).readText()
        assertTrue(source.contains("TraitLayoutFactory"))
        assertTrue(source.contains("NodeTraitValueSession"))
        assertTrue(source.contains("controller.layoutId()"))
        assertTrue(source.contains("R.layout.trait_tree_photo"))
        assertTrue(source.contains("onRequestPhoto"))
        assertTrue(source.contains("onRequestPhotoCropSettings") || source.contains("onRequestPhotoSettings"))
        assertTrue(source.contains("showNodePhotoSettings") || source.contains("onRequestPhotoSettings") || source.contains("tree_node_photo_settings"))
        assertFalse(source.contains("trait_photo_node_host"))
        assertFalse(source.contains("trait_date_node_host"))
        assertFalse(source.contains("trait_numeric_node_host"))
        assertFalse(source.contains("Capture photo", ignoreCase = true))
        assertFalse(
            "node host must not inflate Collect trait_photo (use trait_tree_photo)",
            Regex("""R\.layout\.trait_photo(?!_)""").containsMatchIn(source),
        )
    }

    @Test
    fun traitLayoutFactory_mapsCommonFormats() {
        val source = File(
            "src/main/java/com/fieldbook/tracker/traits/TraitLayoutFactory.kt",
        ).readText()
        listOf(
            "date",
            "numeric",
            "location",
            "zebra label print",
            "counter",
            "boolean",
            "percent",
            "barcode",
            "angle",
            "audio",
            "gnss",
            "scale",
            "stop_watch",
            "seconds",
        ).forEach { assertTrue("factory missing $it", source.contains("\"$it\"")) }
        assertTrue(source.contains("isNodeHostable"))
        assertTrue(source.contains("StopWatchTraitLayout"))
        assertTrue(source.contains("AudioTraitLayout"))
    }
}
