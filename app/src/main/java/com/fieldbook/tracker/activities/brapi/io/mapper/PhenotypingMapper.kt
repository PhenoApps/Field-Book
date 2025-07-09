package com.fieldbook.tracker.activities.brapi.io.mapper

import android.content.Context
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2.ADDITIONAL_INFO_OBSERVATION_LEVEL_NAMES
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.brapi.v2.model.pheno.BrAPIObservationVariable

/**
 * File for extension functions to convert BrAPI objects to FieldBook objects
 */

fun BrAPIObservationVariable.toTraitObject(context: Context) = TraitObject().also {

    val synonym = synonyms?.firstOrNull()

    it.defaultValue = defaultValue ?: ""
    it.name = synonym ?: observationVariableName
    it.details = trait.traitDescription
    it.externalDbId = observationVariableDbId

    BrAPIService.getHostUrl(context)?.let { url ->
        it.traitDataSource = url
    }

    scale?.validValues?.min?.let { min ->
        it.minimum = min.toString()
    }

    scale?.validValues?.max?.let { max ->
        it.maximum = max.toString()
    }

    val dataType = scale?.dataType
    it.format = if (dataType != null) {
        DataTypes.convertBrAPIDataType(dataType.brapiValue)
    } else {
        "text"
    }

    scale?.validValues?.categories?.let { categories ->
        if (categories.isNotEmpty()) {
            it.categories = CategoryJsonUtil.buildCategoryList(categories)
            it.details += "\nCategories: ${CategoryJsonUtil.buildCategoryDescriptionString(categories)}"
        }
    }

    if (additionalInfo != null && additionalInfo.has(ADDITIONAL_INFO_OBSERVATION_LEVEL_NAMES)) {

        additionalInfo.getAsJsonArray(ADDITIONAL_INFO_OBSERVATION_LEVEL_NAMES)?.let { array ->

            val listType = object : TypeToken<List<String?>?>() {}.type

            it.observationLevelNames =
                Gson().fromJson<List<String>>(
                    array,
                    listType
                )

        }
    }

    it.visible = true

}