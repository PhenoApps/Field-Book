package com.fieldbook.tracker.database.models

import android.content.Context
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.Row
import com.fieldbook.tracker.database.dao.ObservationVariableDao
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.CategoricalTraitLayout
import com.fieldbook.tracker.utilities.CategoryJsonUtil.Companion.decode

data class ObservationModel(val map: Row) {
    val internal_id_observation: Int by map
    val observation_unit_id: String by map
    val observation_variable_db_id: Int by map
    val observation_variable_field_book_format: String? by map
    val observation_variable_name: String? by map
    val observation_variable_alias: String? by map
    var value: String = if ("value" in map) (map["value"] ?: "NA").toString()
    else if ("observation_variable_db_id" in map.keys) ObservationVariableDao.getTraitById(
        observation_variable_db_id.toString()
    )?.defaultValue ?: "NA"
    else "NA"
    val observation_time_stamp: String? by map
    val collector: String? by map
    val geo_coordinates: String? =
        if (map.containsKey("geo_coordinates")) map["geo_coordinates"]?.toString() else null
    val study_id: String = (map["study_id"] ?: -1).toString()
    val last_synced_time: String by map
    val additional_info: String? by map
    var rep: String = if (map.containsKey("rep")) map["rep"]?.toString() ?: "1" else "1"
    var photo_uri: String? =
        if (map.containsKey("photo_uri")) map["photo_uri"]?.toString() else null
    var video_uri: String? =
        if (map.containsKey("video_uri")) map["video_uri"]?.toString() else null
    var audio_uri: String? =
        if (map.containsKey("audio_uri")) map["audio_uri"]?.toString() else null

    //used during file migration when updating photo/audio values to uris
    fun createMap() = mutableMapOf<String, Any?>(
        "internal_id_observation" to internal_id_observation,
        "value" to value,
    )

    companion object {
        fun createInstance(variableDbId: Int, traitName: String) = ObservationModel(
            mapOf(
                "observation_variable_db_id" to variableDbId,
                "observation_variable_name" to traitName
            )
        )
    }

    fun getNonNullAttributes(
        context: Context,
        currentTrait: TraitObject,
        fieldName: String
    ): MutableMap<String, Any> {
        val nonNullAttributes = mutableMapOf<String, Any>()

        // get the "map" property
        val mapProperty = map.toMutableMap()
        try {
            if (mapProperty != null) {
                // remove unwanted fields
                mapProperty.remove("internal_id_observation")
                mapProperty.remove("study_id")
                mapProperty.remove("observation_variable_db_id")

                // add study name to result
                nonNullAttributes[getKeyDisplayName(context, "study_name")] = fieldName

                // Iterate through the attributes
                for ((key, value) in mapProperty) {
                    if (
                        (value != null) &&
                        (value.toString().trim().isNotEmpty())
                    ) {
                        // if the trait is categorical, the "value" field should be decoded
                        val isCategoricalTrait =
                            CategoricalTraitLayout.isTraitCategorical(currentTrait.format)
                        if (isCategoricalTrait && key == "value") {
                            val decodedValue: String = decodeCategorical(value.toString())
                            nonNullAttributes[getKeyDisplayName(context, key)] = decodedValue
                        } else {
                            nonNullAttributes[getKeyDisplayName(context, key)] = value
                        }
                    }
                }
            }
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }

        return nonNullAttributes
    }

    private fun getKeyDisplayName(context: Context, attributeName: String): String =
        when (attributeName) {
            "study_name" -> context.getString(R.string.observation_info_study_name)
            "observation_unit_id" -> context.getString(R.string.observation_info_entry_id)
            "observation_variable_field_book_format" -> context.getString(R.string.observation_info_trait_format)
            "observation_variable_name" -> context.getString(R.string.observation_info_trait_name)
            "observation_variable_alias" -> context.getString(R.string.observation_info_trait_alias)
            "value" -> context.getString(R.string.observation_info_value)
            "observation_time_stamp" -> context.getString(R.string.observation_info_timestamp)
            "collector" -> context.getString(R.string.observation_info_collector)
            "geo_coordinates" -> context.getString(R.string.observations_info_geo_coordinates)
            "last_synced_time" -> context.getString(R.string.observation_info_last_synced_time)
            "additional_info" -> context.getString(R.string.observation_info_additional_info)
            "rep" -> context.getString(R.string.observation_info_rep)
            else -> context.getString(R.string.observation_info_other)
        }

    // function to help code value for categorical and multicategorical trait
    private fun decodeCategorical(value: String): String {
        val categories = decode(value)
        val v = StringBuilder(categories[0].value)
        if (categories.size > 1) {
            for (i in 1 until categories.size) v.append(":").append(categories[i].value)
        }
        return v.toString()
    }

    fun getMultiMediaCount(): Int {

        return setOf(photo_uri, video_uri, audio_uri).count { it != null }

    }
}