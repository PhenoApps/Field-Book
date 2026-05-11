package com.fieldbook.tracker.utilities

import com.fieldbook.tracker.traits.formats.coders.DateJsonCoder
import com.google.common.reflect.TypeToken
import com.google.gson.Gson

class DateJsonUtil {

    companion object {

        fun encode(dateJson: DateJsonCoder.DateJson): String {
            return Gson().toJson(
                dateJson,
                object : TypeToken<DateJsonCoder.DateJson>() {}.type
            )
        }

        fun decode(json: String): Any {
            return if (json == "NA" || !JsonUtil.isJsonValid(json)) json
            else Gson().fromJson(
                json,
                object : TypeToken<DateJsonCoder.DateJson>() {}.type
            ) as DateJsonCoder.DateJson
        }
    }
}