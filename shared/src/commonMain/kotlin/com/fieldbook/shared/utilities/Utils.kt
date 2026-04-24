package com.fieldbook.shared.utilities

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun relativeTimeText(dateImport: String?): String? {
    val raw = dateImport?.takeIf { it.isNotBlank() } ?: return null
    val instant = runCatching { Instant.parse(raw) }.getOrNull() ?: return null
    val diffMillis = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - instant.toEpochMilliseconds()
    if (diffMillis < 0) return null

    val dayMillis = 24L * 60L * 60L * 1000L
    val days = diffMillis / dayMillis
    return when {
        days >= 365 -> {
            val years = days / 365
            if (years == 1L) "1 year ago" else "$years years ago"
        }
        days >= 30 -> {
            val months = days / 30
            if (months == 1L) "1 month ago" else "$months months ago"
        }
        days >= 1 -> {
            if (days == 1L) "1 day ago" else "$days days ago"
        }
        else -> "Today"
    }
}
