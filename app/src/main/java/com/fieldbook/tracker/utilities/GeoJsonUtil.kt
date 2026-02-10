package com.fieldbook.tracker.utilities

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.fieldbook.tracker.database.DataHelper
import com.google.gson.Gson
import org.json.JSONObject

class GeoJsonUtil {

    //based on RFC 7956
    //{
    //  "type": "Feature",
    //  "geometry": {
    //    "type": "Point",
    //    "coordinates": [125.6, 10.1]  <!-- NOTE this is [lng, lat]
    //  },
    //  "properties": {
    //    "name": "Dinagat Islands"
    //  }
    //}
    data class Geometry(val type: String = "Point", val coordinates: Array<Double>) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Geometry

            if (type != other.type) return false
            if (!coordinates.contentEquals(other.coordinates)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + coordinates.contentHashCode()
            return result
        }
    }

    data class GeoJSON(val type: String = "Feature", val geometry: Geometry, val properties: Map<String, String>? = null) {
        fun toJson() = JSONObject(mapOf("type" to this.type,
            "geometry" to mapOf("type" to this.geometry.type,
                "coordinates" to this.geometry.coordinates,
                "properties" to properties
            )))

        fun toCoordinateString(delim: String) = "${geometry.coordinates[0]}$delim ${geometry.coordinates[1]}"
    }

    companion object {

        private const val TAG = "GeoJSON"

        fun decode(json: String) = Gson().fromJson(json, GeoJSON::class.java)

        @Throws(Exception::class)
        fun fixGeoCoordinates(helper: DataHelper, db: SQLiteDatabase) {

            Log.d(TAG, "Starting to fix observation unit coordinates.")

            try {

                fixObservationUnitCoordinates(helper, db)

                Log.d(TAG, "Fixing observation unit coordinates success.")

            } catch (e: Exception) {

                e.printStackTrace()

                Log.e(TAG, "Fixing observation unit coordinates failed.")

                throw e
            }

            Log.d(TAG, "Starting to fix observation value coordinates.")

            try {

                fixObservationValues(helper, db)

                Log.d(TAG, "Fixing observation value coordinates success.")

            } catch (e: Exception) {

                e.printStackTrace()

                Log.e(TAG, "Fixing observation value coordinates failed.")

                throw e
            }
        }

        private fun fixObservationUnitCoordinates(helper: DataHelper, db: SQLiteDatabase) {

            val updatedObservationUnits = helper.queryObservationUnitsWithGeoCoordinates(db).map { model ->

                model.apply {

                    geo_coordinates?.let { coords ->

                        geo_coordinates = swap(coords)

                    }
                }
            }

            //update the database study models
            helper.updateObservationUnitModels(db, updatedObservationUnits)
        }

        private fun fixObservationValues(helper: DataHelper, db: SQLiteDatabase) {

            val updatedObservations = helper.queryLocationObservations(db).map { model ->

                model.apply {

                    value = swap(value)
                }
            }

            //update the database observation value
            helper.updateObservationModels(db, updatedObservations)
        }
        private fun DataHelper.queryObservationUnitsWithGeoCoordinates(db: SQLiteDatabase) =
            getAllObservationUnits(db).filter {
                it.geo_coordinates !in setOf(
                    null,
                    "null"
                ) && it.geo_coordinates?.isNotBlank() == true
            }

        private fun DataHelper.queryLocationObservations(db: SQLiteDatabase) =
            getAllObservations(db).filter {
                it.observation_variable_field_book_format in setOf(
                    "location",
                    "gnss"
                )
            }

        private fun swap(latlng: String): String {

            var lnglat: String = ""

            //first check if the latlng is a GeoJSON encoding
            if (JsonUtil.isJsonValid(latlng)) {

                //decode to the GeoJSON object
                val geoJson = decode(latlng)

                //flip the coordinate indices
                geoJson.geometry.coordinates.apply {
                    val old = this
                    this[0] = old[1]
                    this[1] = old[0]
                }

                lnglat = geoJson.toString()

            } else {

                val tokens = latlng.split(";")

                if (tokens.size in 2..3) {

                    //check if fix is included
                    lnglat = when (tokens.size) {

                        2 -> "${tokens[1]}; ${tokens[0]}"

                        else -> "${tokens[1]}; ${tokens[0]}; ${tokens[2]}"
                    }
                }
            }

            return lnglat
        }
    }
}