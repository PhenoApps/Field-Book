package com.fieldbook.shared.utilities

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.offsetAt

val internalTimeFormatter = DateTimeComponents.Format {
    byUnicodePattern("yyyy-MM-dd HH:mm:ss.SSSZZZZZ")
}

fun currentLocalInternalTimestamp(): String {
    val now = Clock.System.now()
    val offset = TimeZone.currentSystemDefault().offsetAt(now)

    return now.format(internalTimeFormatter, offset = offset)
}
