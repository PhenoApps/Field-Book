package com.fieldbook.tracker.cropontology.models

import com.google.gson.annotations.SerializedName

data class Method(
    val methodDbId: String?,
    @SerializedName("name")
    val methodName: String?,
    @SerializedName("class")
    val methodClass: String?,
    val description: String?,
    val formula: String?,
    val reference: String?
) {
    override fun toString(): String {
        return "\n\t Name: $methodName Class: $methodClass \n\t Formula: $formula \n\t $description"
    }
}