package com.fieldbook.tracker.traits.formats.tree

import com.fieldbook.tracker.traits.TraitLayoutFactory
import com.fieldbook.tracker.traits.formats.Formats
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Policy lock (09 §3.1): factory blocks photo / camera-family so Collect CameraX is
 * never pulled into Compose; node photo still hosts via PhotoChromeHost +
 * `trait_tree_photo`. Block in factory ≠ no host.
 */
class PhotoFactoryHostPathPolicyTest {

    private val cameraFamilyKeys = listOf(
        "photo",
        Formats.CAMERA.getDatabaseName(),
        Formats.BASE_PHOTO.getDatabaseName(),
        Formats.USB_CAMERA.getDatabaseName(),
        Formats.GO_PRO.getDatabaseName(),
        Formats.CANON.getDatabaseName(),
    )

    @Test
    fun factory_blocksPhotoAndCameraFamily() {
        cameraFamilyKeys.forEach { key ->
            assertFalse(
                "isNodeHostable($key) must be false — photo hosts via PhotoChromeHost, not factory",
                TraitLayoutFactory.isNodeHostable(key),
            )
        }
    }

    @Test
    fun nodeTraitField_routesPhotoToPhotoChromeHost_notFactory() {
        val source = File(
            "src/main/java/com/fieldbook/tracker/traits/composables/collect/NodeTraitField.kt",
        ).readText()

        // Dedicated path: isPhotoFormat → PhotoChromeHost + trait_tree_photo
        assertTrue(source.contains("isPhotoFormat(format)"))
        assertTrue(source.contains("PhotoChromeHost("))
        assertTrue(source.contains("R.layout.trait_tree_photo"))
        assertTrue(source.contains("Formats.CAMERA.getDatabaseName()"))
        assertTrue(source.contains("Formats.BASE_PHOTO.getDatabaseName()"))
        assertTrue(
            source.contains("""key == "photo"""") ||
                Regex("""equals\("photo",\s*ignoreCase\s*=\s*true\)""").containsMatchIn(source),
        )

        // PhotoChromeHost body must not pull factory / Collect CameraX layouts
        val photoHost = source
            .substringAfter("private fun PhotoChromeHost(")
            .substringBefore("\ninternal fun bindPhotoPreview(")
        assertTrue(photoHost.contains("inflate(R.layout.trait_tree_photo"))
        assertFalse("PhotoChromeHost must not call TraitLayoutFactory", photoHost.contains("TraitLayoutFactory"))
        assertFalse(photoHost.contains("PhotoTraitLayout"))
        assertFalse(photoHost.contains("R.layout.trait_camera"))

        // Factory create stays on the non-photo CollectLayoutHost path only
        val collectHost = source
            .substringAfter("private fun CollectLayoutHost(")
            .substringBefore("\nprivate data class HostState")
        assertTrue(collectHost.contains("TraitLayoutFactory.create"))
    }

    @Test
    fun factorySource_listsPhotoUnsupported_noCreateArm() {
        val source = File(
            "src/main/java/com/fieldbook/tracker/traits/TraitLayoutFactory.kt",
        ).readText()
        assertTrue(source.contains("Formats.CAMERA.getDatabaseName()"))
        assertTrue(source.contains("Formats.BASE_PHOTO.getDatabaseName()"))
        assertTrue(source.contains("Formats.USB_CAMERA.getDatabaseName()"))
        assertTrue(source.contains("Formats.GO_PRO.getDatabaseName()"))
        assertTrue(source.contains("Formats.CANON.getDatabaseName()"))
        assertTrue(source.contains("\"photo\""))
        // No when-arm that constructs a photo layout
        assertFalse(source.contains("PhotoTraitLayout"))
        assertFalse(Regex("""key\s*==\s*"photo"\s*->""").containsMatchIn(source))
    }
}
