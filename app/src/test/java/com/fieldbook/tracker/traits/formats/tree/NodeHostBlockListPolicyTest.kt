package com.fieldbook.tracker.traits.formats.tree

import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.TraitLayoutFactory
import com.fieldbook.tracker.traits.composables.constructor.isAttachableTreePaletteTrait
import com.fieldbook.tracker.traits.composables.constructor.isUnsupportedTreePaletteFormat
import com.fieldbook.tracker.traits.formats.Formats
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Policy (09 §3.1): Constructor [UnsupportedFormats] ⊆ [TraitLayoutFactory] unsupported.
 *
 * Factory may block additional Collect-coupled formats; do not require equality.
 * Blocking Attach is allowed; inventing tree-* replacements is not.
 */
class NodeHostBlockListPolicyTest {

    @Test
    fun constructorUnsupportedFormats_subsetOfFactoryUnsupported() {
        val documentedConstructorBlocked = listOf(
            Formats.VIDEO.getDatabaseName(),
            Formats.USB_CAMERA.getDatabaseName(),
            Formats.GO_PRO.getDatabaseName(),
            Formats.CANON.getDatabaseName(),
            Formats.TREE_ARCHITECTURE.getDatabaseName(),
            Formats.TREE_SUMMARY.getDatabaseName(),
        )

        for (key in documentedConstructorBlocked) {
            assertTrue(
                "Constructor palette must still block documented key: $key",
                isUnsupportedTreePaletteFormat(key),
            )
        }

        // Discover Constructor block list via public API (may grow; factory may be broader).
        val constructorBlocked = Formats.entries
            .map { it.getDatabaseName() }
            .filter { isUnsupportedTreePaletteFormat(it) }
            .toSet()

        assertTrue(
            "Discovered Constructor block list must include documented keys",
            constructorBlocked.containsAll(documentedConstructorBlocked),
        )

        for (key in constructorBlocked) {
            assertFalse(
                "Factory must refuse node host for Constructor-blocked format: $key",
                TraitLayoutFactory.isNodeHostable(key),
            )
        }
    }

    @Test
    fun newlyHostedFormats_areNodeHostable() {
        listOf(
            Formats.AUDIO.getDatabaseName(),
            Formats.GNSS.getDatabaseName(),
            Formats.SCALE.getDatabaseName(),
            Formats.LABEL_PRINT.getDatabaseName(),
            Formats.STOP_WATCH.getDatabaseName(),
            "seconds",
            Formats.NIX.getDatabaseName(),
            Formats.INNO_SPECTRA_SENSOR.getDatabaseName(),
            Formats.GREEN_SEEKER.getDatabaseName(),
            Formats.BASE_SPECTRAL.getDatabaseName(),
        ).forEach { key ->
            assertTrue(
                "expected hostable study format: $key",
                TraitLayoutFactory.isNodeHostable(key),
            )
            assertTrue(
                "expected attachable in Constructor palette: $key",
                isAttachableTreePaletteTrait(
                    TraitObject().apply { name = "t_$key"; format = key },
                ),
            )
            assertFalse(
                "must not be on Constructor block list: $key",
                isUnsupportedTreePaletteFormat(key),
            )
        }
    }

    @Test
    fun photo_isAttachableViaChromeHost_notFactoryHostable() {
        assertFalse(TraitLayoutFactory.isNodeHostable("photo"))
        assertFalse(TraitLayoutFactory.isNodeHostable(Formats.CAMERA.getDatabaseName()))
        assertFalse(isUnsupportedTreePaletteFormat("photo"))
        assertTrue(
            isAttachableTreePaletteTrait(
                TraitObject().apply { name = "branch photo"; format = "photo" },
            ),
        )
    }

    @Test
    fun traitSampleFormats_areAttachable() {
        listOf(
            "numeric", "categorical", "percent", "date", "boolean",
            "text", "photo", "counter", "location", "angle",
        ).forEach { format ->
            assertTrue(
                "trait_sample format must attach: $format",
                isAttachableTreePaletteTrait(
                    TraitObject().apply { name = "t_$format"; this.format = format },
                ),
            )
        }
    }

    @Test
    fun topologyShells_notAttachableInPalette() {
        listOf(
            Formats.TREE_ARCHITECTURE.getDatabaseName(),
            Formats.TREE_SUMMARY.getDatabaseName(),
            Formats.VIDEO.getDatabaseName(),
        ).forEach { format ->
            assertFalse(
                isAttachableTreePaletteTrait(
                    TraitObject().apply { name = "x"; this.format = format },
                ),
            )
        }
    }
}
