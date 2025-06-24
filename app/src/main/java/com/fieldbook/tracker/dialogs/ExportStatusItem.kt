package com.fieldbook.tracker.dialogs

/**
 * Data class representing an export status item
 */
data class ExportStatusItem(
    val id: String,                 // Unique identifier for this status item
    val label: String,              // Display label
    val completedCount: Int = 0,    // Number of items completed
    val totalCount: Int = 0,        // Total number of items
    val isComplete: Boolean = false // Whether this item is complete
)