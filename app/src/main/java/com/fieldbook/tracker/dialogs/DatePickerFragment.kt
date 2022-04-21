package com.fieldbook.tracker.dialogs

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.widget.DatePicker
import androidx.fragment.app.DialogFragment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.PrefsConstants
import java.text.SimpleDateFormat
import java.util.*

/**
 * Creates a date picker dialog.
 * From Android Dev documentation https://developer.android.com/guide/topics/ui/controls/pickers#java
 * The date trait layout sends the observed or current date and format to parse.
 * This class also has an onSet callback which sends back the year, month and day to be saved.
 */
class DatePickerFragment(val format: SimpleDateFormat,
                         val onSet: (Int, Int, Int) -> Boolean) : DialogFragment(), DatePickerDialog.OnDateSetListener {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val date = context?.getSharedPreferences("Settings", Context.MODE_PRIVATE)
            ?.getString(GeneralKeys.CALENDAR_LAST_SAVED_DATE, "2000-01-01") ?: "2000-01-01"

        // Use the current date as the default date in the picker
        val c = Calendar.getInstance()

        //parse the date parameter and set the calendar's time
        format.parse(date)?.let { savedDate ->
            c.time = savedDate
        }

        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)

        // Create a new instance of DatePickerDialog and return it
        val dialog = DatePickerDialog(requireContext(), this, year, month, day)

        //set a 'today' neutral button that resets the calendar to today's date
        dialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.dialog_date_picker_neutral_button)) { _,_ -> }

        //get neutral button when dialog is shown and update the calendar to today's date
        dialog.setOnShowListener {

            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {

                val today = Calendar.getInstance()

                dialog.updateDate(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH))

            }
        }

        return dialog
    }

    override fun onDateSet(view: DatePicker, year: Int, month: Int, day: Int) {
        // Do something with the date chosen by the user
        onSet(year, month, day)
    }
}