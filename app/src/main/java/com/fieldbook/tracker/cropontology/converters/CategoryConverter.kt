package com.fieldbook.tracker.cropontology.converters

import androidx.room.TypeConverter
import com.google.gson.JsonArray

class CategoryConverter {

    @TypeConverter
    fun jsonArrayToString(cat: JsonArray?): String {
        return cat?.joinToString(",") { it.asString } ?: ""
    }

    @TypeConverter
    fun stringToJson(commaDelim: String?): JsonArray {
        return JsonArray().apply {
            commaDelim?.split(",")?.forEach {
                this.add(it)
            }
        }
    }
}