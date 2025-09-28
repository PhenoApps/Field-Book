package com.fieldbook.tracker.dialogs

import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.AttributeAdapter.AttributeModel
import com.fieldbook.tracker.preferences.DropDownKeyModel
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.Utils

class GeoNavCollectDialog(private val activity: CollectActivity) :
    AlertDialog.Builder(activity, R.style.AppAlertDialog) {

    companion object {
        private const val GEO_NAV_RESTART_DELAY_MS = 1000L
    }

    private val preferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private var auto
        get() = preferences.getBoolean(GeneralKeys.GEONAV_AUTO, false)
        set(value) {
            preferences.edit { putBoolean(GeneralKeys.GEONAV_AUTO, value) }
        }

    private var audioOnDrop
        get() = preferences.getBoolean(GeneralKeys.GEONAV_CONFIG_AUDIO_ON_DROP, false)
        set(value) {
            preferences.edit { putBoolean(GeneralKeys.GEONAV_CONFIG_AUDIO_ON_DROP, value) }
        }

    private var degreeOfPrecision
        get() = preferences.getString(GeneralKeys.GEONAV_CONFIG_DEGREE_PRECISION, "Any")
        set(value) {
            preferences.edit { putString(GeneralKeys.GEONAV_CONFIG_DEGREE_PRECISION, value) }
        }

    private var geoNavPopupDisplay: AttributeModel
        get() {
            if (geoNavPopupTrait == DropDownKeyModel.DEFAULT_TRAIT_ID) {
                return AttributeModel(geoNavPopupAttribute)
            } else {
                val trait = activity.getDatabase().getTraitById(geoNavPopupTrait)
                return AttributeModel(trait.alias, trait = trait)
            }
        }
        set(value) {
            if (value.trait == null) {
                geoNavPopupAttribute = value.label
                geoNavPopupTrait = DropDownKeyModel.DEFAULT_TRAIT_ID
            } else {
                geoNavPopupAttribute = value.label
                geoNavPopupTrait = value.trait.id
            }
        }

    private var geoNavPopupAttribute
        get() = preferences.getString(GeneralKeys.GEONAV_POPUP_DISPLAY, DropDownKeyModel.DEFAULT_ATTRIBUTE_LABEL) ?: DropDownKeyModel.DEFAULT_ATTRIBUTE_LABEL
        set(value) {
            preferences.edit { putString(GeneralKeys.GEONAV_POPUP_DISPLAY, value) }
        }

    private var geoNavPopupTrait
        get() = preferences.getString(GeneralKeys.GEONAV_POPUP_TRAIT, DropDownKeyModel.DEFAULT_TRAIT_ID) ?: DropDownKeyModel.DEFAULT_TRAIT_ID
        set(value) {
            preferences.edit { putString(GeneralKeys.GEONAV_POPUP_TRAIT, value) }
        }

    private var autoNavigateCb: CheckBox? = null
    private var audioOnDropCb: CheckBox? = null
    private var degreeOfPrecisionSp: Spinner? = null
    private var geoNavPopupDisplaySp: Spinner? = null
    private var geoNavAttributeModels: List<AttributeModel> = emptyList()

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
        geoNavAttributeModels = activity.getGeoNavPopupSpinnerItems()
        geoNavPopupDisplayAdapter.addAll(geoNavAttributeModels.map { it.label })

        // Set the ArrayAdapter to the Spinner
        geoNavPopupDisplaySp?.adapter = geoNavPopupDisplayAdapter

        loadPreferencesIntoUi()

        setNeutralButton(context.getString(R.string.dialog_geonav_collect_neutral_reconnect)) { dialog, which ->
            Utils.makeToast(context, context.getString(R.string.dialog_geonav_collect_reset_start_toast_message))
            activity.getGeoNavHelper().stopGeoNav()
            waitForGeoNavStopped()
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

    //recursive function that waits for geo nav threads to stop and then restarts
    private fun waitForGeoNavStopped() {

        if (!activity.getGeoNavHelper().initialized) {

            activity.getGeoNavHelper().startGeoNav()

        } else {

            Handler(Looper.getMainLooper()).postDelayed({

                waitForGeoNavStopped()

            }, GEO_NAV_RESTART_DELAY_MS)
        }
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
        auto = autoNavigateCb?.isChecked == true
        audioOnDrop = audioOnDropCb?.isChecked == true
        degreeOfPrecision = degreeOfPrecisionSp?.selectedItem.toString()
        geoNavPopupDisplay = geoNavAttributeModels[geoNavPopupDisplaySp?.selectedItemPosition ?: 0]
    }
}