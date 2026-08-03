package com.fieldbook.tracker.traits.formats.tree

import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.coders.DateJsonCoder
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.JsonUtil
import java.text.SimpleDateFormat
import java.util.Locale

internal data class TreeCategoryOption(
    val label: String,
    val value: String,
)

internal object TreeTraitValueSupport {
    private const val UNKNOWN_YEAR_PREFIX = "????-"
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
    }
    private val previewDateFormat = SimpleDateFormat("MMM dd", Locale.getDefault()).apply {
        isLenient = false
    }

    fun categoryOptions(trait: TraitObject): List<TreeCategoryOption> =
        CategoryJsonUtil.parseTraitCategories(trait.categories).mapNotNull { cat ->
            val label = cat.label?.trim().orEmpty()
            val value = cat.value?.trim().orEmpty()
            when {
                label.isNotEmpty() && value.isNotEmpty() -> TreeCategoryOption(label, value)
                label.isNotEmpty() -> TreeCategoryOption(label, label)
                value.isNotEmpty() -> TreeCategoryOption(value, value)
                else -> null
            }
        }

    fun displayCategory(option: TreeCategoryOption, trait: TraitObject): String =
        if (trait.categoryDisplayValue) option.value else option.label

    fun isValidCategory(rawValue: String, trait: TraitObject): Boolean {
        val options = categoryOptions(trait)
        if (options.isEmpty()) return true
        val trimmed = rawValue.trim()
        if (trimmed.isBlank() || trimmed == "[]") return true

        val selected = if (trimmed.startsWith("[") && JsonUtil.isJsonValid(trimmed)) {
            try {
                CategoryJsonUtil.decodeCategories(trimmed).mapNotNull { cat ->
                    listOfNotNull(cat.value?.trim(), cat.label?.trim())
                        .firstOrNull { it.isNotEmpty() }
                }
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            trimmed.split(":").map { it.trim() }.filter { it.isNotEmpty() }
        }
        if (selected.isEmpty()) return true

        return selected.all { token ->
            options.any { option ->
                token == option.value || token == option.label || token == displayCategory(option, trait)
            }
        }
    }

    fun isValidDate(rawValue: String, trait: TraitObject): Boolean {
        val decoded = DateJsonCoder().decode(rawValue)
        if (decoded is DateJsonCoder.DateJson) return true
        if (rawValue.isBlank()) return true

        return try {
            if (trait.useDayOfYear) {
                rawValue.toInt().let { it in 1..366 }
            } else if (rawValue.startsWith(UNKNOWN_YEAR_PREFIX)) {
                val parts = rawValue.split("-")
                if (parts.size != 3) {
                    false
                } else {
                    val month = parts[1].toInt()
                    val day = parts[2].toInt()
                    month in 1..12 && day in 1..31
                }
            } else {
                isoDateFormat.parse(rawValue) != null || previewDateFormat.parse(rawValue) != null
            }
        } catch (_: Exception) {
            try {
                previewDateFormat.parse(rawValue) != null
            } catch (_: Exception) {
                false
            }
        }
    }

    fun isValidNumeric(rawValue: String, trait: TraitObject): Boolean {
        if (!trait.mathSymbolsEnabled && containsMathematicalSymbols(rawValue)) return false
        val maxDecimalPlaces = trait.maxDecimalPlaces.toIntOrNull() ?: -1
        if (maxDecimalPlaces >= 0 && !hasValidDecimalPlaces(rawValue, maxDecimalPlaces)) return false
        return rawValue.toDoubleOrNull() != null
    }

    fun isValidBoolean(rawValue: String): Boolean {
        val v = rawValue.trim()
        return v.equals("true", true) ||
            v.equals("false", true) ||
            v.equals("TRUE", false) ||
            v.equals("FALSE", false) ||
            v == "1" ||
            v == "0"
    }

    /** Matches [com.fieldbook.tracker.traits.StopWatchTraitLayout] saved CircularTimer format. */
    fun isValidStopWatch(rawValue: String): Boolean =
        Regex("""^\d+:[0-5]\d:[0-5]\d(\.\d{1,3})?$""").matches(rawValue.trim())

    private fun hasValidDecimalPlaces(value: String, maxDecimalPlaces: Int): Boolean {
        if (value.isEmpty()) return true

        return try {
            val doubleValue = value.toDouble()
            if (maxDecimalPlaces == 0) {
                doubleValue == doubleValue.toInt().toDouble()
            } else {
                val decimalIndex = value.indexOf('.')
                decimalIndex == -1 || value.substring(decimalIndex + 1).length <= maxDecimalPlaces
            }
        } catch (_: NumberFormatException) {
            false
        }
    }

    private fun containsMathematicalSymbols(data: String): Boolean =
        data.contains("+") || data.contains("-") || data.contains("*") || data.contains(";")
}
