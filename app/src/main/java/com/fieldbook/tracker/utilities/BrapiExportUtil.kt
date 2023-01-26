package com.fieldbook.tracker.utilities

import com.fieldbook.tracker.brapi.model.Observation

class BrapiExportUtil {

    companion object {

        fun firstIndexOfDbId(observations: List<Observation>, item: Observation): Int {
            return observations.indexOfFirst { it.fieldBookDbId == item.fieldBookDbId }
        }

        fun lastIndexOfDbId(observations: List<Observation>, item: Observation): Int {
            return observations.indexOfLast { it.fieldBookDbId == item.fieldBookDbId }
        }
    }
}