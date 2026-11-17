package com.fieldbook.tracker.activities.brapi.io.mapper

import android.content.Context
import android.util.Log
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2.ADDITIONAL_INFO_OBSERVATION_LEVEL_NAMES
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.SynonymsUtil
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.brapi.v2.model.pheno.BrAPIObservationVariable

/**
 * File for extension functions to convert BrAPI objects to FieldBook objects
 */

fun BrAPIObservationVariable.toTraitObject(context: Context) = TraitObject().also {

    it.defaultValue = defaultValue ?: ""
    it.name = observationVariableName
    it.alias = observationVariableName

    it.synonyms = SynonymsUtil.addAliasToSynonyms(observationVariableName, synonyms ?: emptyList())

    it.details = trait.traitDescription ?: ""
    it.externalDbId = observationVariableDbId

    BrAPIService.getHostUrl(context)?.let { url ->
        it.traitDataSource = url
    }

    scale?.validValues?.minimumValue?.let { min ->
        it.minimum = min.toString()
    }

    scale?.validValues?.maximumValue?.let { max ->
        it.maximum = max.toString()
    }

    val dataType = scale?.dataType
    it.format = if (dataType != null) {
        DataTypes.convertBrAPIDataType(dataType.brapiValue)
    } else {
        "text"
    }

    if (it.format == "multicat") { // convert brapi multicat to field book categorical
        it.format = "categorical"
        it.allowMulticat = true
    }

    scale?.validValues?.categories?.let { categories ->
        if (categories.isNotEmpty()) {
            it.categories = CategoryJsonUtil.buildCategoryList(categories)
            it.details += "\nCategories: ${CategoryJsonUtil.buildCategoryDescriptionString(categories)}"
        }
    }

    it.observationLevelNames = getObservationLevelNames()

    it.visible = true

}

/**
 * The BMS implementation of BrAPI 2.x includes an observationLevelNames array in a variable's
 * additionalInfo. This metadata identifies the level(s) at which a variable is used within a
 * study/field, and is not otherwise retrievable through GET /variables.
 *
 * Returns null when the variable does not carry the metadata.
 */
fun BrAPIObservationVariable.getObservationLevelNames(): List<String>? {

    if (additionalInfo == null || !additionalInfo.has(ADDITIONAL_INFO_OBSERVATION_LEVEL_NAMES)) return null

    return try {

        val array = additionalInfo.getAsJsonArray(ADDITIONAL_INFO_OBSERVATION_LEVEL_NAMES) ?: return null

        val listType = object : TypeToken<List<String?>?>() {}.type

        Gson().fromJson<List<String>>(array, listType)

    } catch (e: Exception) {

        Log.e("PhenotypingMapper", "Failed to parse $ADDITIONAL_INFO_OBSERVATION_LEVEL_NAMES", e)

        null
    }
}

/**
 * BMS specific. Variables that declare observation levels only belong to the study at those
 * levels; variables without the metadata are kept, so servers that don't supply it are unaffected.
 */
fun BrAPIObservationVariable.isAtObservationLevel(levelName: String?): Boolean {

    if (levelName == null) return true

    val levels = getObservationLevelNames() ?: return true

    return levels.any { it.equals(levelName, ignoreCase = true) }
}