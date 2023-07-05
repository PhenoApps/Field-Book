package com.fieldbook.tracker.objects

/**
 * Simple data class to hold info bar data and make a ":" delimited string.
 */
data class InfoBarModel(val prefix: String, val value: String) {
    override fun toString(): String {
        return "$prefix: $value"
    }
}