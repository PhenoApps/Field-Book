package com.fieldbook.tracker.activities.brapi.io

/**
 * Add new endpoints that are added to the BrAPI spec
 * https://brapi.org/specification
 *
 * Below version is as of November 12, 2025
 */
val coreEndpoints = setOf(
    "commoncropnames", "lists", "locations", "people", "programs",
    "seasons", "serverinfo", "studies", "studytypes", "trials",
)

val phenotypingEndpoints = setOf(
    "events", "images", "methods", "observationlevels",
    "observationunits", "observations", "ontologies", "scales",
    "traits", "variables",
)

val germplasmEndpoints = setOf(
    "germplasm", "breedingmethods", "seedlots", "crosses",
    "crossingprojects", "plannedcrosses", "pedigree", "progeny",
    "attributes", "attributevalues",
)

val genotypingEndpoints = setOf(
    "allelematrix", "callsets", "calls", "maps", "markerpositions",
    "plates", "referencesets", "references", "samples",
    "variantsets", "variants", "vendor",
)