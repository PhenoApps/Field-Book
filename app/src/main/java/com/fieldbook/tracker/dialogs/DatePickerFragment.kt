package com.fieldbook.tracker.dialogs

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.DatePicker
import androidx.fragment.app.DialogFragment
import java.text.SimpleDateFormat
import java.util.*

/**
 * Creates a date picker dialog.
 * From Android Dev documentation https://developer.android.com/guide/topics/ui/controls/pickers#java
 * The date trait layout sends the observed or current date and format to parse.
 * This class also has an onSet callback which sends back the year, month and day to be saved.
 */
class DatePickerFragment(val format: SimpleDateFormat,
                         val date: String,
                         val onSet: (Int, Int, Int) -> Boolean) : DialogFragment(), DatePickerDialog.OnDateSetListener {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Use the current date as the default date in the picker
        val c = Calendar.getInstance()

        format.parse(date)?.let { savedDate ->
            c.time = savedDate
        }

        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)

        // Create a new instance of DatePickerDialog and return it
        return DatePickerDialog(requireContext(), this, year, month, day)
    }

    override fun onDateSet(view: DatePicker, year: Int, month: Int, day: Int) {
        // Do something with the date chosen by the user
        onSet(year, month, day)
    }
}