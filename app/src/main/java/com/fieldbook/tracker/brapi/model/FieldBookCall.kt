package com.fieldbook.tracker.brapi.model

/**
 * What Field Book uses a BrAPI call for, the compatibility page lists each purpose separately.
 */
enum class BrapiCallPurpose {
    // reads: importing fields and traits, downloading observations
    IMPORT,
    // writes: sending observations and images to the server
    UPLOAD
}

/**
 * One method of one BrAPI call that Field Book makes.
 */
data class FieldBookCall(
    val service: String,
    val method: String,
    val purpose: BrapiCallPurpose = BrapiCallPurpose.IMPORT,
)
