package com.fieldbook.tracker.brapi

import com.fieldbook.tracker.brapi.model.BrapiServerCall

/**
 * Add BrAPI endpoints that Field Book implements here
 */
val fieldBookImplementedCalls = listOf(
    // core calls
    BrapiServerCall("commoncropnames", listOf("GET")),
    BrapiServerCall("programs", listOf("GET")),
    BrapiServerCall("seasons", listOf("GET")),
    BrapiServerCall("serverinfo", listOf("GET")),
    BrapiServerCall("studies", listOf("GET")),
    BrapiServerCall("studies/{studyDbId}", listOf("GET")),
    BrapiServerCall("trials", listOf("GET")),

    // germplasm calls
    BrapiServerCall("germplasm", listOf("GET")),
    BrapiServerCall("search/germplasm", listOf("POST")),
    BrapiServerCall("search/germplasm/{searchResultsDbId}", listOf("GET")),

    // phenotyping calls
    BrapiServerCall("variables", listOf("GET")),
    BrapiServerCall("observations", listOf("GET", "POST", "PUT")),
    BrapiServerCall("observationlevels", listOf("GET")),
    BrapiServerCall("observationunits", listOf("GET")),
    BrapiServerCall("images", listOf("POST")),
    BrapiServerCall("images/{imageDbId}", listOf("PUT")),
    BrapiServerCall("images/{imageDbId}/imagecontent", listOf("PUT")),
)