package com.fieldbook.tracker.utilities

import android.app.Activity
import android.app.AlertDialog
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
import com.fieldbook.tracker.preferences.PreferenceKeys
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject

data class BarcodeMatch(val field: FieldObject?, val plotId: String?)

/**
 * A close but inexact match for a barcode.
 * @param plotId the matched observation unit db id, or null when the field itself matched
 * @param text the alias, name, id or search attribute value that matched
 */
data class SuggestedMatch(val field: FieldObject, val plotId: String?, val text: String)


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

                val suggestions = if (isSuggestSimilarEnabled()) findSimilarFieldsAndPlots(barcode) else emptyList()

                if (suggestions.isNotEmpty()) {
                    showSuggestionsDialog(barcode, suggestions) { resolveFuzzySearchResult(it.field, it.plotId) }
                } else {
                    Utils.makeToast(
                        context,
                        context.getString(R.string.act_config_fuzzy_search_failed, barcode)
                    )
                }
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

    fun isSuggestSimilarEnabled(): Boolean =
        preferences.getBoolean(PreferenceKeys.BARCODE_SUGGEST_SIMILAR, true)

    private data class Candidate(val field: FieldObject, val plotId: String?, val texts: List<String?>)

    private fun plotCandidates(field: FieldObject): List<Candidate> =
        database.getObservationUnitSearchAttributeValues(field.studyId).map { (plotId, searchValue) ->
            Candidate(field, plotId, listOf(plotId, searchValue))
        }

    private fun rank(barcode: String?, candidates: List<Candidate>): List<SuggestedMatch> =
        IdMatcher.rank(barcode, candidates, { it.texts }, { it.plotId ?: "field:${it.field.studyId}" })
            .map { SuggestedMatch(it.item.field, it.item.plotId, it.text) }

    /**
     * Finds plots whose id or search attribute value is close to [barcode],
     * looking in the current field first and in all fields only if it has none.
     */
    fun findSimilarPlots(barcode: String?, currentStudyId: Int): List<SuggestedMatch> {

        val fields = database.getAllFieldObjects().filterNotNull()

        fields.firstOrNull { it.studyId == currentStudyId }?.let { current ->
            val matches = rank(barcode, plotCandidates(current))
            if (matches.isNotEmpty()) return matches
        }

        return rank(barcode, fields.filter { it.studyId != currentStudyId }.flatMap { plotCandidates(it) })
    }

    /**
     * Finds fields whose alias or name, and plots whose id or search attribute value,
     * are close to [barcode], across all fields.
     */
    fun findSimilarFieldsAndPlots(barcode: String?): List<SuggestedMatch> {

        val fields = database.getAllFieldObjects().filterNotNull()

        val candidates = fields.map { Candidate(it, null, listOf(it.alias, it.name)) } +
                fields.flatMap { plotCandidates(it) }

        return rank(barcode, candidates)
    }

    /**
     * Lets the user pick one of [suggestions] after [barcode] had no exact match.
     * Never navigates on its own, so data is not recorded against a plot the user did not confirm.
     */
    fun showSuggestionsDialog(
        barcode: String?,
        suggestions: List<SuggestedMatch>,
        onPick: (SuggestedMatch) -> Unit
    ) {

        val labels = suggestions.map {
            if (it.plotId == null) {
                context.getString(R.string.barcode_suggestion_field, it.text)
            } else {
                context.getString(R.string.barcode_suggestion_in_field, it.text, it.field.alias ?: it.field.name)
            }
        }.toTypedArray<CharSequence>()

        AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(context.getString(R.string.barcode_suggestions_title, barcode))
            .setItems(labels) { dialog, which ->
                dialog.dismiss()
                onPick(suggestions[which])
            }
            .setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
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

        }

        // ConfigActivity opens collect for LOAD_FIELD_ID, which then moves to LAST_PLOT
        val intent = Intent(context, ConfigActivity::class.java)

        intent.putExtra(ConfigActivity.LOAD_FIELD_ID, newStudyId)

        (context as? Activity)?.startActivity(intent)
    }
}