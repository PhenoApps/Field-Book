package com.fieldbook.tracker.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.ui.components.widgets.ThreeStateToggle
import com.fieldbook.tracker.ui.theme.LocalAppColors
import com.fieldbook.tracker.ui.theme.colors.SodaDarkAppColors
import com.fieldbook.tracker.ui.theme.colors.SodaDarkPalette
import com.fieldbook.tracker.utilities.AppThemeResolver
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Build.VERSION_CODES.P])
class ThreeStateToggleSodaDarkRenderingTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext()).edit()
            .putString(PreferenceKeys.THEME, ThemedActivity.SODA_DARK.toString())
            .apply()
    }

    @Test
    fun sodaDark_photoAudio_rendersBrightSelectedIconNotBlack() {
        mountProductionToggle(selectedIndex = 0)

        composeTestRule.waitForIdle()
        val rgb = composeTestRule
            .onNodeWithContentDescription("Photo")
            .captureToImage()
            .dominantOpaqueRgb()

        ToggleRenderingAssertions.assertNotBlack(rgb, "selected photo icon")
        ToggleRenderingAssertions.assertRgbNear(
            0xA6E5FF,
            rgb,
            "selected photo icon",
        )
    }

    @Test
    fun sodaDark_disabledVideo_rendersVisibleGrayIcon() {
        mountProductionToggle(selectedIndex = 0)

        composeTestRule.waitForIdle()
        val rgb = composeTestRule
            .onNodeWithContentDescription("Video")
            .captureToImage()
            .dominantOpaqueRgb()

        ToggleRenderingAssertions.assertNotBlack(rgb, "disabled video icon")
        ToggleRenderingAssertions.assertRgbNear(
            0xCCCCCC,
            rgb,
            "disabled video icon",
        )
    }

    @Test
    fun sodaDark_photoAudio_rendersAudioIcon() {
        mountProductionToggle(selectedIndex = 2)

        composeTestRule.waitForIdle()
        val rgb = composeTestRule
            .onNodeWithContentDescription("Audio")
            .captureToImage()
            .dominantOpaqueRgb()

        ToggleRenderingAssertions.assertNotBlack(rgb, "selected audio icon")
        ToggleRenderingAssertions.assertRgbNear(
            0xA6E5FF,
            rgb,
            "selected audio icon",
        )
    }

    @Test
    fun sodaDark_threeSlots_evenlySpaced() {
        mountProductionToggle(selectedIndex = 0)

        composeTestRule.waitForIdle()
        val photo = composeTestRule.onNodeWithContentDescription("Photo").fetchSemanticsNode().boundsInRoot
        val video = composeTestRule.onNodeWithContentDescription("Video").fetchSemanticsNode().boundsInRoot
        val audio = composeTestRule.onNodeWithContentDescription("Audio").fetchSemanticsNode().boundsInRoot

        ToggleRenderingAssertions.assertEvenSlotSpacing(photo, video, audio)
    }

    @Test
    fun composeAppColors_matchesSodaDarkToggleConfig() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
        val toggle = AppThemeResolver.composeAppColors(prefs).toggle
        assertEquals(SodaDarkPalette.AccentBright, toggle.icon)
        assertEquals(SodaDarkPalette.Panel, toggle.track)
    }

    private fun mountProductionToggle(selectedIndex: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
        val colors = AppThemeResolver.composeAppColors(prefs)
        val labels = listOf("Photo", "Video", "Audio")

        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppColors provides colors) {
                val painters = listOf(
                    painterResource(R.drawable.ic_media_toggle_photo),
                    painterResource(R.drawable.ic_media_toggle_video),
                    painterResource(R.drawable.ic_media_toggle_audio),
                )
                ThreeStateToggle(
                    states = painters,
                    selectedIndex = selectedIndex,
                    onSelected = {},
                    contentDescriptions = labels,
                    enabledStates = listOf(true, false, true),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.ImageBitmap.dominantOpaqueRgb(): Int =
    with(ToggleRenderingAssertions) { dominantOpaqueRgb() }
