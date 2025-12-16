package com.fieldbook.shared.utilities

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Momentary model placeholders
 * TODO BrAPI kotlin/ktor client?
 */
@Serializable
open class BrAPIScaleValidValuesCategories(
    var label: String? = null,
    var value: String? = null
)

val POSSIBLE_VALUES: Array<String> = arrayOf("qualitative", "categorical")

/**
 * Simple util class to encode/decode category objects.
 */
class CategoryJsonUtil {
    companion object {
        fun encode(categories: ArrayList<BrAPIScaleValidValuesCategories>): String {
            return Json.encodeToString(categories)
        }

        fun decode(json: String): ArrayList<BrAPIScaleValidValuesCategories> {
            return if (json == "NA" || !isJsonValid(json)) arrayListOf(
                BrAPIScaleValidValuesCategories().apply {
                    label = json
                    value = json
                }) else Json.decodeFromString(json)
        }

        fun decodeCategories(json: String): ArrayList<BrAPIScaleValidValuesCategories> {
            return Json.decodeFromString(json)
        }

        /**
         * Takes an array of BrAPIScaleValidValuesCategories and returns a printable version.
         * JSON is currently stored in the backend to keep record of label/value pairs, the actual exported value
         * is converted using this function to a ":" delimited string. This might lead to data having extra ":" if the user
         * created labels/values with ":".
         */
        fun flattenMultiCategoryValue(
            scale: ArrayList<BrAPIScaleValidValuesCategories>,
            showLabel: Boolean = true
        ): String {
            return scale.joinToString(":") {
                if (showLabel) it.label ?: "" else it.value ?: ""
            }
        }

        /**
         * Filters the selected categories based on what exists in the trait's definition.
         * It is possible to observe a category for a trait A, B, C as A, then later edit that trait and delete A.
         * This simply checks for this case and removes A.
         * @param categories the classes defined for the trait
         * @param scale the currently selected observation
         * @return the filtered classes
         */
        fun filterExists(
            categories: Array<BrAPIScaleValidValuesCategories>,
            scale: ArrayList<BrAPIScaleValidValuesCategories>
        ): ArrayList<BrAPIScaleValidValuesCategories> = ArrayList(
            scale.filter { s -> categories.any { c -> c.label == s.label && c.value == s.value } }
        )

        /**
         * Takes a row returned from a query that should contain "observation_variable_field_book_format" and "value" keys.
         * Tests if this row is a categorical/multicat observations and returns the value if json is stored in the backend.
         * For multicat the values are joined with a ":"
         * @param row the cursor row that contains the required keys
         * @return value which represents the raw observation value or the categorical value from its label/val pair
         */
        fun processValue(row: Map<String, Any?>): String? {

            val rawValue = row["value"] as? String

            return when (row["observation_variable_field_book_format"]) {
                in POSSIBLE_VALUES -> {
                    try {
                        decode(rawValue ?: "")[0].value
                    } catch (_: Exception) {
                        rawValue
                    }
                }

                in setOf("multicat") -> {
                    try {
                        decode(rawValue ?: "").joinToString(":") { it.value ?: "" }
                    } catch (_: Exception) {
                        rawValue
                    }
                }

                else -> rawValue
            }
        }

        fun contains(
            cats: ArrayList<BrAPIScaleValidValuesCategories>,
            cat: BrAPIScaleValidValuesCategories
        ) = cat in cats

        fun contains(cats: ArrayList<BrAPIScaleValidValuesCategories>, value: String) =
            value in cats.map { it.value }


        fun buildCategoryList(categories: List<BrAPIScaleValidValuesCategories>): String {
            return try {
                encode(ArrayList(categories))
            } catch (_: Exception) {
                buildCategoryListOld(categories)
            }
        }

        fun buildCategoryDescriptionString(categories: List<BrAPIScaleValidValuesCategories>): String {
            val sb = StringBuilder()
            for (j in categories.indices) {
                sb.append((categories[j].value ?: "") + "=" + (categories[j].label ?: ""))
                if (j != categories.size - 1) {
                    sb.append("; ")
                }
            }
            return sb.toString()
        }

        private fun buildCategoryListOld(categories: List<BrAPIScaleValidValuesCategories>?): String {
            val sb = StringBuilder()
            if (categories != null) {
                for (j in categories.indices) {
                    sb.append(categories[j].value ?: "")
                    if (j != categories.size - 1) {
                        sb.append("/")
                    }
                }
            }
            return sb.toString()
        }

        // Simple JSON validity check for our use case
        private fun isJsonValid(json: String): Boolean {
            return try {
                Json.decodeFromString<ArrayList<BrAPIScaleValidValuesCategories>>(json)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
