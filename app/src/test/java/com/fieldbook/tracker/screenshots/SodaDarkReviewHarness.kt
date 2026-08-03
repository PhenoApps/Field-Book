package com.fieldbook.tracker.screenshots

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.setPadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.activities.brapi.io.sync.BrapiExportUiState
import com.fieldbook.tracker.activities.brapi.io.sync.BrapiSyncScreen
import com.fieldbook.tracker.charts.HistogramChartHelper
import com.fieldbook.tracker.charts.PieChartHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.composables.ServerInfoScreen
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.ui.AttachMediaChoice
import com.fieldbook.tracker.ui.components.widgets.ThreeStateToggle
import com.fieldbook.tracker.ui.grid.datagrid.DataGridHeaderCell
import com.fieldbook.tracker.ui.grid.datagrid.DataGridUiColors
import com.fieldbook.tracker.ui.screens.traits.listItems.TraitListItem
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.BrapiModuleCalls
import com.fieldbook.tracker.utilities.CallImplementedBy
import com.fieldbook.tracker.utilities.ServiceComparison
import com.fieldbook.tracker.utilities.ThemedAlertDialog
import com.fieldbook.tracker.viewmodels.BrapiServerInfoViewModel.ServerInfoUiState
import com.fieldbook.tracker.views.OverlayImageView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.charts.PieChart
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.math.BigDecimal

/**
 * Lightweight AppCompat host for PR #1477 soda-dark review screenshots.
 * Avoids Hilt ThemedActivity / Collect / BrAPI activities.
 */
class SodaDarkReviewHarness : AppCompatActivity() {

    enum class Scene {
        TRAITS_RESOURCE_ICONS,
        SUMMARY_HOME_AND_NAV,
        COLLECT_AUDIO_TRAIT,
        FIELD_EXPORT_DIALOG,
        FIELD_DELETE_DIALOG,
        PREFERENCE_SETTINGS_DIALOG,
        FIELD_SORT_ICONS,
        DATA_GRID_HEADERS,
        FIELD_DETAIL_STATS_AXIS,
        BRAPI_SYNC_TITLE,
        BRAPI_IMPORTER_CHIPS,
        BRAPI_SERVER_CHECKER,
        ATTACH_MEDIA_AUDIO_ICON,
        DEFINE_CROP_REGION_MASK,
        COLLECT_PHOTO_SETTINGS,
    }

    /** View Roborazzi should capture (defaults to activity content). */
    var captureTarget: View? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        applyForcedThemePrefs(applicationContext)
        applyForcedThemePrefs(this)
        ThemedActivity.applyTheme(this)
        super.onCreate(savedInstanceState)

        val scene = intent.getStringExtra(EXTRA_SCENE)
            ?.let { runCatching { Scene.valueOf(it) }.getOrNull() }
            ?: Scene.TRAITS_RESOURCE_ICONS

