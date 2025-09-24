package com.fieldbook.tracker.utilities

import com.fieldbook.tracker.traits.CategoricalTraitLayout
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories


/**
 * Simple util class to encode/decode category objects.
 */
class CategoryJsonUtil {

    companion object {

        fun encode(categories: ArrayList<BrAPIScaleValidValuesCategories>): String {
            return Gson().toJson(
                categories,
                object : TypeToken<List<BrAPIScaleValidValuesCategories?>?>() {}.type
            )
        }

        fun decode(json: String): ArrayList<BrAPIScaleValidValuesCategories> {
            return if (json == "NA" || !JsonUtil.isJsonValid(json)) arrayListOf(
                BrAPIScaleValidValuesCategories().apply {
                    label = json
                    value = json
                }) else Gson().fromJson(
                json,
                object : TypeToken<List<BrAPIScaleValidValuesCategories?>>() {}.type
            )
        }

        fun decodeCategories(json: String): ArrayList<BrAPIScaleValidValuesCategories> {
            return Gson().fromJson(
                json,
                object : TypeToken<List<BrAPIScaleValidValuesCategories?>>() {}.type
            )
        }

        /**
         * Takes an array of BrAPIScaleValidValuesCategories and returns a printable version.
         * JSON is currently stored in the backend to keep record of label/value pairs, the actual exported value
         * is converted using this function to a ":" delimited string. This might lead to data having extra ":" if the user
         * created labels/values with ":".
         */
        fun flattenMultiCategoryValue(scale: ArrayList<BrAPIScaleValidValuesCategories>, showLabel: Boolean = true): String {
            return scale.joinToString(":") {
                if (showLabel) it.label
                else it.value
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
        fun filterExists(categories: Array<BrAPIScaleValidValuesCategories>,
            scale: java.util.ArrayList<BrAPIScaleValidValuesCategories>
        ): ArrayList<BrAPIScaleValidValuesCategories> = arrayListOf(
            *scale.filter { categories.any { c -> c.label == it.label && c.value == it.value } }.toTypedArray()
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

            return when(row["observation_variable_field_book_format"]) {
                in CategoricalTraitLayout.POSSIBLE_VALUES -> {
                    try {
                        val decoded = decode(rawValue ?: "")
                        if (decoded.size > 1) { // trait has multicat enabled
                            decoded.joinToString(":") { it.value }
                        } else {
                            decoded[0].value
                        }
                    } catch (ignore: Exception) {
                        rawValue
                    }
                }
                else -> {
                    rawValue
                }
            }
        }

        fun contains(cats: ArrayList<BrAPIScaleValidValuesCategories>, cat: BrAPIScaleValidValuesCategories) = cat in cats
        fun contains(cats: ArrayList<BrAPIScaleValidValuesCategories>, value: String) = value in cats.map { it.value }


        fun buildCategoryList(categories: List<BrAPIScaleValidValuesCategories>): String {
            return try {
                encode(ArrayList(categories))
            } catch (e: Exception) {
                buildCategoryListOld(categories)
            }
        }

        fun buildCategoryDescriptionString(categories: List<BrAPIScaleValidValuesCategories>): String {
            val sb = java.lang.StringBuilder()
            for (j in categories.indices) {
                sb.append(categories[j].value + "=" + categories[j].label)
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
                    // Use the "value" like brapi v1
                    sb.append(categories[j].value)
                    if (j != categories.size - 1) {
                        sb.append("/")
                    }
                }
            }
            return sb.toString()
        }
    }
}