package com.fieldbook.tracker.database.models

import android.location.Location
import com.fieldbook.tracker.database.Row
import com.fieldbook.tracker.traits.GNSSTraitLayout
import com.google.gson.Gson
import java.lang.NumberFormatException

/**
 * Plot-level table structure.
 * */
data class ObservationUnitModel(val map: Row) {

    val internal_id_observation_unit: Int by map //comp. pk 1
    val study_id: Int by map  //fk to studies table
    val observation_unit_db_id: String by map //unique id
    val primary_id: String by map
    val secondary_id: String by map
    val geo_coordinates: String? by map //blob?
    val additionalInfo: String? by map //blob, can be replaced with value/attr query?
    val germplasmDbId: String? by map //brapId ?
    val germplasmName: String? by map
    val observationLevel: String? by map
    val position_coordinate_x: String? by map //x-axis value e.g row=1
    val positionCoordinateXType: String? by map //x-axis label?
    val position_coordinate_y: String? by map //y-axis value e.g col=2
    val positionCoordinateYType: String? by map

    /**
     * Converts the geo_coordinate field to a Location object or null
     */
    fun getLocation(): Location? {

        val location = Location("model location")

        if (geo_coordinates == null) return null

        var failed = false
        var nonJson = false

        try {

            val geoJson = Gson().fromJson(geo_coordinates, GNSSTraitLayout.GeoJSON::class.java)

            location.latitude = geoJson.geometry.coordinates[0].toDouble()

            location.longitude = geoJson.geometry.coordinates[1].toDouble()

        } catch (e: Exception) {  //could be a NPE, number format exception, index out of bounds or json syntax exception,

            failed = true
            nonJson = true

        }

        if (nonJson) { //check semi colon delimited values can be parsed to doubles

            val latLngTokens = geo_coordinates?.split(";")

            if (latLngTokens?.size == 2) {

                try {

                        location.latitude = latLngTokens[0].toDouble()

                        location.longitude = latLngTokens[1].toDouble()

                        failed = false

                } catch (e: NumberFormatException) {

                        failed = true

                }
            }
        }

        return if (!failed) location
        else null

    }
}