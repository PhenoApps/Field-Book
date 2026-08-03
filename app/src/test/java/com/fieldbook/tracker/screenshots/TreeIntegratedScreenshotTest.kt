package com.fieldbook.tracker.screenshots

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Pixel 5 width, very tall viewport for full-length screenshots. */
private const val TALL_SCREEN = "w393dp-h4096dp-normal-long-notround-any-440dpi-keyshidden-nonav"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = TALL_SCREEN)
class TreeIntegratedScreenshotTest {

  @Test fun integrated00_constructor_blank_root() =
    captureConstructor(TreeConstructorIntegratedHarness.Step.BLANK_ROOT, "00_constructor_blank_root")

  @Test fun integrated01_constructor_add_type_dialog() =
    captureConstructor(TreeConstructorIntegratedHarness.Step.ADD_TYPE_DIALOG, "01_constructor_add_type_dialog")

  @Test fun integrated02_constructor_stem_selected() =
    captureConstructor(TreeConstructorIntegratedHarness.Step.STEM_SELECTED, "02_constructor_stem_selected")

  @Test fun integrated03_constructor_branch_plus_connections() =
    captureConstructor(TreeConstructorIntegratedHarness.Step.BRANCH_PLUS_CONNECTIONS, "03_constructor_branch_plus_connections")

  @Test fun integrated04_constructor_palette_open_on_branch() =
    captureConstructor(TreeConstructorIntegratedHarness.Step.PALETTE_OPEN_ON_BRANCH, "04_constructor_palette_open_on_branch")

  @Test fun integrated05_constructor_traits_attached() =
    captureConstructor(TreeConstructorIntegratedHarness.Step.TRAITS_ATTACHED, "05_constructor_traits_attached")

  @Test fun integrated05b_constructor_all_sample_traits_attached() =
    captureConstructor(
      TreeConstructorIntegratedHarness.Step.ALL_SAMPLE_TRAITS_ATTACHED,
      "05b_constructor_all_sample_traits_attached",
    )

  @Test fun integrated06_collect_on_stem_add_buttons() =
    captureCollect(TreeCollectIntegratedHarness.Step.STEM_ADD_BUTTONS, "06_collect_on_stem_add_buttons")

  @Test fun integrated07_collect_on_branch_date_fields() =
    captureCollect(TreeCollectIntegratedHarness.Step.BRANCH_DATE_FIELDS, "07_collect_on_branch_date_fields")

  @Test fun integrated08_collect_overview_summary_footer() =
    captureCollect(TreeCollectIntegratedHarness.Step.OVERVIEW_SUMMARY, "08_collect_overview_summary_footer")

  @Test fun integrated09_collect_locked_navigate_only() =
    captureCollect(TreeCollectIntegratedHarness.Step.LOCKED_NAVIGATE_ONLY, "09_collect_locked_navigate_only")

  @Test fun integrated10_collect_deep_breadcrumb() =
    captureCollect(TreeCollectIntegratedHarness.Step.DEEP_BREADCRUMB, "10_collect_deep_breadcrumb")

  private fun captureConstructor(step: TreeConstructorIntegratedHarness.Step, fileStem: String) {
    launch(TreeConstructorIntegratedHarness::class.java, TreeConstructorIntegratedHarness.EXTRA_STEP, step.name)
      .captureFullLengthRoboImage("src/test/screenshots/integrated/$fileStem.png")
  }

  private fun captureCollect(step: TreeCollectIntegratedHarness.Step, fileStem: String) {
    launch(TreeCollectIntegratedHarness::class.java, TreeCollectIntegratedHarness.EXTRA_STEP, step.name)
      .captureFullLengthRoboImage("src/test/screenshots/integrated/$fileStem.png")
  }

  private fun launch(activityClass: Class<out AppCompatActivity>, extraKey: String, extraValue: String): AppCompatActivity {
    val intent = Intent(ApplicationProvider.getApplicationContext(), activityClass)
      .putExtra(extraKey, extraValue)
      .putExtra(IntegratedScreenshotCapture.EXTRA_FULL_LENGTH, true)
    return Robolectric.buildActivity(activityClass, intent).create().start().resume().visible().get()
  }
}
