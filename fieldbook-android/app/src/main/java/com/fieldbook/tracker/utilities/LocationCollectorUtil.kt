package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.location.GPSTracker
import com.fieldbook.tracker.preferences.LocationPreferencesFragment
import com.fieldbook.tracker.preferences.PreferenceKeys
import java.util.*

class LocationCollectorUtil {

    companion object {

        /**
         * Prioritizes external gps and returns the most recent location.
         * @return lat;lng string or null if no recent location has been taken
         */
        private fun getRecentLocation(context: Context, internalGps: Location?, externalGps: Location?): String? {
            val joiner = StringJoiner(";")
            if (externalGps != null) {
                joiner.add(externalGps.latitude.toString())
                joiner.add(externalGps.longitude.toString())
                return joiner.toString()
            } else if (internalGps != null) {
                joiner.add(internalGps.latitude.toString())
                joiner.add(internalGps.longitude.toString())
                return joiner.toString()
            } else {
                val tracker = GPSTracker(context)
                if (tracker.canGetLocation()) {
                    joiner.add(tracker.latitude.toString())
                    joiner.add(tracker.longitude.toString())
                    return joiner.toString()
                }
            }
            return null
        }

        fun getLocationByCollectMode(context: Context, prefs: SharedPreferences,
                                     studyId: String, obsUnit: String,
                                     internalGps: Location?, externalGps: Location?,
                                     database: DataHelper
        ): String {

            //default to no location
            var location = String() //prefs.getString(GeneralKeys.LOCATION, "") ?: "" <-- old way was to use preference location

            try {
                val recent: String? = getRecentLocation(context, internalGps, externalGps)

                //if obs mode, save the most recent location, prioritize external gps
                val locationCollectionMode: Int = prefs.getString(PreferenceKeys.GENERAL_LOCATION_COLLECTION, "0")?.toInt() ?: 0
                if (locationCollectionMode == LocationPreferencesFragment.LOCATION_COLLECTION_OBS) {
                    if (recent != null) {
                        location = recent
                    }
                } else if (locationCollectionMode == LocationPreferencesFragment.LOCATION_COLLECTION_OBS_UNIT) {

                    //if obs unit mode, search all observations within the current plot
                    //if a location already exists, use that location for this observation, otherwise use the most recent location
                    location = database.getAllObservations(studyId, obsUnit).getLocation() ?: recent ?: String()

                } else if (locationCollectionMode == LocationPreferencesFragment.LOCATION_COLLECTION_STUDY) {

                    //similar to above but check if an observation has been saved for a field/study
                    location = database.getAllObservations(studyId).getLocation() ?: recent ?: String()
                }

                // Format location based on preference
                val coordinateFormat: Int = prefs.getString(PreferenceKeys.COORDINATE_FORMAT, "0")?.toInt() ?: 0
                location = formatLocation(location, coordinateFormat)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return location
        }

        private fun formatLocation(location: String, format: Int): String {
            return if (format == 1) {
                // Convert to GeoJSON format
                val coords = location.split(";")
                if (coords.size == 2) {
                    """{"type":"Point","coordinates":[${coords[1]},${coords[0]}]}"""
                } else {
                    location
                }
            } else {
                // Default to ISO 6709 format (no change needed)
                location
            }
        }

        //simple search to find a non null geo coordinate column
        private fun Array<ObservationModel>.getLocation(): String? = try {
            this.firstNotNullOf { it.geo_coordinates }
        } catch (e: NoSuchElementException) {
            null
        }
    }
}