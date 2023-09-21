package com.fieldbook.tracker.dialogs

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.Utils

class GeoNavCollectDialog(private val activity: CollectActivity) :
    AlertDialog.Builder(activity, R.style.AppAlertDialog) {
      
    private val prefs by lazy {
        context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
    }

    private var auto
        get() = prefs.getBoolean(GeneralKeys.GEONAV_AUTO, false)
        set(value) {
            prefs.edit().putBoolean(GeneralKeys.GEONAV_AUTO, value).apply()
        }

    private var audioOnDrop
        get() = prefs.getBoolean(GeneralKeys.GEONAV_CONFIG_AUDIO_ON_DROP, false)
        set(value) {
            prefs.edit().putBoolean(GeneralKeys.GEONAV_CONFIG_AUDIO_ON_DROP, value).apply()
        }

    private var degreeOfPrecision
        get() = prefs.getString(GeneralKeys.GEONAV_CONFIG_DEGREE_PRECISION, "Any")
        set(value) {
            prefs.edit().putString(GeneralKeys.GEONAV_CONFIG_DEGREE_PRECISION, value).apply()
        }

    private var geoNavPopupDisplay
        get() = prefs.getString(GeneralKeys.GEONAV_POPUP_DISPLAY, "plot_id")
        set(value) {
            prefs.edit().putString(GeneralKeys.GEONAV_POPUP_DISPLAY, value).apply()
        }

    private var autoNavigateCb: CheckBox? = null
    private var audioOnDropCb: CheckBox? = null
    private var degreeOfPrecisionSp: Spinner? = null
    private var geoNavPopupDisplaySp: Spinner? = null

    private val view by lazy {
        LayoutInflater.from(context).inflate(R.layout.dialog_geonav_collect, null, false)
    }

    init {
        setView(R.layout.dialog_geonav_collect)
    }

    override fun setView(layoutResId: Int): AlertDialog.Builder {

        setTitle(R.string.dialog_geonav_collect_title)

        autoNavigateCb = view.findViewById(R.id.dialog_geonav_collect_auto_navigate)
        audioOnDropCb = view.findViewById(R.id.dialog_geonav_collect_notify_on_precision_loss)
        degreeOfPrecisionSp = view.findViewById(R.id.dialog_geonav_collect_precision_threshold)
        geoNavPopupDisplaySp = view.findViewById(R.id.dialog_geonav_popup_display)

        // fetching spinner items
        val geoNavPopupDisplayAdapter = ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item)
        geoNavPopupDisplayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        geoNavPopupDisplayAdapter.addAll(activity.getGeoNavPopupSpinnerItems())

        // Set the ArrayAdapter to the Spinner
        geoNavPopupDisplaySp?.adapter = geoNavPopupDisplayAdapter

        loadPreferencesIntoUi()

        setNeutralButton(context.getString(R.string.dialog_geonav_collect_neutral_reconnect)) { dialog, which ->
            Utils.makeToast(context, context.getString(R.string.dialog_geonav_collect_reset_start_toast_message))
            activity.getGeoNavHelper().stopGeoNav()
            activity.getGeoNavHelper().startGeoNav()
            Utils.makeToast(context, context.getString(R.string.dialog_geonav_collect_reset_end_toast_message))
            dialog.dismiss()
        }

        setNegativeButton(android.R.string.cancel) { dialog, which ->
            dialog.dismiss()
        }

        setPositiveButton(android.R.string.ok) { dialog, which ->
            saveUiToPreferences()
            dialog.dismiss()
        }

        return super.setView(view)
    }

    private fun loadPreferencesIntoUi() {

        autoNavigateCb?.isChecked = auto
        audioOnDropCb?.isChecked = audioOnDrop
        degreeOfPrecisionSp?.setSelection(
            when (degreeOfPrecision) {
                "GPS" -> 1
                "RTK" -> 2
                "Float RTK" -> 3
                else -> 0
            }
        )

        // set text for geoNavPopupDisplaySp based on preferences
        val popupItem = activity.getGeoNavPopupSpinnerItems()
        val index = popupItem.indexOf(geoNavPopupDisplay)
        // if the attribute/trait cannot be found
        // then default to 'plot_id'
        val selection = if( index != -1 ) index else 0
        // preferences will be updated in getPopupInfo method in GeoNavHelper.kt
        geoNavPopupDisplaySp?.setSelection(selection)
    }

    private fun saveUiToPreferences() {
        auto = autoNavigateCb?.isChecked ?: false
        audioOnDrop = audioOnDropCb?.isChecked ?: false
        degreeOfPrecision = degreeOfPrecisionSp?.selectedItem.toString()
        geoNavPopupDisplay = geoNavPopupDisplaySp?.selectedItem.toString()
    }
}