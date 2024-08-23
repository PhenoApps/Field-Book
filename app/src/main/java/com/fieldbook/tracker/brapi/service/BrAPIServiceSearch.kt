package com.fieldbook.tracker.brapi.service

import androidx.arch.core.util.Function
import org.brapi.v2.model.core.BrAPIProgram
import org.brapi.v2.model.core.BrAPIStudy
import org.brapi.v2.model.core.BrAPITrial
import org.brapi.v2.model.core.request.BrAPIStudySearchRequest

//TODO docs
interface BrAPIServiceSearch {

    //search calls
//    fun searchPrograms(
//        lastPage: Int,
//        pages: Int,
//        programDbIds: List<String?>?,
//        failFunction: Function<Int?, Void?>?
//    ): android.util.Pair<Int, Map<String?, BrAPIProgram?>?>
//
//    fun searchTrials(
//        lastPage: Int,
//        pages: Int,
//        programDbIds: List<String?>?,
//        seasonDbIds: List<String?>?,
//        trialDbIds: List<String?>?,
//        failFunction: Function<Int?, Void?>?
//    ): android.util.Pair<Int, Map<String?, BrAPITrial?>?>

    fun searchStudies(
        requestParams: BrAPIStudySearchRequest?,
        failFunction: Function<Int?, Void?>?
    ): android.util.Pair<Int, Map<String?, BrAPIStudy?>?>
}