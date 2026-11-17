package com.fieldbook.tracker.utilities

import android.text.format.DateFormat
import com.fieldbook.tracker.traits.formats.coders.DateJsonCoder
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DateJsonUtil {

    companion object {

        private const val MAX_DAY_OF_YEAR = 366
        private const val MAX_DAY_OF_YEAR_COMMON = 365

        /** Non-leap year used only to turn a day of year back into a month and day for a label. */
        private const val REFERENCE_YEAR = 2001

        private const val ISO_DATE_PATTERN = "yyyy-MM-dd"

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

        /**
         * Recovers the day of year from a stored date observation.
         *
         * Stored values take several shapes: the current DateJson, a DateJson that the version 19
         * migration built out of a bare day of year (whose formattedDate carries a "????" year),
         * and the occasional plain string left behind when a BrAPI import could not parse an
         * incoming date. dayOfYear is the only field populated across all of them, so it is read
         * first and the formatted date is only parsed as a fallback.
         *
         * Returns null when nothing usable can be recovered, so callers can drop the value rather
         * than place it somewhere arbitrary on an axis.
         */
        fun extractDayOfYear(value: String): Int? {

            if (value.isBlank() || value == "NA") return null

            val decoded = try {
                decode(value)
            } catch (e: Exception) {
                value
            }

            val dateJson = decoded as? DateJsonCoder.DateJson

            // Gson leaves absent members null whatever the declared type says, so these are read
            // through nullable locals rather than trusted to be non-null.
            val encodedDayOfYear: String? = dateJson?.dayOfYear
            encodedDayOfYear?.trim()?.toIntOrNull()?.let {
                if (it in 1..MAX_DAY_OF_YEAR) return it
            }

            val formattedDate: String? = dateJson?.formattedDate
            return parseDayOfYear(formattedDate ?: value)
        }

        private fun parseDayOfYear(value: String): Int? {

            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null

            // A bare number is a day of year recorded before the json encoding existed.
            trimmed.toIntOrNull()?.let {
                return if (it in 1..MAX_DAY_OF_YEAR) it else null
            }

            return try {
                // A "????" year fails to parse here, but the migration only writes one when it
                // also wrote a usable dayOfYear, so those values never reach this point.
                val parsed = SimpleDateFormat(ISO_DATE_PATTERN, Locale.getDefault())
                    .parse(trimmed) ?: return null

                Calendar.getInstance().apply { time = parsed }.get(Calendar.DAY_OF_YEAR)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Renders a day of year as a calendar date for a chart axis.
         *
         * Charting collapses every year onto a single axis, so there is no real year to format
         * against and a fixed non-leap reference year stands in. Data recorded during a leap year
         * therefore labels one day early from March onwards, which stays within a bin at any
         * realistic bin width.
         */
        fun formatDayOfYear(dayOfYear: Int, locale: Locale = Locale.getDefault()): String {

            val calendar = Calendar.getInstance().apply {
                clear()
                set(Calendar.YEAR, REFERENCE_YEAR)
                set(Calendar.DAY_OF_YEAR, dayOfYear.coerceIn(1, MAX_DAY_OF_YEAR_COMMON))
            }

            val pattern = DateFormat.getBestDateTimePattern(locale, "MMMd")
            return SimpleDateFormat(pattern, locale).format(calendar.time)
        }
    }
}