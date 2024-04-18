package com.fieldbook.tracker.activities.brapi.hackathon.cropontology.models

import androidx.room.Embedded
import com.fieldbook.tracker.activities.brapi.hackathon.cropontology.tables.BrapiStudy

data class RankedStudy(
    @Embedded
    val variable : BrapiStudy,
    val matchInfo: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RankedStudy

        if (variable != other.variable) return false
        if (!matchInfo.contentEquals(other.matchInfo)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = variable.hashCode()
        result = 31 * result + matchInfo.contentHashCode()
        return result
    }
}
