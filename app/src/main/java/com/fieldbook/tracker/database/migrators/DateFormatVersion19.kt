package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase
import com.fieldbook.tracker.traits.formats.coders.DateJsonCoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DateFormatVersion19 : FieldBookMigrator {

    companion object {
        const val TAG = "DateFormatVersion19"
        const val VERSION = 19
        private const val UNKNOWN_YEAR = "????"
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {

        //get all observations with variable format "date"
        val cursor = db.rawQuery("""
            SELECT internal_id_observation, value 
            FROM observations AS O
            JOIN observation_variables AS OV ON O.observation_variable_db_id = OV.internal_id_observation_variable
            WHERE OV.observation_variable_field_book_format = 'date'
            """, null)

        //encode the value into json and update the observation
        while (cursor.moveToNext()) {
            val observationId = cursor.getString(0)
            val value = cursor.getString(1)
            if (value != null) {

                val dateJson = DateJsonCoder.DateJson(
                    formattedDate = "",
                    dayOfYear = ""
                )

                //use date formatter/calendar to fill formatted and year dates into json
                try {
                    val date = dateFormat.parse(value)
                    dateJson.formattedDate = value
                    dateJson.dayOfYear = if (date != null) {
                        val calendar = Calendar.getInstance()
                        //set calendar based on formatted time
                        calendar.time = date
                        calendar.get(Calendar.DAY_OF_YEAR).toString()
                    } else {
                        ""
                    }
                } catch (e: Exception) {
                    dateJson.dayOfYear = value
                    //format day of year into formatted date
                    try {
                        val dayOfYear = value.toInt()
                        val year = getYear(dayOfYear)

                        val calendar = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.DAY_OF_YEAR, dayOfYear)
                        }

                        val mm = String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.MONTH) + 1)
                        val dd = String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.DAY_OF_MONTH))

                        dateJson.formattedDate = "$UNKNOWN_YEAR-$mm-$dd"
                    } catch (e: Exception) {
                        dateJson.formattedDate = ""
                    }
                }

                val encodedValue = DateJsonCoder().encode(dateJson)
                db.execSQL("UPDATE observations SET value = ? WHERE internal_id_observation = ?", arrayOf(encodedValue, observationId))
            }
        }

        cursor.close()
    }

    private fun getYear(dayOfYear: Int): Int {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return if (dayOfYear == 366) { // find the most recent leap year
            var year = currentYear
            val tempCalendar = Calendar.getInstance()
            while (true) {
                tempCalendar.set(Calendar.YEAR, year)
                if (tempCalendar.getActualMaximum(Calendar.DAY_OF_YEAR) == 366) {
                    break
                }
                year--
            }
            year
        } else {
            currentYear
        }
    }
}