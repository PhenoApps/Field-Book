package com.fieldbook.tracker.cropontology.models

import androidx.room.Embedded
import com.fieldbook.tracker.cropontology.tables.Variable

data class RankedVariable(
    @Embedded
    val variable : Variable,
    val matchInfo: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RankedVariable

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
