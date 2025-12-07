package com.fieldbook.tracker.utilities

import android.util.Log
import java.time.LocalDate
import java.util.Calendar

object TraitDetailUtil {
    fun getTodayDayOfYear(): String = Calendar.getInstance().get(Calendar.DAY_OF_YEAR).toString()

    fun getTodayFormattedDate(): String {
        val calendar = Calendar.getInstance()

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1 // calendar months are 0-based
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return LocalDate.of(year, month, day).toString()
    }

    fun parseCategories(categories: String): List<String> {
        return try {
            if (categories.startsWith("[")) {
                CategoryJsonUtil.decode(categories).map { it.value }
            } else {
                categories.split("/").map { it.trim() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseCategoryExample(categories: String): Pair<String, String> {
        return try {
            if (categories.startsWith("[")) {
                val parsedCategories = CategoryJsonUtil.Companion.decode(categories)
                if (parsedCategories.isNotEmpty()) {
                    val firstItem = parsedCategories[0]
                    val labelField = firstItem.javaClass.getDeclaredField("label")
                    labelField.isAccessible = true
                    val label = labelField.get(firstItem)?.toString() ?: "Label"

                    Pair(label, firstItem.value)
                } else {
                    Pair("Example", "Value")
                }
            } else {
                val values = categories.split("/").map { it.trim() }
                if (values.isNotEmpty()) {
                    Pair(values[0], values[0])
                } else {
                    Pair("Example", "Value")
                }
            }
        } catch (e: Exception) {
            Log.e("parseCategoryExample", "Failed to parse categories: $categories", e)
            Pair("Example", "Value")
        }
    }
}