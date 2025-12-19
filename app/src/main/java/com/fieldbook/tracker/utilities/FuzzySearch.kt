package com.fieldbook.tracker.utilities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.activities.ConfigActivity
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.models.ObservationUnitModel
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.preferences.GeneralKeys
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject

data class BarcodeMatch(val field: FieldObject?, val plotId: String?)


class FuzzySearch @Inject constructor(@param:ActivityContext private val context: Context) {

    @Inject
    lateinit var database: DataHelper

    @Inject
    lateinit var preferences: SharedPreferences

    @Inject
    lateinit var soundHelper: SoundHelperImpl

    @Inject
    lateinit var fieldSwitcher: FieldSwitchImpl

    private fun searchStudiesForBarcode(barcode: String?): FieldObject? {

        val fields: ArrayList<FieldObject?> = database.getAllFieldObjects()

        // first, search to try and match study alias
        for (f in fields) {
            if (f != null && f.alias != null && f.alias == barcode) {
                return f
            }
        }

        // second, if field is not found search for study name
        for (f in fields) {
            if (f != null && f.name != null && f.name == barcode) {
                return f
            }
        }

        return null
    }

    private fun searchPlotsForBarcode(barcode: String?): ObservationUnitModel? {

        // search for barcode in database
        val models: Array<ObservationUnitModel> = database.getAllObservationUnits()
        for (m in models) {
            if (m.observation_unit_db_id == barcode) {
                return m
            }
        }

        return null
    }

    //1) study alias, 2) study names, 3) plotdbids
    fun fuzzyBarcodeSearch(barcode: String?) {

        // search for studies
        val f = searchStudiesForBarcode(barcode)

        if (f == null) {

            // search for plots
            val m = searchPlotsForBarcode(barcode)

            if (m != null && m.study_id != -1) {
                val study: FieldObject? = database.getFieldObject(m.study_id)

                resolveFuzzySearchResult(study!!, barcode)
            } else {
                soundHelper.playError()

                Utils.makeToast(
                    context,
                    context.getString(R.string.act_config_fuzzy_search_failed, barcode)
                )
            }

        } else {

            resolveFuzzySearchResult(f, null)
        }
    }

    fun findBarcodeMatch(barcode: String?): BarcodeMatch {

        try {
            val fields: ArrayList<FieldObject?> = database.getAllFieldObjects()
            for (field in fields) {
                if (field == null) continue
                val units = database.getObservationUnitsBySearchAttribute(field.studyId, barcode)
                if (units != null && units.isNotEmpty()) {
                    return BarcodeMatch(field, units[0].observation_unit_db_id)
                }
            }
        } catch (_: Exception) {}

        val m = searchPlotsForBarcode(barcode)
        if (m != null && m.study_id != -1) {
            val study: FieldObject? = database.getFieldObject(m.study_id)
            if (study != null) {
                return BarcodeMatch(study, m.observation_unit_db_id)
            }
        }

        return BarcodeMatch(null, null)
    }

    private fun resolveFuzzySearchResult(f: FieldObject, plotId: String?) {

        soundHelper.playCelebrate()

        val studyId: Int = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)

        val newStudyId = f.studyId

        CollectActivity.reloadData = true

        if (plotId != null) {
            preferences.edit { putString(GeneralKeys.LAST_PLOT, plotId) }
        }

        if (studyId != newStudyId) {

            fieldSwitcher.switchField(newStudyId)

            val intent = Intent(context, ConfigActivity::class.java)

            intent.putExtra(ConfigActivity.LOAD_FIELD_ID, newStudyId)

            (context as? Activity)?.startActivity(intent)

        }
    }
}