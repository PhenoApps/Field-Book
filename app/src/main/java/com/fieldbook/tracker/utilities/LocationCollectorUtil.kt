package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import com.fieldbook.tracker.database.dao.ObservationDao
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.location.GPSTracker
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.GeneralPreferencesFragment
import java.util.*
import kotlin.NoSuchElementException
import kotlin.math.exp

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
                                     expId: String, obsUnit: String,
                                     internalGps: Location?, externalGps: Location?): String {

            //default to no location
            var location = String() //prefs.getString(GeneralKeys.LOCATION, "") ?: "" <-- old way was to use preference location

            try {
                val recent: String? = getRecentLocation(context, internalGps, externalGps)

                //if obs mode, save the most recent location, prioritize external gps
                val locationCollectionMode: Int = prefs.getString(GeneralKeys.GENERAL_LOCATION_COLLECTION, "0")?.toInt() ?: 0
                if (locationCollectionMode == GeneralPreferencesFragment.LOCATION_COLLECTION_OBS) {
                    if (recent != null) {
                        location = recent
                    }
                } else if (locationCollectionMode == GeneralPreferencesFragment.LOCATION_COLLECTION_OBS_UNIT) {

                    //if obs unit mode, search all observations within the current plot
                    //if a location already exists, use that location for this observation, otherwise use the most recent location
                    location = ObservationDao.getAll(expId, obsUnit).getLocation() ?: recent ?: String()

                } else if (locationCollectionMode == GeneralPreferencesFragment.LOCATION_COLLECTION_STUDY) {

                    //similar to above but check if an observation has been saved for a field/study
                    location = ObservationDao.getAll(expId).getLocation() ?: recent ?: String()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return location
        }

        //simple search to find a non null geo coordinate column
        private fun Array<ObservationModel>.getLocation(): String? = try {
            this.firstNotNullOf { it.geo_coordinates }
        } catch (e: NoSuchElementException) {
            null
        }
    }
}