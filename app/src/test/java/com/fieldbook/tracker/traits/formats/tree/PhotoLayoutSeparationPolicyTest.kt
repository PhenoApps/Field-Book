package com.fieldbook.tracker.traits.formats.tree

import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.traits.TreeTraitLayout
import com.fieldbook.tracker.traits.formats.BasePhotoFormat
import com.fieldbook.tracker.traits.formats.PhotoFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Owner test for trait-tree/09 L3 + L4 (photo layout / request-code separation).
 *
 * Locks Collect photo format on main [R.layout.trait_photo] via [BasePhotoFormat],
 * CameraX path on [R.layout.trait_camera] via [AbstractCameraTrait], node chrome on
 * [R.layout.trait_tree_photo] only, and node capture on
 * [TreeTraitLayout.REQUEST_TREE_NODE_PHOTO] (not [CollectActivity.REQUEST_MEDIA_CODE]).
 *
 * Does not invent a tree photo format or rewire CameraX.
 */
class PhotoLayoutSeparationPolicyTest {

    private val layoutDir = File("src/main/res/layout")

    private val basePhotoFormatSource: String by lazy {
        File("src/main/java/com/fieldbook/tracker/traits/formats/BasePhotoFormat.kt").readText()
    }

    private val abstractCameraTraitSource: String by lazy {
        File("src/main/java/com/fieldbook/tracker/traits/AbstractCameraTrait.kt").readText()
    }

    private val treeTraitLayoutSource: String by lazy {
        File("src/main/java/com/fieldbook/tracker/traits/TreeTraitLayout.kt").readText()
    }

    private val collectActivitySource: String by lazy {
        File("src/main/java/com/fieldbook/tracker/activities/CollectActivity.java").readText()
    }

    private val nodeTraitFieldSource: String by lazy {
        File(
            "src/main/java/com/fieldbook/tracker/traits/composables/collect/NodeTraitField.kt",
        ).readText()
    }

    // --- L3: layout IDs stay separated ---

    @Test
    fun basePhotoFormat_defaultLayoutId_isTraitPhoto_mainParity() {
        assertEquals(R.layout.trait_photo, BasePhotoFormat().defaultLayoutId)
        assertEquals(R.layout.trait_photo, PhotoFormat().defaultLayoutId)
    }

    @Test
    fun basePhotoFormat_source_pinsTraitPhoto_notTreePhoto() {
        assertTrue(
            "BasePhotoFormat must default to main Collect trait_photo",
            basePhotoFormatSource.contains("R.layout.trait_photo"),
        )
        assertFalse(
            "BasePhotoFormat must not point plot photo at trait_tree_photo",
            basePhotoFormatSource.contains("trait_tree_photo"),
        )
    }

    @Test
    fun abstractCameraTrait_layoutId_isTraitCamera() {
        val layoutIdBody = abstractCameraTraitSource
            .substringAfter("override fun layoutId()")
            .substringBefore("fun setImageNa")
        assertTrue(layoutIdBody.contains("R.layout.trait_camera"))
        assertFalse(layoutIdBody.contains("trait_tree_photo"))
        assertFalse(layoutIdBody.contains("trait_photo"))
    }

    @Test
    fun plotAndNodePhotoLayouts_allExist_andStayDistinct() {
        assertTrue(File(layoutDir, "trait_photo.xml").exists())
        assertTrue(File(layoutDir, "trait_camera.xml").exists())
        assertTrue(File(layoutDir, "trait_tree_photo.xml").exists())
        assertNotEquals(R.layout.trait_photo, R.layout.trait_camera)
        assertNotEquals(R.layout.trait_photo, R.layout.trait_tree_photo)
        assertNotEquals(R.layout.trait_camera, R.layout.trait_tree_photo)
    }

    @Test
    fun traitPhotoXml_isPhotoTraitLayoutRoot_mainParity() {
        val xml = File(layoutDir, "trait_photo.xml").readText()
        assertTrue(
            "Collect trait_photo must keep main PhotoTraitLayout root",
            xml.contains("com.fieldbook.tracker.traits.PhotoTraitLayout"),
        )
        assertTrue(xml.contains("android:id=\"@+id/trait_photo_rv\""))
        assertTrue(xml.contains("android:id=\"@+id/capture\""))
        assertFalse(
            "Collect trait_photo must not be rewritten into tree node chrome",
            xml.contains("tree_node_photo_preview"),
        )
    }

    @Test
    fun nodeChrome_inflatesTraitTreePhotoOnly() {
        val photoHost = nodeTraitFieldSource
            .substringAfter("private fun PhotoChromeHost(")
            .substringBefore("internal fun bindPhotoPreview(")
            .substringBefore("private fun bindPhotoPreview(")
        assertTrue(
            "PhotoChromeHost must inflate trait_tree_photo",
            photoHost.contains("R.layout.trait_tree_photo"),
        )
        assertFalse(
            "Node photo chrome must not inflate Collect trait_photo or trait_camera",
            photoHost.contains("R.layout.trait_camera") ||
                Regex("""inflate\(R\.layout\.trait_camera""").containsMatchIn(nodeTraitFieldSource) ||
                Regex("""inflate\(R\.layout\.trait_photo(?!_)""").containsMatchIn(nodeTraitFieldSource),
        )
        assertFalse(
            "must not invent a parallel tree photo format layout id",
            nodeTraitFieldSource.contains("trait_tree_photo_format") ||
                nodeTraitFieldSource.contains("TreePhotoFormat"),
        )
    }

