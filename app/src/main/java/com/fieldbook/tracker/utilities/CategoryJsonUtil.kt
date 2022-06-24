package com.fieldbook.tracker.utilities

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

        fun contains(cats: Array<String>, value: String) = value in cats
    }
}