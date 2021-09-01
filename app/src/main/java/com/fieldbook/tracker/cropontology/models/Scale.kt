package com.fieldbook.tracker.cropontology.models

import androidx.room.Embedded
import com.google.gson.annotations.SerializedName

data class Scale(

    val scaleDbId: String?,

    @SerializedName("name")
    val scaleName: String?,
    val dataType: String?,
    val decimalPlaces: String?,

    @Embedded
    val validValues: ValidValues?,

    ) {
    override fun toString(): String {
        return "\n\tName: $scaleName \n\t Data Type: $dataType $validValues"
    }
}