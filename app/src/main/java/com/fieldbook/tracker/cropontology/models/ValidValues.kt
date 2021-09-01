package com.fieldbook.tracker.cropontology.models

import androidx.room.TypeConverters
import com.fieldbook.tracker.cropontology.converters.CategoryConverter
import com.google.gson.JsonArray
import com.google.gson.annotations.SerializedName

data class ValidValues(
    val min: String?,
    val max: String?,

    @SerializedName("categories")
    @TypeConverters(CategoryConverter::class)
    val categories: JsonArray?
) {
    override fun toString(): String {
        return "\tCategories: ${categories?.joinToString(",") { it.asString } ?: String()}"
    }
}