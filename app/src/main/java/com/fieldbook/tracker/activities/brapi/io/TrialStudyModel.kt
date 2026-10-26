package com.fieldbook.tracker.activities.brapi.io

import org.brapi.v2.model.core.BrAPIStudy
import org.brapi.v2.model.pheno.BrAPIObservationVariable

data class BrapiCacheModel(
    var studies: List<TrialStudyModel>,
    var variables: MutableMap<String, BrAPIObservationVariable>,
    //null until the server's study lists are fetched, see BrapiStudyFilterActivity.fetchStudyLists
    var studyLists: List<StudyListModel>? = null
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

/**
 * A server list of studies, used as a study filter, see BrapiStudyListsFilterActivity.
 * studyDbIds is null until the list's items are fetched, which happens when it's chosen as a filter.
 */
data class StudyListModel(
    val listDbId: String,
    val listName: String?,
    val listOwnerName: String?,
    val listSize: Int?,
    val studyDbIds: List<String>? = null
)