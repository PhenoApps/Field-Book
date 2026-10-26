package com.fieldbook.tracker.utilities.sort

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One column of a field's entry sort.
 * When [serpentine] is set, [ascending] is reversed for every other group of the previous column,
 * so e.g. sorting by Row then a serpentine Column walks the field back and forth.
 */
@Serializable
data class FieldSortKey(
    val column: String,
    val ascending: Boolean = true,
    val serpentine: Boolean = false,
)

/**
 * The ordered columns a field's entries are sorted by, stored as JSON in studies.study_sort_name.
 * Older databases, BrAPI imports and the field creator store a comma separated list of column names instead,
 * which [parse] still reads.
 */
@Serializable
data class FieldSortSpec(
    val keys: List<FieldSortKey> = emptyList(),
    val version: Int = VERSION,
) {

    val columns: List<String> get() = keys.map { it.column }

    /** The value to store in studies.study_sort_name, null when there is nothing to sort by. */
    fun encode(): String? = if (keys.isEmpty()) null else json.encodeToString(serializer(), this)

    companion object {

        private const val TAG = "FieldSortSpec"

        const val VERSION = 1

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * Reads a stored sort, either the JSON written by [encode] or a legacy comma separated list of column names.
         * @param legacyAscending the direction applied to every column of a legacy list, which had one direction for the whole sort
         */
        fun parse(raw: String?, legacyAscending: Boolean = true): FieldSortSpec {

            val value = raw?.trim()

            if (value.isNullOrEmpty() || value == "null") return FieldSortSpec()

            if (value.startsWith("{")) {
                return try {
                    json.decodeFromString(serializer(), value)
                } catch (e: Exception) {
                    Log.w(TAG, "Unreadable sort: $value", e)
                    FieldSortSpec()
                }
            }

            return FieldSortSpec(
                value.split(',')
                    .filter { it.isNotBlank() }
                    .map { FieldSortKey(it, ascending = legacyAscending) }
            )
        }
    }
}
