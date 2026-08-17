package com.fieldbook.tracker.preferences.enums

/**
 * Collect activity toolbar item references
 */
enum class ToolbarItem(val value: String) {
    SEARCH("search"),
    RESOURCES("resources"),
    SUMMARY("summary"),
    LOCK_DATA("lockData");

    companion object {
        fun fromValue(value: String?): ToolbarItem {
            return when (value) {
                "search" -> SEARCH
                "resources" -> RESOURCES
                "summary" -> SUMMARY
                "lockData" -> LOCK_DATA
                else -> SEARCH
            }
        }
    }
}