    @Test
    fun traitTreePhotoXml_isPlainViewGroup_notCameraXRoot() {
        val xml = File(layoutDir, "trait_tree_photo.xml").readText()
        assertTrue(xml.contains("android:id=\"@+id/capture\""))
        assertFalse("node chrome must not be Hilt PhotoTraitLayout", xml.contains("PhotoTraitLayout"))
        assertFalse(
            "node chrome must not embed CameraX PreviewView",
            xml.contains("PreviewView") || xml.contains("trait_camera_pv"),
        )
        assertFalse(
            "node chrome must not reuse Collect camera preview card ids",
            xml.contains("trait_camera_cv") || xml.contains("trait_camera_iv"),
        )
    }

    // --- L4: dedicated request code ---

    @Test
    fun requestCodes_treeNodePhoto_distinctFromMediaCode() {
        assertEquals(206, TreeTraitLayout.REQUEST_TREE_NODE_PHOTO)
        assertEquals(102, CollectActivity.REQUEST_MEDIA_CODE)
        assertNotEquals(
            TreeTraitLayout.REQUEST_TREE_NODE_PHOTO,
            CollectActivity.REQUEST_MEDIA_CODE,
        )
    }

    @Test
    fun treeTraitLayout_launchesWithRequestTreeNodePhoto_notMediaCode() {
        val requestBody = treeTraitLayoutSource
            .substringAfter("fun requestNodePhoto")
            .substringBefore("private fun finishNodePhotoSave")
            .substringBefore("private fun clearPendingNodePhoto")
        assertTrue(
            "requestNodePhoto must startActivityForResult with REQUEST_TREE_NODE_PHOTO",
            requestBody.contains("REQUEST_TREE_NODE_PHOTO"),
        )
        assertFalse(
            "node capture must not use REQUEST_MEDIA_CODE",
            requestBody.contains("REQUEST_MEDIA_CODE"),
        )
        assertTrue(requestBody.contains("startActivityForResult"))
        assertTrue(
            "EXTRA_TRAIT_ID must come from study photo trait, not only tree architecture",
            requestBody.contains("photoTrait") || requestBody.contains("traitIdExtra"),
        )
    }

    @Test
    fun treeTraitLayout_appliesCropPrefsBeforeSidecarSave() {
        assertTrue(treeTraitLayoutSource.contains("TreeNodePhotoCrop"))
        assertTrue(treeTraitLayoutSource.contains("ApplyExistingRoi"))
        assertTrue(treeTraitLayoutSource.contains("NeedsDefinition"))
        assertTrue(treeTraitLayoutSource.contains("showCropDialog"))
        assertTrue(treeTraitLayoutSource.contains("handleNodePhotoCropFinished"))
        assertTrue(treeTraitLayoutSource.contains("requestNodePhotoCropDefinition"))
        assertTrue(treeTraitLayoutSource.contains("showNodePhotoSettings"))
        assertTrue(treeTraitLayoutSource.contains("CameraTraitSettingsView"))
        assertTrue(
            collectActivitySource.contains("hasPendingNodePhotoCrop") ||
                collectActivitySource.contains("handleNodePhotoCropFinished"),
        )
        assertTrue(collectActivitySource.contains("requestAndCropImage(int traitId"))
        assertTrue(
            "system camera cache fallback for node photo",
            collectActivitySource.contains("TEMPORARY_IMAGE_NAME"),
        )
    }

    @Test
    fun collectActivity_hasDedicatedTreeNodePhotoCase_notFoldedIntoMedia() {
        assertTrue(
            collectActivitySource.contains("case TreeTraitLayout.REQUEST_TREE_NODE_PHOTO"),
        )
        assertTrue(collectActivitySource.contains("handleNodePhotoResult"))

        val treeCase = collectActivitySource
            .substringAfter("case TreeTraitLayout.REQUEST_TREE_NODE_PHOTO:")
            .substringBefore("case REQUEST_MEDIA_VIDEO_TRAIT:")
            .substringBefore("case REQUEST_MEDIA_CODE:")
        assertTrue(
            "dedicated case must forward to TreeTraitLayout.handleNodePhotoResult",
            treeCase.contains("handleNodePhotoResult"),
        )
        assertFalse(
            "tree node photo case must not call saveAttachedMedia (REQUEST_MEDIA_CODE path)",
            treeCase.contains("saveAttachedMedia"),
        )

        // Comment contract in Collect: do not fold into REQUEST_MEDIA_CODE
        assertTrue(
            collectActivitySource.contains("Do not fold into") &&
                collectActivitySource.contains("REQUEST_MEDIA_CODE"),
        )
    }
}
