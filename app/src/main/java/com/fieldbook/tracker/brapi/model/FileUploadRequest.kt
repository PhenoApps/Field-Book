package com.fieldbook.tracker.brapi.model

import com.google.gson.JsonObject
import java.time.OffsetDateTime

class FileUploadRequest(
    private var additionalInfoObject: AdditionalInfo,
    val fileName: String,
    val description: String,
    val timeStamp: OffsetDateTime
) {
    var additionalInfo: JsonObject
        get() {
            val jsonObject = JsonObject()
            jsonObject.addProperty("media", additionalInfoObject.media)
            jsonObject.addProperty("fieldId", additionalInfoObject.fieldId)
            return jsonObject
        }
        set(value) {
            additionalInfoObject = AdditionalInfo(
                value.get("media").asString,
                value.get("fieldId").asString)
        }
}

class AdditionalInfo(
    val media: String,
    val fieldId: String
)

