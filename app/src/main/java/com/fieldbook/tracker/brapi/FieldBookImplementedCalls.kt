package com.fieldbook.tracker.brapi

import com.fieldbook.tracker.brapi.model.BrapiCallPurpose.UPLOAD
import com.fieldbook.tracker.brapi.model.FieldBookCall

/**
 * Add BrAPI endpoints that Field Book implements here, one entry per method.
 * Mark calls that send data to the server as [UPLOAD].
 */
val fieldBookImplementedCalls = listOf(
    // core calls
    FieldBookCall("programs", "GET"), // older import screens only
    FieldBookCall("serverinfo", "GET"),
    FieldBookCall("studies", "GET"),
    FieldBookCall("studies/{studyDbId}", "GET"), // older import screens only
    FieldBookCall("trials", "GET"),

    // germplasm calls
    FieldBookCall("germplasm", "GET"),
    // a POST, but a search that only reads, used by the older import screens
    FieldBookCall("search/germplasm", "POST"),
    FieldBookCall("search/germplasm/{searchResultsDbId}", "GET"),

    // phenotyping calls
    FieldBookCall("variables", "GET"),
    FieldBookCall("observationlevels", "GET"),
    FieldBookCall("observationunits", "GET"),
    FieldBookCall("observations", "GET"),
    FieldBookCall("observations", "POST", UPLOAD), // new observations
    FieldBookCall("observations", "PUT", UPLOAD), // edited observations, in bulk
    FieldBookCall("images", "POST", UPLOAD), // new image metadata
    FieldBookCall("images/{imageDbId}", "PUT", UPLOAD), // edited or unfinished image metadata
    FieldBookCall("images/{imageDbId}/imagecontent", "PUT", UPLOAD), // the image file
)
