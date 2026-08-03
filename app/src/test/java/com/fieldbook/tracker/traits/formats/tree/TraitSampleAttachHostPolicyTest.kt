package com.fieldbook.tracker.traits.formats.tree

import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.TraitLayoutFactory
import com.fieldbook.tracker.traits.composables.constructor.filterAttachableStudyTraits
import com.fieldbook.tracker.traits.composables.constructor.isAttachableTreePaletteTrait
import com.fieldbook.tracker.traits.composables.constructor.isForgottenSoybeanPhantomName
import com.fieldbook.tracker.traits.formats.Formats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Every format in [app/src/main/assets/trait/trait_sample.trt] must be Attachable
 * (photo via chrome host; others via [TraitLayoutFactory.isNodeHostable]).
 * Soybean phantom names must never appear in the Attach pool.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TraitSampleAttachHostPolicyTest {

    /** Formats listed in trait_sample.trt (CSV column "format"). */
    private val traitSampleFormats = listOf(
        "numeric",
        "categorical",
        "percent",
        "date",
        "boolean",
        "text",
        "photo",
        "counter",
        "location",
        "angle",
    )

    @Test
    fun traitSampleTrt_formatsMatchExpectedList() {
        val trt = File("src/main/assets/trait/trait_sample.trt").readLines()
            .drop(1)
            .filter { it.isNotBlank() }
        val formats = trt.map { line ->
            // "name","format",...
            line.split(",").getOrNull(1)?.trim('"') ?: error("bad line: $line")
        }
        assertEquals(traitSampleFormats, formats)
    }

    @Test
    fun everyTraitSampleFormat_isAttachableAndHostedOrPhotoChrome() {
        traitSampleFormats.forEach { format ->
            val trait = TraitObject().apply {
                name = "sample_$format"
                this.format = format
            }
            assertTrue(
                "trait_sample format must be attachable: $format",
                isAttachableTreePaletteTrait(trait),
            )
            when (format) {
                "photo" -> assertFalse(
                    "photo uses PhotoChromeHost, not factory",
                    TraitLayoutFactory.isNodeHostable(format),
                )
                else -> assertTrue(
                    "trait_sample format must be factory-hostable: $format",
                    TraitLayoutFactory.isNodeHostable(format),
                )
            }
        }
    }

    @Test
    fun stopWatchAliases_areHostableAndAttachable() {
        listOf(
            Formats.STOP_WATCH.getDatabaseName(),
            "stopwatch",
            "seconds",
        ).forEach { format ->
            assertTrue(TraitLayoutFactory.isNodeHostable(format))
            assertNotNull(
                TraitLayoutFactory.create(format, androidx.test.core.app.ApplicationProvider.getApplicationContext()),
            )
            assertTrue(
                isAttachableTreePaletteTrait(
                    TraitObject().apply { name = "timer"; this.format = format },
                ),
            )
        }
    }

    @Test
    fun soybeanPhantomNames_neverAttachable() {
        listOf("Node position", "Seed count", "Pod photo", "node position").forEach { name ->
            assertTrue(isForgottenSoybeanPhantomName(name))
            assertFalse(
                isAttachableTreePaletteTrait(
                    TraitObject().apply {
                        this.name = name
                        format = "numeric"
                    },
                ),
            )
        }
        val pool = filterAttachableStudyTraits(
            listOf(
                TraitObject().apply { name = "Node position"; format = "numeric" },
                TraitObject().apply { name = "height"; format = "numeric" },
                TraitObject().apply { name = "Seed count"; format = "counter" },
                TraitObject().apply { name = "Pod photo"; format = "photo" },
                TraitObject().apply { name = "notes"; format = "text" },
            ),
        ).map { it.name }
        assertEquals(listOf("height", "notes"), pool)
    }
}
