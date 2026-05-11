package com.fieldbook.tracker.utilities

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.widget.LinearLayout
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject

class VerifyPersonHelper @Inject constructor(@ActivityContext private val context: Context) {

    val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    private fun Int.hourToNano() = this * 3600 * 1e9.toLong()

    /**
     * Simple function that checks if the collect activity was opened >24hrs ago.
     * If the condition is met, it asks the user to reenter the collector id.
     */
    fun checkLastOpened() {

        val lastOpen: Long = preferences.getLong(GeneralKeys.LAST_TIME_OPENED, 0L)
        val alreadyAsked: Boolean = preferences.getBoolean(GeneralKeys.ASKED_SINCE_OPENED, false)
        val systemTime = System.nanoTime()

        //number of hours to wait before asking for user, pref found in profile
        val interval = when (preferences.getString(PreferenceKeys.VERIFICATION_INTERVAL, "2")) {
            "0" -> 0
            "1" -> 12
            "2" -> 24
            else -> -1
        }

        val nanosToWait = 1e9.toLong() * 3600 * interval
        if ((interval == 0 && !alreadyAsked) // ask on opening and app just opened
            || (interval > 0 && lastOpen != 0L && systemTime - lastOpen > nanosToWait)) { //ask after interval and interval has elapsed

            val firstName: String = preferences.getString(GeneralKeys.FIRST_NAME, "") ?: ""
            val lastName: String = preferences.getString(GeneralKeys.LAST_NAME, "") ?: ""
            if (firstName.isNotEmpty() || lastName.isNotEmpty()) {
                //person presumably has been set
                showAskCollectorDialog(
                    context.getString(R.string.activity_collect_dialog_verify_collector) + " " + firstName + " " + lastName + "?",
                    context.getString(R.string.activity_collect_dialog_verify_yes_button),
                    context.getString(R.string.activity_collect_dialog_neutral_button),
                    context.getString(R.string.activity_collect_dialog_verify_no_button)
                )
            } else {
                //person presumably hasn't been set
                showAskCollectorDialog(
                    context.getString(R.string.activity_collect_dialog_new_collector),
                    context.getString(R.string.activity_collect_dialog_verify_no_button),
                    context.getString(R.string.activity_collect_dialog_neutral_button),
                    context.getString(R.string.activity_collect_dialog_verify_yes_button)
                )
            }
        }
        preferences.edit().putBoolean(GeneralKeys.ASKED_SINCE_OPENED, true).apply()
    }

    private fun showAskCollectorDialog(
        message: String,
        positive: String,
        neutral: String,
        negative: String
    ) {
        val builder = AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(message)
            .setPositiveButton(positive, null)
            .setNeutralButton(neutral, null)
            .setNegativeButton(negative, null)

        val dialog = builder.create()

        dialog.setOnShowListener { dialogInterface ->
            val alertDialog = dialogInterface as AlertDialog

            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
            }

            alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                val preferenceIntent = Intent(context, PreferencesActivity::class.java)
                preferenceIntent.putExtra("ModifyProfileSettings", true)
                context.startActivity(preferenceIntent)
            }

            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                dialog.dismiss()
                val preferenceIntent = Intent(context, PreferencesActivity::class.java)
                preferenceIntent.putExtra("PersonUpdate", true)
                context.startActivity(preferenceIntent)
            }
        }

        dialog.show()

        val params = dialog.window?.attributes
        params?.width = LinearLayout.LayoutParams.MATCH_PARENT
        dialog.window?.attributes = params
    }

    fun updateLastOpenedTime() {
        preferences.edit().putLong(GeneralKeys.LAST_TIME_OPENED, System.nanoTime()).apply()
        preferences.edit().putBoolean(GeneralKeys.ASKED_SINCE_OPENED, false).apply()
    }
}

