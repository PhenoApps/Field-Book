package com.fieldbook.tracker.traits

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Owner test for 09 §3.1 unknown-format fail-closed policy:
 * [TraitLayoutFactory.create] must not soft-host unknown keys as [TextTraitLayout];
 * [TraitLayoutFactory.isNodeHostable] is true only for explicit `when` arms.
 *
 * Collect [LayoutCollections] unknown→text is intentionally unchanged (upstream).
 * [NodeTraitField] still has `?: create("text")` — residual soft-bad, out of scope.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TraitLayoutFactoryUnknownHostPolicyTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val factorySource: String by lazy {
        File("src/main/java/com/fieldbook/tracker/traits/TraitLayoutFactory.kt").readText()
    }

    private val explicitHostKeys = listOf(
        "text",
        "numeric",
        "angle",
        "barcode",
        "boolean",
        "categorical",
        "multicat",
        "qualitative",
        "counter",
        "date",
        "disease rating",
        "location",
        "percent",
        "stop_watch",
        "stopwatch",
        "seconds",
        "audio",
        "gnss",
        "scale",
        "zebra label print",
        "spectral",
        "nix",
        "inno_spectra",
        "green_seeker",
    )

    @Test
    fun create_unknownFormat_returnsNull_notText() {
        val layout = TraitLayoutFactory.create("totally-unknown-format", context)
        assertNull(
            "Unknown format must fail closed (null), not soft TextTraitLayout",
            layout,
        )
        assertFalse(TraitLayoutFactory.isNodeHostable("totally-unknown-format"))
    }

    @Test
    fun isNodeHostable_trueOnlyForExplicitArms() {
        explicitHostKeys.forEach { key ->
            assertTrue(
                "explicit host key must be hostable: $key",
                TraitLayoutFactory.isNodeHostable(key),
            )
            assertNotNull(
                "explicit host key must construct: $key",
                TraitLayoutFactory.create(key, context),
            )
        }

        assertTrue(TraitLayoutFactory.isNodeHostable("audio"))
        assertFalse(TraitLayoutFactory.isNodeHostable("photo"))
        assertFalse(TraitLayoutFactory.isNodeHostable("video"))
        assertFalse(TraitLayoutFactory.isNodeHostable("not_a_real_format"))
        assertFalse(TraitLayoutFactory.isNodeHostable("tree architecture"))
    }

    @Test
    fun isNodeHostable_alignedWithCreate() {
        val probes = explicitHostKeys + listOf(
            "totally-unknown-format",
            "photo",
            "video",
            "not_a_real_format",
        )
        probes.forEach { key ->
            val hostable = TraitLayoutFactory.isNodeHostable(key)
            val created = TraitLayoutFactory.create(key, context)
            assertTrue(
                "isNodeHostable($key)=$hostable must match create nullness (${created != null})",
                hostable == (created != null),
            )
        }
    }

    @Test
    fun source_hasNoSoftTextElseFallback() {
        assertFalse(
            "create must not soft-fall back unknown → TextTraitLayout",
            Regex("""else\s*->\s*TextTraitLayout""").containsMatchIn(factorySource),
        )
        assertTrue(
            "create else arm must fail closed to null",
            Regex("""else\s*->\s*null""").containsMatchIn(factorySource),
        )
        assertTrue(
            "isNodeHostable else arm must be false",
            Regex("""else\s*->\s*false""").containsMatchIn(factorySource),
        )
    }
}
