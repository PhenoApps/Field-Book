package com.fieldbook.tracker.traits.formats.coders

import com.fieldbook.tracker.utilities.DateJsonUtil

class DateJsonCoder : StringCoder {

    data class DateJson(
        var formattedDate: String,
        var dayOfYear: String
    )

    override fun encode(value: Any): String {

        return DateJsonUtil.encode(value as DateJson)
    }

    override fun decode(value: String): Any {

        var decoded: Any = value

        try {

            decoded = DateJsonUtil.decode(value)

        } catch (e: Exception) {

            e.printStackTrace()

        }

        return decoded
    }
}
