package com.fieldbook.tracker.utilities

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class DateResult {
    TODAY, YESTERDAY, THIS_YEAR, YEARS_AGO, INVALID_DATE // Added INVALID_DATE for error handling
}

data class SemanticDate(val result: DateResult, val yearsAgo: Int = 0)

object SemanticDateUtil {
    fun getSemanticDate(dateStr: String?): SemanticDate {
        if (dateStr.isNullOrEmpty()) {
            return SemanticDate(DateResult.INVALID_DATE)
        }

        val datePart = dateStr.split(" ")[0] // Assuming 'dateStr' is in "yyyy-MM-dd" format

        // Assuming this example only, adjust as needed for your full implementation
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
}

