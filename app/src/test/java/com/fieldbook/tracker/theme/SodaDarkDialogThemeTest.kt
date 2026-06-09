package com.fieldbook.tracker.theme

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.AppThemeResolver
import com.fieldbook.tracker.utilities.ThemedAlertDialog
import com.google.android.material.color.MaterialColors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rendering tests for Soda Dark alert dialogs via production [ThemedAlertDialog] paths.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class SodaDarkDialogThemeTest {

    private lateinit var appContext: Context
    private lateinit var sodaActivityContext: Context
    private lateinit var preferenceActivityContext: Context

    private val sodaWindow by lazy { ContextCompat.getColor(appContext, R.color.soda_dark_window) }
    private val sodaRow by lazy { ContextCompat.getColor(appContext, R.color.soda_dark_row) }
    private val sodaBright by lazy { ContextCompat.getColor(appContext, R.color.soda_dark_text_bright) }
    private val whiteRgb by lazy { Color.WHITE and 0x00FFFFFF }

    private val openDialogs = mutableListOf<AlertDialog>()

    private val customViewCases = listOf(
        CustomViewCase(R.layout.dialog_field_edit_name, R.string.field_edit_display_name, "edit display name"),
        CustomViewCase(R.layout.dialog_field_sort, R.string.dialog_field_sort_title, "sort entries"),
        CustomViewCase(R.layout.dialog_collect_att_chooser, R.string.search_attribute_dialog_title, "set search id"),
        CustomViewCase(R.layout.dialog_export, R.string.settings_export, "export"),
        CustomViewCase(R.layout.dialog_trait_creator, R.string.traits_new_dialog_title, "trait layout"),
    )

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(appContext).edit()
            .putString(PreferenceKeys.THEME, ThemedActivity.SODA_DARK.toString())
            .apply()
        sodaActivityContext = ContextThemeWrapper(appContext, R.style.BaseAppTheme_SodaDark_MediumTextTheme)
        preferenceActivityContext = ContextThemeWrapper(appContext, R.style.PreferenceTheme_SodaDark)
    }

    @After
    fun tearDown() {
        openDialogs.forEach { if (it.isShowing) it.dismiss() }
        openDialogs.clear()
    }

    @Test
    fun showPath_messageOnly_rendersDarkPanelAndBrightTitle() {
        val dialog = ThemedAlertDialog.builder(sodaActivityContext)
            .setTitle(R.string.fields_delete_study)
            .setMessage(R.string.fields_delete_permanent_warning)
            .show()
        track(dialog)
        DialogChromeAssertions.idleAfterShow()
        assertChrome(dialog, "delete confirmation")
    }

    @Test
    fun showPath_httpWarningPreferenceContext_rendersDarkPanel() {
        assertEquals(
            R.style.AppAlertDialog_Preference_SodaDark,
            AppThemeResolver.alertDialogStyle(preferenceActivityContext),
        )
        val dialog = ThemedAlertDialog.builder(preferenceActivityContext)
            .setTitle(R.string.act_brapi_auth_http_warning_title)
            .setMessage(R.string.act_brapi_auth_http_warning_message)
            .show()
        track(dialog)
        DialogChromeAssertions.idleAfterShow()
        assertChrome(dialog, "HTTP warning")
    }

    @Test
    fun createShowPath_messageOnly_rendersDarkPanelWithoutManualChrome() {
        val dialog = ThemedAlertDialog.builder(sodaActivityContext)
            .setTitle(R.string.fields_new_dialog_title)
            .setMessage(R.string.dialog_cancel)
            .create()
        dialog.show()
        track(dialog)
        DialogChromeAssertions.idleAfterShow()
        assertChrome(dialog, "new field message (create().show())")
    }

    @Test
    fun chainOnShowPath_rendersDarkPanelWithoutManualChrome() {
        val dialog = ThemedAlertDialog.builder(sodaActivityContext)
            .setTitle(R.string.field_edit_display_name)
            .setPositiveButton(R.string.dialog_save, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
        ThemedAlertDialog.chainOnShow(dialog, sodaActivityContext, null)
        dialog.show()
        track(dialog)
        DialogChromeAssertions.idleAfterShow()
        assertChrome(dialog, "edit display name (chainOnShow)")
    }

    @Test
    fun showPath_customViewDialogs_rendersDarkPanelAndContent() {
        customViewCases.forEach { case ->
            val content = ThemedAlertDialog.inflate(sodaActivityContext, case.layoutRes)
            val rootBg = MaterialColors.getColor(content.context, R.attr.fb_color_background, Color.WHITE)
            DialogChromeAssertions.assertColorEquals("${case.label} content bg", sodaWindow, rootBg)

            val dialog = ThemedAlertDialog.builder(sodaActivityContext)
                .setTitle(case.titleRes)
                .setView(content)
                .setPositiveButton(R.string.dialog_ok, null)
                .show()
            track(dialog)
            DialogChromeAssertions.idleAfterShow()
            assertChrome(dialog, case.label)
        }
    }

    @Test
    fun showPath_newFieldListAddPattern_rendersDarkPanelAndContent() {
        val dialogContext = ThemedAlertDialog.contentContext(sodaActivityContext)
        val surfaceColor = MaterialColors.getColor(dialogContext, R.attr.fb_color_background, Color.WHITE)
        assertEquals(sodaWindow, surfaceColor)

        val layout = LinearLayout(dialogContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(surfaceColor)
        }

        val dialog = ThemedAlertDialog.builder(sodaActivityContext)
            .setTitle(R.string.fields_new_dialog_title)
            .setView(layout)
            .setPositiveButton(R.string.dialog_cancel, null)
            .show()
        track(dialog)
        DialogChromeAssertions.idleAfterShow()
        assertChrome(dialog, "new field (ListAdd pattern)")
        DialogChromeAssertions.assertColorEquals(
            "ListAdd custom root background",
            sodaWindow,
            layout.backgroundColor(),
        )
    }

    @Test
    fun lightTheme_showPath_doesNotForceSodaPanelColor() {
        PreferenceManager.getDefaultSharedPreferences(appContext).edit()
            .putString(PreferenceKeys.THEME, ThemedActivity.DEFAULT.toString())
            .apply()
        val lightActivity = ContextThemeWrapper(appContext, R.style.BaseAppTheme_MediumTextTheme)

        val dialog = ThemedAlertDialog.builder(lightActivity)
            .setTitle(R.string.fields_new_dialog_title)
            .setMessage(R.string.dialog_cancel)
            .show()
        track(dialog)
        DialogChromeAssertions.idleAfterShow()

        val panelColor = dialog.requireParentPanel("light theme").backgroundColor()
        if (panelColor != null) {
            assertNotEquals("Light theme must not paint Soda row color on panel", sodaRow, panelColor)
        }
    }

    @Test
    fun resolverAndAlertDialogStyle_useSodaDarkResources() {
        assertEquals(R.style.AppAlertDialog_SodaDark, AppThemeResolver.alertDialogStyle(sodaActivityContext))
        val dialogContext = ContextThemeWrapper(sodaActivityContext, R.style.AppAlertDialog_SodaDark)
        val styleValue = android.util.TypedValue()
        assertTrue(
            dialogContext.theme.resolveAttribute(
                androidx.appcompat.R.attr.alertDialogStyle,
                styleValue,
                true,
            ),
        )
        assertEquals(R.style.AlertDialog_FieldBook_SodaDark, styleValue.resourceId)
    }

    private fun assertChrome(dialog: AlertDialog, label: String) {
        DialogChromeAssertions.assertRenderedAlertChrome(dialog, label, sodaRow, sodaBright, whiteRgb)
    }

    private fun track(dialog: AlertDialog) {
        openDialogs.add(dialog)
    }

    private fun AlertDialog.requireParentPanel(label: String): View =
        with(DialogChromeAssertions) { requireParentPanel(label) }

    private fun View.backgroundColor(): Int? = with(DialogChromeAssertions) { backgroundColor() }

    private data class CustomViewCase(
        @LayoutRes val layoutRes: Int,
        @StringRes val titleRes: Int,
        val label: String,
    )
}
