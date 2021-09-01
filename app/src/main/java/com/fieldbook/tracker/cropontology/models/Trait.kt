package com.fieldbook.tracker.cropontology.models

import com.google.gson.annotations.SerializedName

data class Trait(
    val traitDbId: String?,
    @SerializedName("name")
    val traitName: String?,
    @SerializedName("class")
    val traitClass: String?,
    @SerializedName("description")
    val traitDescription: String?,
    val entity: String?,
    val attribute: String?,
) {
    override fun toString(): String {
        return "\n\t Name: $traitName \n\t Class: $traitClass \n\t $entity \n\t $attribute \n\t $traitDescription"
    }
}