        when (scene) {
            Scene.TRAITS_RESOURCE_ICONS -> showTraitsResourceIcons()
            Scene.SUMMARY_HOME_AND_NAV -> showSummaryHomeAndNav()
            Scene.COLLECT_AUDIO_TRAIT -> showCollectAudioTrait()
            Scene.FIELD_EXPORT_DIALOG -> showFieldExportDialog()
            Scene.FIELD_DELETE_DIALOG -> showFieldDeleteDialog()
            Scene.PREFERENCE_SETTINGS_DIALOG -> showPreferenceSettingsDialog()
            Scene.FIELD_SORT_ICONS -> showFieldSortIcons()
            Scene.DATA_GRID_HEADERS -> showDataGridHeaders()
            Scene.FIELD_DETAIL_STATS_AXIS -> showFieldDetailStatsAxis()
            Scene.BRAPI_SYNC_TITLE -> showBrapiSyncTitle()
            Scene.BRAPI_IMPORTER_CHIPS -> showBrapiImporterChips()
            Scene.BRAPI_SERVER_CHECKER -> showBrapiServerChecker()
            Scene.ATTACH_MEDIA_AUDIO_ICON -> showAttachMediaAudioIcon()
            Scene.DEFINE_CROP_REGION_MASK -> showDefineCropRegionMask()
            Scene.COLLECT_PHOTO_SETTINGS -> showCollectPhotoSettings()
        }
    }

    private fun blankRoot(): FrameLayout =
        FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(MaterialColors.getColor(this@SodaDarkReviewHarness, R.attr.fb_color_background, Color.BLACK))
        }

    private fun showTraitsResourceIcons() {
        val host = ComposeView(this)
        setContentView(host)
        host.setContent {
            ScreenshotAppTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppTheme.colors.background),
                    color = AppTheme.colors.background,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Traits — format icons",
                            style = AppTheme.typography.subheadingStyle,
                            color = AppTheme.colors.text.primary,
                        )
                        sampleTraits().forEach { trait ->
                            TraitListItem(
                                trait = trait,
                                onClick = {},
                                onToggleVisibility = {},
                            )
                        }
                    }
                }
            }
        }
        captureTarget = host
    }

    private fun showSummaryHomeAndNav() {
        setContentView(R.layout.fragment_summary)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.fragment_summary_toolbar_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        tintToolbarNavigationIcon(toolbar)

        val rv = findViewById<RecyclerView>(R.id.fragment_summary_rv)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = object : RecyclerView.Adapter<SummaryRowHolder>() {
            private val rows = listOf("plot_id" to "101", "row" to "3", "plant_height" to "42.5")
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SummaryRowHolder {
                val row = layoutInflater.inflate(R.layout.list_item_summary, parent, false)
                return SummaryRowHolder(row)
            }

            override fun getItemCount(): Int = rows.size

            override fun onBindViewHolder(holder: SummaryRowHolder, position: Int) {
                val (k, v) = rows[position]
                holder.bind(k, v)
            }
        }
        captureTarget = findViewById(android.R.id.content)
    }

    private fun tintToolbarNavigationIcon(toolbar: Toolbar) {
        val drawable = ContextCompat.getDrawable(this, R.drawable.arrow_left)?.mutate() ?: return
        val tintColor = MaterialColors.getColor(toolbar, R.attr.fb_icon_tint, 0)
        if (tintColor != 0) {
            DrawableCompat.setTint(drawable, tintColor)
            toolbar.navigationIcon = drawable
        } else {
            toolbar.setNavigationIcon(R.drawable.arrow_left)
        }
    }

    private class SummaryRowHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(key: String, value: String) {
            itemView.findViewById<TextView>(R.id.list_item_summary_key_tv)?.text = key
            itemView.findViewById<TextView>(R.id.list_item_summary_value_tv)?.text = value
        }
    }

    private fun showCollectAudioTrait() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(MaterialColors.getColor(this@SodaDarkReviewHarness, R.attr.fb_color_background, Color.BLACK))
            setPadding(dp(8))
        }
        val traitBox = layoutInflater.inflate(R.layout.view_trait_box, root, false)
        traitBox.findViewById<TextView>(R.id.traitTypeTv)?.text = "Audio"
        root.addView(traitBox)

        val audio = layoutInflater.inflate(R.layout.trait_audio, root, false)
        audio.visibility = View.VISIBLE
        audio.findViewById<TextView>(R.id.file_name_text)?.text = "plot101_audio.m4a"
        audio.findViewById<TextView>(R.id.file_timestamp)?.text = "May 21, 2:45 PM"
        audio.findViewById<TextView>(R.id.file_duration)?.text = "00:12"
        audio.findViewById<TextView>(R.id.file_size)?.text = "128 KB"
        audio.findViewById<FloatingActionButton>(R.id.record)?.show()
        root.addView(audio)

        setContentView(root)
        captureTarget = root
    }

    private fun showFieldExportDialog() {
        val content = ThemedAlertDialog.inflate(this, R.layout.dialog_export)
        setContentView(dialogHost(R.string.settings_export, content))
        captureTarget = findViewById(android.R.id.content)
    }

    private fun showFieldDeleteDialog() {
        val message = TextView(this).apply {
            text = getString(R.string.fields_delete_permanent_warning)
            setTextColor(MaterialColors.getColor(this@SodaDarkReviewHarness, android.R.attr.textColorPrimary, Color.WHITE))
            textSize = 16f
            setPadding(dp(24))
        }
        setContentView(dialogHost(R.string.fields_delete_study, message))
        captureTarget = findViewById(android.R.id.content)
    }

    private fun showPreferenceSettingsDialog() {
        val prefContext = ContextThemeWrapper(this, R.style.PreferenceTheme_SodaDark)
        val content = ThemedAlertDialog.inflate(prefContext, R.layout.dialog_person)
        setContentView(dialogHost(R.string.preferences_profile_person_dialog_title, content, prefContext))
        captureTarget = findViewById(android.R.id.content)
    }

    /**
     * Collect → photo trait → cog settings dialog ([R.layout.view_trait_photo_settings]).
     * Inflates the real settings layout (avoids [CameraTraitSettingsView]'s CollectActivity cast).
     */
    private fun showCollectPhotoSettings() {
        val content = ThemedAlertDialog.inflate(this, R.layout.view_trait_photo_settings)
        content.findViewById<RadioGroup>(R.id.view_trait_photo_settings_camera_rg)
            .check(R.id.view_trait_photo_settings_camera_custom_rb)
        content.findViewById<CheckBox>(R.id.view_trait_photo_preview_cb).isChecked = true

        val resolutionGroup = content.findViewById<RadioGroup>(R.id.view_trait_photo_settings_resolution_rg)
        listOf("1920x1080", "1280x720", "640x480").forEachIndexed { index, label ->
            resolutionGroup.addView(
                RadioButton(content.context).apply {
                    text = label
                    id = index
                    isChecked = index == 0
                },
            )
        }
        // Crop is optional per trait; keep visible so the control is reviewed under soda-dark.
        content.findViewById<View>(R.id.view_trait_photo_settings_crop_btn).visibility = View.VISIBLE

        ThemedAlertDialog.applyDialogTextColors(content)
        setContentView(dialogHost(R.string.trait_system_photo_settings_title, content))
        captureTarget = findViewById(android.R.id.content)
    }

    private fun showFieldSortIcons() {
        val content = ThemedAlertDialog.inflate(this, R.layout.dialog_field_sort)
        val rv = content.findViewById<RecyclerView>(R.id.dialog_field_sort_rv)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = object : RecyclerView.Adapter<SortRowHolder>() {
            private val labels = listOf("plot_id", "row")
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SortRowHolder {
                val row = layoutInflater.inflate(R.layout.list_item_field_sort, parent, false)
                return SortRowHolder(row)
            }

            override fun getItemCount(): Int = labels.size

            override fun onBindViewHolder(holder: SortRowHolder, position: Int) {
                holder.itemView.findViewById<TextView>(R.id.list_item_field_sort_value_tv)?.text =
                    labels[position]
            }
        }
        setContentView(dialogHost(R.string.dialog_field_sort_title, content))
        captureTarget = findViewById(android.R.id.content)
    }

    /** Embeds alert-style chrome so Roborazzi can capture without dialog window issues. */
    private fun dialogHost(
        titleRes: Int,
        content: View,
        themedContext: android.content.Context = this,
    ): LinearLayout {
        val panelBg = MaterialColors.getColor(themedContext, R.attr.fb_color_card_background, Color.DKGRAY)
        val titleColor = MaterialColors.getColor(themedContext, android.R.attr.textColorPrimary, Color.WHITE)
        val root = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(MaterialColors.getColor(this@SodaDarkReviewHarness, R.attr.fb_color_background, Color.BLACK))
            setPadding(dp(16))
            gravity = Gravity.CENTER
        }
        val panel = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panelBg)
            setPadding(dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        panel.addView(
            TextView(themedContext).apply {
                text = themedContext.getString(titleRes)
                setTextColor(titleColor)
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(16), dp(12), dp(16), dp(8))
            },
        )
        if (content.parent != null) {
            (content.parent as ViewGroup).removeView(content)
        }
        panel.addView(
            content,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val buttons = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(8))
        }
        buttons.addView(
            TextView(themedContext).apply {
                text = themedContext.getString(R.string.dialog_cancel)
                setTextColor(MaterialColors.getColor(themedContext, R.attr.fb_color_accent, Color.CYAN))
                setPadding(dp(16))
            },
        )
        buttons.addView(
            TextView(themedContext).apply {
                text = themedContext.getString(R.string.dialog_ok)
                setTextColor(MaterialColors.getColor(themedContext, R.attr.fb_color_accent, Color.CYAN))
                setPadding(dp(16))
            },
        )
        panel.addView(buttons)
        root.addView(panel)
        return root
    }

    private class SortRowHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private fun showDataGridHeaders() {
        val typed = TypedValue()
        fun attrColor(attr: Int, fallback: Int): Int =
            if (theme.resolveAttribute(attr, typed, true)) typed.data else fallback

        val colors = DataGridUiColors(
            activeCellBgColor = attrColor(R.attr.activeCellColor, Color.CYAN),
            filledCellBgColor = attrColor(R.attr.dataFilledColor, Color.GRAY),
            emptyCellBgColor = attrColor(R.attr.emptyCellColor, Color.DKGRAY),
            activeCellTextColor = attrColor(R.attr.activeCellTextColor, Color.BLACK),
            cellTextColor = attrColor(R.attr.cellTextColor, Color.WHITE),
            headerCellBgColor = attrColor(R.attr.headerCellColor, Color.DKGRAY),
        )

        val host = ComposeView(this)
        setContentView(host)
        host.setContent {
            ScreenshotAppTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppTheme.colors.background),
                    color = AppTheme.colors.background,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Data grid headers",
                            style = AppTheme.typography.subheadingStyle,
                            color = AppTheme.colors.text.primary,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        Row {
                            listOf("plot_id", "plant_height", "flowering", "notes").forEach { label ->
                                Box(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .height(40.dp),
                                ) {
                                    DataGridHeaderCell(
                                        text = label,
                                        colors = colors,
                                        sortIconRes = if (label == "plant_height") R.drawable.ic_sort else null,
                                        isLocked = label == "plot_id",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        captureTarget = host
    }

    private fun showFieldDetailStatsAxis() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MaterialColors.getColor(this@SodaDarkReviewHarness, R.attr.fb_color_background, Color.BLACK))
            setPadding(dp(8))
        }
        val item = layoutInflater.inflate(R.layout.list_item_field_detail_recycler, root, false)
        item.findViewById<TextView>(R.id.traitNameTextView)?.text = "plant_height"
        item.findViewById<TextView>(R.id.traitCountTextView)?.text = "24 / 40 collected"
        item.findViewById<ImageView>(R.id.traitIconImageView)
            ?.setImageResource(R.drawable.ic_trait_numeric)

        val pie = item.findViewById<PieChart>(R.id.traitCompletenessChart)
        PieChartHelper.setupPieChart(this, pie, 0.6f, 12f)

        val histogram = item.findViewById<BarChart>(R.id.histogram)
        val samples = listOf(10, 12, 12, 15, 18, 20, 21, 22, 25, 30).map { BigDecimal(it) }
        HistogramChartHelper.setupHistogram(this, histogram, samples, 12f)

        item.findViewById<HorizontalBarChart>(R.id.barChart)?.visibility = View.GONE

        root.addView(item)
        setContentView(root)
        captureTarget = root
    }

    private fun showBrapiSyncTitle() {
        val host = ComposeView(this)
        setContentView(host)
        host.setContent {
            ScreenshotAppTheme {
                BrapiSyncScreen(
                    uiState = BrapiExportUiState(
                        isInitialized = true,
                        brapiServerDisplayName = "Demo BrAPI",
                    ),
                    onDownloadClick = {},
                    onCancelDownloadClick = {},
                    onExportClick = {},
                    onCancelExportClick = {},
                    onImageUploadToggle = {},
                    onNavigateUp = {},
                    onAuthenticate = {},
                    onMergeStrategyChange = {},
                )
            }
        }
        captureTarget = host
    }

    private fun showBrapiImporterChips() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MaterialColors.getColor(this@SodaDarkReviewHarness, R.attr.fb_color_background, Color.BLACK))
            setPadding(dp(16))
        }
        val title = TextView(this).apply {
            text = "BrAPI importer filters"
            setTextColor(MaterialColors.getColor(this@SodaDarkReviewHarness, R.attr.fb_color_text_dark, Color.WHITE))
            textSize = 18f
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(title)

        val chipGroup = ChipGroup(this).apply {
            id = R.id.act_list_filter_cg
        }
        val chips = listOf(
            R.style.FirstChipTheme to "Program A",
            R.style.SecondChipTheme to "Trial 2024",
            R.style.ThirdChipTheme to "Season 1",
            R.style.FourthChipTheme to "wheat",
        )
        chips.forEach { (style, label) ->
            chipGroup.addView(
                Chip(ContextThemeWrapper(this, style)).apply {
                    text = label
                    closeIcon = AppCompatResources.getDrawable(this@SodaDarkReviewHarness, R.drawable.close)
                    isCloseIconVisible = true
                    isCheckable = false
                },
            )
        }
        root.addView(chipGroup)
        setContentView(root)
        captureTarget = root
    }

    private fun showBrapiServerChecker() {
        val host = ComposeView(this)
        setContentView(host)
        host.setContent {
            ScreenshotAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppTheme.colors.background,
                ) {
                    ServerInfoScreen(uiState = sampleServerInfo())
                }
            }
        }
        captureTarget = host
    }

    private fun showAttachMediaAudioIcon() {
        val host = ComposeView(this)
        setContentView(host)
        host.setContent {
            ScreenshotAppTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppTheme.colors.background),
                    color = AppTheme.colors.background,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Text(
                            text = "Attach media",
                            style = AppTheme.typography.subheadingStyle,
                            color = AppTheme.colors.text.primary,
                        )
                        AttachMediaChoice(
                            photoState = remember { mutableStateOf(true) },
                            videoState = remember { mutableStateOf(false) },
                            audioState = remember { mutableStateOf(true) },
                        )
                        Text(
                            text = "Camera media toggle",
                            style = AppTheme.typography.subheadingStyle,
                            color = AppTheme.colors.text.primary,
                        )
                        val painters = listOf(
                            painterResource(R.drawable.ic_media_toggle_photo),
                            painterResource(R.drawable.ic_media_toggle_video),
                            painterResource(R.drawable.ic_media_toggle_audio),
                        )
                        ThreeStateToggle(
                            states = painters,
                            selectedIndex = 2,
                            onSelected = {},
                            contentDescriptions = listOf("Photo", "Video", "Audio"),
                            enabledStates = listOf(true, false, true),
                        )
                    }
                }
            }
        }
        captureTarget = host
    }

    private fun showDefineCropRegionMask() {
        val frame = blankRoot()
        val overlay = OverlayImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            )
            scaleType = ImageView.ScaleType.FIT_XY
        }
        frame.addView(overlay)
        setContentView(frame)

        frame.post {
            val w = frame.width.coerceAtLeast(resources.displayMetrics.widthPixels)
            val h = frame.height.coerceAtLeast((resources.displayMetrics.heightPixels * 0.7f).toInt())
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(0xFF3D5A3D.toInt())
                // Simple brighter "subject" band so the crop hole is obvious.
                for (y in (h * 0.3f).toInt() until (h * 0.7f).toInt()) {
                    for (x in (w * 0.25f).toInt() until (w * 0.75f).toInt()) {
                        bitmap.setPixel(x, y, 0xFF6B8F6B.toInt())
                    }
                }
            }
            overlay.drawRectangle(
                bmp,
                0f,
                0f,
                w,
                h,
                w * 0.22f,
                h * 0.28f,
                w * 0.78f,
                h * 0.72f,
            )
        }
        captureTarget = frame
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    companion object {
        const val EXTRA_SCENE = "scene"

        private fun sampleTraits(): List<TraitObject> =
            listOf(
                Formats.PERCENT to "Percent",
                Formats.AUDIO to "Audio",
                Formats.CATEGORICAL to "Flower color",
                Formats.NUMERIC to "Plant height",
                Formats.DATE to "Flowering date",
                Formats.TEXT to "Notes",
                Formats.BOOLEAN to "Lodging",
            ).map { (format, alias) ->
                TraitObject().apply {
                    this.alias = alias
                    this.format = format.getDatabaseName()
                    visible = true
                }
            }

        private fun sampleServerInfo(): ServerInfoUiState =
            ServerInfoUiState(
                isLoading = false,
                serverName = "Demo BrAPI Server",
                organizationName = "Breeding Insight",
                serverDescription = "Compatibility checker under Soda Dark",
                modulesMap = mapOf(
                    "core" to BrapiModuleCalls(
                        moduleName = "Core",
                        calls = listOf(
                            ServiceComparison(
                                service = "studies",
                                methods = listOf("GET", "POST"),
                                isAppImplemented = true,
                                implementedMethods = listOf("GET"),
                                source = CallImplementedBy.SERVER_AND_APP,
                            ),
                            ServiceComparison(
                                service = "programs",
                                methods = listOf("GET"),
                                isAppImplemented = true,
                                implementedMethods = listOf("GET"),
                                source = CallImplementedBy.SERVER_AND_APP,
                            ),
                        ),
                        appImplementedCount = 2,
                        totalCalls = 2,
                    ),
                ),
                appCompatibility = BrapiModuleCalls(
                    moduleName = "Field Book Compatibility",
                    calls = listOf(
                        ServiceComparison(
                            service = "programs",
                            methods = listOf("GET"),
                            isAppImplemented = true,
                            implementedMethods = listOf("GET"),
                            source = CallImplementedBy.SERVER_AND_APP,
                        ),
                    ),
                    appImplementedCount = 1,
                    totalCalls = 1,
                ),
            )
    }
}
