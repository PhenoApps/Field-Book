package com.fieldbook.tracker.dialogs

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Button
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

    private var autoNavigateCb: CheckBox? = null
    private var audioOnDropCb: CheckBox? = null
    private var degreeOfPrecisionSp: Spinner? = null
    private var reconnectButton: Button? = null

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
        reconnectButton = view.findViewById(R.id.dialog_geonav_collect_reconnect)

        loadPreferencesIntoUi()

        setNegativeButton(android.R.string.cancel) { dialog, which ->
            dialog.dismiss()
        }

        setPositiveButton(android.R.string.ok) { dialog, which ->
            saveUiToPreferences()
            dialog.dismiss()
        }

        reconnectButton?.setOnClickListener {
            reconnectButton?.isEnabled = false
            Utils.makeToast(context, context.getString(R.string.dialog_geonav_collect_reset_start_toast_message))
            activity.getGeoNavHelper().stopGeoNav()
            activity.getGeoNavHelper().startGeoNav()
            Utils.makeToast(context, context.getString(R.string.dialog_geonav_collect_reset_end_toast_message))

            Handler(Looper.getMainLooper()).postDelayed({
                reconnectButton?.isEnabled = true
            }, 5000)
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
    }

    private fun saveUiToPreferences() {
        auto = autoNavigateCb?.isChecked ?: false
        audioOnDrop = audioOnDropCb?.isChecked ?: false
        degreeOfPrecision = degreeOfPrecisionSp?.selectedItem.toString()
    }
}