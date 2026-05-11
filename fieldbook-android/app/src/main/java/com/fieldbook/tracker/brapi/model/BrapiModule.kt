package com.fieldbook.tracker.brapi.model

/**
 * BrAPI calls categorized by respective modules
 */
data class BrapiModule(
    val moduleName: String,
    val calls: MutableList<BrapiServerCall> = mutableListOf(),
)