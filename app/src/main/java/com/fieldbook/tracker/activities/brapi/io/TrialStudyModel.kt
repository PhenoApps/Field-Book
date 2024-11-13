package com.fieldbook.tracker.activities.brapi.io

import org.brapi.v2.model.core.BrAPIStudy
import org.brapi.v2.model.pheno.BrAPIObservationVariable

data class BrapiCacheModel(
    var studies: List<TrialStudyModel>,
    var variables: MutableMap<String, BrAPIObservationVariable>
) {
    companion object {
        fun empty() = BrapiCacheModel(emptyList(), mutableMapOf())
    }
}

data class TrialStudyModel(
    val study: BrAPIStudy,
    var trialDbId: String? = null,
    var trialName: String? = null,
    var programDbId: String? = null,
    var programName: String? = null,
)