package com.fieldbook.tracker.printing

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.fieldbook.tracker.R

/**
 * State holder and configuration persistence for the label print feature.
 * Manages reactive state (Compose-friendly) and SharedPreferences I/O.
 *
 * Field assignments are stored per-template in TemplateRepository.
 * Only the copies count is persisted globally here.
 */
class LabelPrintStore(private val prefs: SharedPreferences) {

    companion object {
        const val KEY_COPIES = "COPIES"

        /**
         * Resolves a saved preference value against the current available options.
         * If the saved value is null or not present in the options list, returns the default.
         */
        fun resolvePreference(
            savedValue: String?,
            options: List<String>,
            defaultValue: String
        ): String {
            return if (savedValue != null && savedValue in options) {
                savedValue
            } else {
                defaultValue
            }
        }

        /**
         * Determines whether the badge should be visible on the Print FAB.
         */
        fun shouldShowBadge(copiesCount: Int): Boolean = copiesCount > 1

        /**
         * Returns the badge text to display when the badge is visible.
         */
        fun badgeText(copiesCount: Int): String = copiesCount.toString()
    }

    // ─── Reactive State ─────────────────────────────────────────────────────────

    val selectedCopies = mutableIntStateOf(1)

    val showConfigDialog = mutableStateOf(false)
    val showFieldDialog = mutableStateOf(false)
    val selectedTemplateNameState = mutableStateOf<String?>(null)
    val currentPlotIdState = mutableStateOf<String?>(null)
    val assignmentsRevision = mutableIntStateOf(0)

    /** Tracks which placeholder is currently being assigned via AttributeChooserDialog. */
    var pendingFieldPlaceholder: String? = null

    /** Temporary in-dialog field assignments (bridging fragment callback to Compose state). */
    val fieldAssignments = mutableMapOf<String, String>()

    /** Field options for selectors (built during initialization). */
    var fieldOptions: List<String> = emptyList()
        private set

    // ─── Field Options ──────────────────────────────────────────────────────────

    /**
     * Builds the field options list from the database attributes and localized labels.
     * Should be called during initialization when the study context is available.
     */
    fun buildFieldOptions(context: Context, attributes: Array<String>) {
        fieldOptions = LabelPrintService.buildFieldOptions(
            fieldNameLabel = context.getString(R.string.field_name_attribute),
            attributes = attributes.toList(),
            dateLabel = context.getString(R.string.trait_layout_print_label_date_option),
            blankLabel = context.getString(R.string.trait_layout_print_label_blank_option),
            defaultValueLabel = context.getString(R.string.trait_layout_print_label_default_value_option)
        )
    }

    // ─── Configuration Persistence ──────────────────────────────────────────────

    /**
     * Saves the copies count to SharedPreferences.
     */
    fun saveConfig() {
        prefs.edit().apply {
            putString(KEY_COPIES, selectedCopies.intValue.toString())
            apply()
        }
    }

    /**
     * Restores the copies count from SharedPreferences.
     */
    fun restoreConfig() {
        val savedCopies = resolvePreference(
            prefs.getString(KEY_COPIES, null), LabelPrintService.COPIES_OPTIONS, "1"
        )
        selectedCopies.intValue = savedCopies.toIntOrNull() ?: 1
    }
}
