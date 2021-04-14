package com.fieldbook.tracker.brapi.typeadapters

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Brapi type adapter that catches parsing exceptions and returns a default date.
 * This can only be used in O and above because BrAPI dates use java.time.OffsetDateTime
 */
@RequiresApi(Build.VERSION_CODES.O)
class OffsetDateTimeTypeAdapter(private var formatter: DateTimeFormatter) : TypeAdapter<OffsetDateTime?>() {

    @Throws(IOException::class)
    override fun write(out: JsonWriter, date: OffsetDateTime?) {
        if (date == null) {
            out.nullValue()
        } else {
            out.value(formatter.format(date))
        }
    }

    @Throws(IOException::class)
    override fun read(reader: JsonReader): OffsetDateTime? {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            else -> {
                var date = reader.nextString()
                if (date.endsWith("+0000")) {
                    date = date.substring(0, date.length - 5) + "Z"
                }
                try {
                    OffsetDateTime.parse(date, formatter)
                } catch (e: DateTimeParseException) {
                    e.printStackTrace()
                    OffsetDateTime.MIN
                }
            }
        }
    }
}