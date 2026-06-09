package com.fieldbook.tracker.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Rendering tests for Soda Dark alert dialogs.
 *
 * These intentionally exercise production paths ([ThemedAlertDialog.builder],
 * [show], [create] + [show], [chainOnShow]) and assert what is painted after
 * [show] — not merely that XML theme attributes resolve in isolation.
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

    // --- Production path: builder.show() (Delete field, Export save, etc.) ---

    @Test
    fun showPath_messageOnly_rendersDarkPanelAndBrightTitle() {
        val dialog = ThemedAlertDialog.builder(sodaActivityContext)
            .setTitle(R.string.fields_delete_study)
            .setMessage(R.string.fields_delete_permanent_warning)
            .show()
        track(dialog)
        idleAfterShow()

        assertRenderedAlertChrome(dialog, "delete confirmation")
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
        idleAfterShow()

        assertRenderedAlertChrome(dialog, "HTTP warning")
    }

    // --- Production path: create().show() (wireSodaChrome onShow) ---

    @Test
    fun createShowPath_messageOnly_rendersDarkPanelWithoutManualChrome() {
        val dialog = ThemedAlertDialog.builder(sodaActivityContext)
            .setTitle(R.string.fields_new_dialog_title)
            .setMessage(R.string.dialog_cancel)
            .create()
        dialog.show()
        track(dialog)
        idleAfterShow()

        assertRenderedAlertChrome(dialog, "new field message (create().show())")
    }

    // --- Production path: create() + chainOnShow + show() (Field detail edit name) ---

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
        idleAfterShow()

        assertRenderedAlertChrome(dialog, "edit display name (chainOnShow)")
    }

    // --- Custom-view dialogs (culprit layouts) ---

    @Test
    fun showPath_editDisplayName_rendersDarkPanelAndContent() {
        assertCustomViewDialog(R.layout.dialog_field_edit_name, R.string.field_edit_display_name, "edit display name")
    }

    @Test
    fun showPath_sortEntries_rendersDarkPanelAndContent() {
        assertCustomViewDialog(R.layout.dialog_field_sort, R.string.dialog_field_sort_title, "sort entries")
    }

    @Test
    fun showPath_setSearchId_rendersDarkPanelAndContent() {
        assertCustomViewDialog(
            R.layout.dialog_collect_att_chooser,
            R.string.search_attribute_dialog_title,
            "set search id",
        )
    }

    @Test
    fun showPath_export_rendersDarkPanelAndContent() {
        assertCustomViewDialog(R.layout.dialog_export, R.string.settings_export, "export")
    }

    @Test
    fun showPath_traitLayout_rendersDarkPanelAndContent() {
        assertCustomViewDialog(R.layout.dialog_trait_creator, R.string.traits_new_dialog_title, "trait layout")
    }

  // --- ListAddDialog pattern: programmatic root using contentContext ---

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
        idleAfterShow()

        assertRenderedAlertChrome(dialog, "new field (ListAdd pattern)")
        assertColorEquals("ListAdd custom root background", sodaWindow, layout.backgroundColor())
    }

    // --- Guard: non-Soda theme must not force dark chrome ---

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
        idleAfterShow()

        val panel = dialog.requireParentPanel("light theme")
        val panelColor = panel.backgroundColor()
        if (panelColor != null) {
            assertNotEquals(
                "Light theme must not paint Soda row color on panel",
                sodaRow,
                panelColor,
            )
        }
    }

    // --- Minimal theme attr sanity (not a substitute for rendering checks above) ---

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

    private fun assertCustomViewDialog(layoutRes: Int, titleRes: Int, label: String) {
        val content = ThemedAlertDialog.inflate(sodaActivityContext, layoutRes)
        val rootBg = MaterialColors.getColor(content.context, R.attr.fb_color_background, Color.WHITE)
        assertColorEquals("$label content bg", sodaWindow, rootBg)

        val dialog = ThemedAlertDialog.builder(sodaActivityContext)
            .setTitle(titleRes)
            .setView(content)
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
        track(dialog)
        idleAfterShow()

        assertRenderedAlertChrome(dialog, label)
    }

    private fun assertRenderedAlertChrome(dialog: AlertDialog, label: String) {
        val panel = dialog.requireParentPanel(label)
        val panelColor = panel.backgroundColor()
        assertNotNull("$label: parentPanel must have a readable background after show", panelColor)
        assertColorEquals("$label: parentPanel background", sodaRow, panelColor)
        assertNotWhite(panelColor!!, "$label: parentPanel")

        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.let { title ->
            assertColorEquals("$label: title text", sodaBright, title.currentTextColor)
            assertNotWhite(title.currentTextColor, "$label: title text")
        }

        dialog.findViewById<TextView>(android.R.id.message)?.let { message ->
            assertNotWhite(message.currentTextColor, "$label: message text")
        }
    }

    private fun AlertDialog.requireParentPanel(label: String): View {
        val panel = findViewById<View>(androidx.appcompat.R.id.parentPanel)
        assertNotNull("$label: parentPanel must exist after show", panel)
        return panel!!
    }

    private fun View.backgroundColor(): Int? = when (val bg = background) {
        is ColorDrawable -> bg.color
        is GradientDrawable -> bg.color?.defaultColor
        is ShapeDrawable -> bg.paint.color
        else -> null
    }

    private fun assertNotWhite(color: Int, label: String) {
        assertNotEquals("$label must not be white", whiteRgb, color and 0x00FFFFFF)
    }

    private fun assertColorEquals(message: String, expected: Int, actual: Int?) {
        assertNotNull(message, actual)
        assertEquals(message, expected.toLong(), actual!!.toLong())
    }

    private fun idleAfterShow() {
        ShadowLooper.idleMainLooper()
    }

    private fun track(dialog: AlertDialog) {
        openDialogs.add(dialog)
    }

    private fun extractSolidColor(drawable: Drawable): Int? {
        return when (drawable) {
            is ColorDrawable -> drawable.color
            is GradientDrawable -> drawable.color?.defaultColor
            is InsetDrawable -> drawable.drawable?.let { extractSolidColor(it) }
            is LayerDrawable -> {
                for (i in 0 until drawable.numberOfLayers) {
                    extractSolidColor(drawable.getDrawable(i))?.let { return it }
                }
                null
            }
            is ShapeDrawable -> drawable.paint.color
            else -> null
        }
    }
}
