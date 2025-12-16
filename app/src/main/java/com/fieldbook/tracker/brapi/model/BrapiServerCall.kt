package com.fieldbook.tracker.brapi.model

/**
 * Only includes fields required from BrAPIService
 */
data class BrapiServerCall(
    val service: String,
    val methods: List<String>,
)