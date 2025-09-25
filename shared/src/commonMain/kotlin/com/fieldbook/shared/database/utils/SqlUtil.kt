package com.fieldbook.shared.database.utils

import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.byUnicodePattern

typealias Row = Map<String, Any?>

val internalTimeFormatter = DateTimeComponents.Format {
    byUnicodePattern("yyyy-MM-dd HH:mm:ss.SSSZZZZZ")
}

