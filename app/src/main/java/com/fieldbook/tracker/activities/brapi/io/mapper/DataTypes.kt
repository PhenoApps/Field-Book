package com.fieldbook.tracker.activities.brapi.io.mapper

import java.util.Locale

/**
 * Static class for converting BrAPI variable formats to Field Book variable formats
 */
class DataTypes {
    companion object {
        /**
         * @param dataType, from the brapi observation variable scale
         * @return string format of Field Book data type
         */
        fun convertBrAPIDataType(dataType: String): String {
            return when (dataType.lowercase(Locale.getDefault())) {
                "nominal", "ordinal", "categorical", "qualitative" ->                 // All Field Book categories are ordered, so this works
                    "categorical"

                "date" -> "date"
                "numerical", "duration", "numeric" -> "numeric"
                "rust rating", "disease rating" -> "disease rating"
                "percent" -> "percent"
                "boolean" -> "boolean"
                "photo" -> "photo"
                "audio" -> "audio"
                "counter" -> "counter"
                "multicat" -> "multicat"
                "location" -> "location"
                "barcode" -> "barcode"
                "gnss" -> "gnss"
                "zebra label printer", "zebra label print" -> "zebra label print"
                "usb camera" -> "usb camera"
                "code", "text" -> "text"
                else -> "text"
            }
        }
    }
}
