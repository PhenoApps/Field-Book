package com.fieldbook.tracker.screenshots

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/** Pixel 5 width; tall enough for full dialog/list scenes. */
private const val REVIEW_SCREEN = "w393dp-h1200dp-normal-long-notround-any-440dpi-keyshidden-nonav"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = REVIEW_SCREEN)
class SodaDarkReviewScreenshotTest {

    @Test fun review01_traits_resource_icons() =
        capture(SodaDarkReviewHarness.Scene.TRAITS_RESOURCE_ICONS, "01_traits_resource_icons")

    @Test fun review02_summary_home_and_nav() =
        capture(SodaDarkReviewHarness.Scene.SUMMARY_HOME_AND_NAV, "02_summary_home_and_nav")

    @Test fun review03_collect_audio_trait() =
        capture(SodaDarkReviewHarness.Scene.COLLECT_AUDIO_TRAIT, "03_collect_audio_trait")

    @Test fun review04_field_export_dialog() =
        capture(SodaDarkReviewHarness.Scene.FIELD_EXPORT_DIALOG, "04_field_export_dialog")

    @Test fun review05_field_delete_dialog() =
        capture(SodaDarkReviewHarness.Scene.FIELD_DELETE_DIALOG, "05_field_delete_dialog")

    @Test fun review06_preference_settings_dialog() =
        capture(SodaDarkReviewHarness.Scene.PREFERENCE_SETTINGS_DIALOG, "06_preference_settings_dialog")

    @Test fun review07_field_sort_icons() =
        capture(SodaDarkReviewHarness.Scene.FIELD_SORT_ICONS, "07_field_sort_icons")

    @Test fun review08_data_grid_headers() =
        capture(SodaDarkReviewHarness.Scene.DATA_GRID_HEADERS, "08_data_grid_headers")

    @Test fun review09_field_detail_stats_axis() =
        capture(SodaDarkReviewHarness.Scene.FIELD_DETAIL_STATS_AXIS, "09_field_detail_stats_axis")

    @Test fun review10_brapi_sync_title() =
        capture(SodaDarkReviewHarness.Scene.BRAPI_SYNC_TITLE, "10_brapi_sync_title")

    @Test fun review11_brapi_importer_chips() =
        capture(SodaDarkReviewHarness.Scene.BRAPI_IMPORTER_CHIPS, "11_brapi_importer_chips")

    @Test fun review12_brapi_server_checker() =
        capture(SodaDarkReviewHarness.Scene.BRAPI_SERVER_CHECKER, "12_brapi_server_checker")

    @Test fun review13_attach_media_audio_icon() =
        capture(SodaDarkReviewHarness.Scene.ATTACH_MEDIA_AUDIO_ICON, "13_attach_media_audio_icon")

    @Test fun review14_define_crop_region_mask() =
        capture(SodaDarkReviewHarness.Scene.DEFINE_CROP_REGION_MASK, "14_define_crop_region_mask")

    @Test fun review15_collect_photo_settings() =
        capture(SodaDarkReviewHarness.Scene.COLLECT_PHOTO_SETTINGS, "15_collect_photo_settings")

    private fun capture(scene: SodaDarkReviewHarness.Scene, fileStem: String) {
        applyForcedThemePrefs(ApplicationProvider.getApplicationContext())
        val intent = Intent(ApplicationProvider.getApplicationContext(), SodaDarkReviewHarness::class.java)
            .putExtra(SodaDarkReviewHarness.EXTRA_SCENE, scene.name)
        val activity = Robolectric.buildActivity(SodaDarkReviewHarness::class.java, intent)
            .create()
            .start()
            .resume()
            .visible()
            .get()
        // Crop mask draws in post{}; dialogs need a frame after show().
        ShadowLooper.idleMainLooper()
        activity.window.decorView.post {}
        ShadowLooper.idleMainLooper()
        activity.captureReviewRoboImage("src/test/screenshots/soda-dark-pr1477/$fileStem.png")
    }
}
