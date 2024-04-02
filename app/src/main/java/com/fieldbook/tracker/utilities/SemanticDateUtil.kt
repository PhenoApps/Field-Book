package com.fieldbook.tracker.utilities

import android.content.Context
import com.fieldbook.tracker.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class DateResult {
    TODAY, YESTERDAY, THIS_YEAR, YEARS_AGO, INVALID_DATE
}

data class SemanticDate(val result: DateResult, val yearsAgo: Int = 0)

object SemanticDateUtil {
    fun getSemanticDate(context: Context, dateStr: String?): String {
        val semanticValue = calculateSemanticValue(dateStr) // Calculate the semantic value
        return formatSemanticDate(context, semanticValue, dateStr) // Format based on the semantic result
    }

    private fun calculateSemanticValue(dateStr: String?): SemanticDate {
        if (dateStr.isNullOrEmpty()) {
            return SemanticDate(DateResult.INVALID_DATE)
        }

        val datePart = dateStr.split(" ")[0]
        val calendarDate = Calendar.getInstance().apply {
            time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(datePart) ?: return SemanticDate(DateResult.INVALID_DATE)
        }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            isSameDay(calendarDate, today) -> SemanticDate(DateResult.TODAY)
            isSameDay(calendarDate, yesterday) -> SemanticDate(DateResult.YESTERDAY)
            calendarDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> SemanticDate(DateResult.THIS_YEAR)
            else -> {
                val yearsBetween = today.get(Calendar.YEAR) - calendarDate.get(Calendar.YEAR)
                SemanticDate(DateResult.YEARS_AGO, yearsBetween)
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun formatSemanticDate(context: Context, semanticDate: SemanticDate, dateStr: String?): String {
        return when (semanticDate.result) {
            DateResult.TODAY -> context.getString(R.string.today)
            DateResult.YESTERDAY -> context.getString(R.string.yesterday)
            DateResult.THIS_YEAR -> formatWithinLastYear(context, dateStr)
            DateResult.YEARS_AGO -> context.resources.getQuantityString(R.plurals.years_ago, semanticDate.yearsAgo, semanticDate.yearsAgo)
            DateResult.INVALID_DATE -> context.getString(R.string.invalid_date)
        }
    }

    private fun formatWithinLastYear(context: Context, dateStr: String?): String {
        return dateStr?.split(" ")?.get(0)?.let { datePart ->
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = dateFormat.parse(datePart)
            SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
        } ?: context.getString(R.string.invalid_date)
    }
}
