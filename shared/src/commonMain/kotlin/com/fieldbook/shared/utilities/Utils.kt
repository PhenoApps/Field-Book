package com.fieldbook.shared.utilities

import kotlinx.datetime.LocalDate

fun localDateToEpochMillis(date: LocalDate): Long {
    // 1970-01-01T00:00:00Z epoch
    val daysSinceEpoch = date.toEpochDays()
    return daysSinceEpoch * 24L * 60L * 60L * 1000L
}

fun epochMillisToLocalDate(epochMillis: Long): LocalDate {
    val daysSinceEpoch = (epochMillis / (24L * 60L * 60L * 1000L)).toInt()
    return LocalDate.fromEpochDays(daysSinceEpoch)
}

fun dateFormatMonthDay(date: LocalDate): String = "${
    date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
} ${date.dayOfMonth}"

fun dateFormatMonthDay(date: String): String {
    val localDate = try {
        LocalDate.parse(date)
    } catch (_: Exception) {
        return "NA"
    }
    return dateFormatMonthDay(localDate)
}
