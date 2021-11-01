package com.fieldbook.tracker.utilities

import android.location.Location
import com.fieldbook.tracker.database.models.ObservationUnitModel
import com.fieldbook.tracker.traits.GNSSTraitLayout
import com.google.gson.Gson
import java.io.FileWriter
import java.io.IOException
import java.lang.NumberFormatException
import kotlin.math.*

class GeodeticUtils {

    companion object {

        /**
         * GeoNav log is a user preference within Settings/Behavior/GeoNav
         * When enabled, a log file is created each time GeoNav begins.
         * The filename contains parameters for the GeoNav function:
         *      "log_interval_address_theta_systemTime.csv"
         *  Where interval is the update interval for the algorithm, 1, 5, or 10
         *  Address is the mac address of the external device (: replaced with _, also can be Internal_GPS)
         *  Theta is the user preference angle threshold 22.5, 45, 67.5, or 90
         *  System time is the device's time in nano seconds.
         *
         *  Whenever a line is added to the log file it is flushed to the file.
         *  Newlines should be manually added using writeGeoNavLog(log, "\n")
         *
         *  File headers are the following:
         *  UTC, primary, secondary, start latitude, start longitude, end latitude, end longitude,
         *      azimuth, teslas, bearing, distance, thetaCheck, closest
         *  UTC: time given by the GPS, refers to the time the start location was received
         *  primary/secondary: the primary/secondary ids of the current iteration
         *  start: the lat/lng coordinates of the rover
         *  end: the lat/lng coordinates of the target
         *  azimuth: the direction the user is facing in degrees from 0-360 like a compass
         *  teslas: the amount of noise calculated from the device's sensors
         *  bearing: the angle between the start and end coordinates
         *  distance: the distance between the start and end coordinates
         *  thetaCheck: true if end lies within the theta threshold of the start's azimuth
         *  closest: true if this is the closest point
         *      e.g: In the below example 1,1 is the first closest, but is updated by 1,2
         *           therefore, 1,2 is the closest point. 1,5 is within the theta threshold,
         *           but it is further away than 1,2.
         *      191438,1,1,...,true,true
         *      191438,1,2,...,true,true
         *      191438,1,5,...,true,false
         *
         *  The file is populated by (1) device coordinate updates
         *  and (2) for each iteration of the impact zone algorithm.
         *
         *  (1) Device coordinate lines look like:
         *      191437.8,null,null,39.19216,-096.62184,null,null,null,null,null,null,null,null
         *      ... where only the start coordinates and UTC are available
         *
         *  (2) IZ lines look like:
         *  191438,1,1,39.19216,-96.62184,39.19222656,-96.62168695,326.4154145186235,56.7287763993025,null,15.124376606431571,false,false
         *      note: the bearing can be null if the compass setting is disabled
         */
        fun writeGeoNavLog(log: FileWriter?, line: String) {

            log?.let { geonav ->

                try {

                    geonav.append(line)

                    geonav.flush()

                } catch (io: IOException) {

                    io.printStackTrace()

                }
            }
        }

        /**
         * Finds the closest location within a list of locations to the user. The location
         * must also be within the field of view of the user. Shown in the diagram below,
         * the user 'u' creates a cone (roughly) in front defined by their azimuth (from the compass).
         * The cone's angle from the user is defined in the preferences.
         *
         *
         * \           |
         *  \    x    |
         *   \_______|
         *       u
         *
         * @param start: the user's location
         * @param coordinates: the coordinate list to search from
         * @param theta: the field of view angle
         * @param isCompass: whether the compass setting is enabled and if theta threshold should be used in algorithm
         * @return a object representing the returned location and it's distance
         **/
        fun impactZoneSearch(log: FileWriter?,
                             start: Location,
                             coordinates: Array<ObservationUnitModel>,
                             azimuth: Double,
                             theta: Double,
                             teslas: Double,
                             isCompass: Boolean = true): Pair<ObservationUnitModel?, Double> {

            //greedy algorithm to find closest point, first point is set to inf
            var closestDistance = Double.MAX_VALUE
            var closestPoint: ObservationUnitModel? = null

            coordinates.forEach { coordinate ->

                val location = parseGeoCoordinate(coordinate.geo_coordinates)

                if (location != null) {

                    writeGeoNavLog(log, "${start.time},${coordinate.primary_id},${coordinate.secondary_id},${start.latitude},${start.longitude},${location.latitude},${location.longitude},$azimuth,$teslas")

                    if (isCompass) {

                        val bearing = checkThetaThreshold(start, location, azimuth, theta)
                        val distance: Double = distanceHaversine(start, location)

                        if (bearing.first) {

                            if (closestDistance > distance) {

                                writeGeoNavLog(log, ",${bearing.second},$distance,true,true")
                                closestDistance = distance
                                closestPoint = coordinate

                            } else {

                                writeGeoNavLog(log, ",${bearing.second},$distance,true,false")
                            }

                        } else {

                            writeGeoNavLog(log, ",${bearing.second},$distance,false,false")
                        }

                    } else {

                        val distance: Double = distanceHaversine(start, location)

                        if (closestDistance > distance) {

                            writeGeoNavLog(log, ",null,$distance,null,true")

                            closestDistance = distance
                            closestPoint = coordinate

                        } else {

                            writeGeoNavLog(log, ",null,$distance,null,false")

                        }
                    }

                    //write newline to log file after each iteration
                    writeGeoNavLog(log, "\n")
                }
            }

            return closestPoint to closestDistance
        }


        /**
         * Geocoordinates in FB can be stored as GeoJson or semi colon delimited strings.
         * This function parses the string and creates a Location object.
         * If the parsing fails then null is returned.
         */
        fun parseGeoCoordinate(latLng: String?): Location? {

            val coords =
                (if (latLng == null || latLng.isBlank()) ""
                else latLng)

            val location = Location("search")

            //first try parsing as geojson, then try semi colon delimited
            var nonJson = false
            var failed = false

            try {

                val geoJson = Gson().fromJson(coords, GNSSTraitLayout.GeoJSON::class.java)

                location.latitude = geoJson.geometry.coordinates[0].toDouble()

                location.longitude = geoJson.geometry.coordinates[1].toDouble()

            } catch (e: Exception) {  //could be a NPE, number format exception, index out of bounds or json syntax exception,

                failed = true
                nonJson = true

            }

            if (nonJson) { //check semi colon delimited values can be parsed to doubles

                val latLngTokens = coords.split(";")

                if (latLngTokens.size == 2) {

                    try {

                        location.latitude = latLngTokens[0].toDouble()

                        location.longitude = latLngTokens[1].toDouble()

                        failed = false

                    } catch (e: NumberFormatException) {

                        failed = true

                    }
                }
            }

            return if (failed) null else location
        }

        /**
         * Checks whether an object lies within the user's field of view.
         * @param start: the user's location
         * @param end: the object's location
         * @param azimuth: defined by the compass, it's where the user is pointing
         * @param thetaThresh: a preference-defined double value that defines the field of view for the detection
         * @return a pair where first is if the bearing passes the threshold, second is the bearing
         */
        private fun checkThetaThreshold(start: Location, end: Location, azimuth: Double, thetaThresh: Double): Pair<Boolean, Double> {

            //find the direction from user to target.
            val angle = angleFromCoordinate(start.latitude, start.longitude, end.latitude, end.longitude)

            //azimuth and angle are between 0-360
            //if azimuth points towards end, then correctedAngle is close to 0 and should be between the theta thresh
            val correctedAngle = azimuth - angle

            //test if the direction found above is within a threshold from our user's azimuth
            return (correctedAngle in -thetaThresh..thetaThresh) to angle

        }

        /* uses the Haversine method to calculate distance between two GPS coordinates */
        fun distanceHaversine(a: Location, b: Location): Double {
            val lata = a.latitude
            val lnga = a.longitude
            val latb = b.latitude
            val lngb = b.longitude
            val R = 6371.0 //radius of the Earth 6.371 million meters
            val latDst = Math.toRadians(latb - lata)
            val lngDst = Math.toRadians(lngb - lnga)
            val A = (sin(latDst / 2)
                    * sin(latDst / 2) + (cos(Math.toRadians(lata))
                    * cos(Math.toRadians(latb)) * sin(lngDst / 2) * sin(lngDst / 2)))
            val c = 2 * atan2(sqrt(A), sqrt(1 - A))
            //double height = el1 - el2;
            //dst = Math.pow(dst, 2);
            //return Math.sqrt(dst);
            return R * c * 1000.0
        }

        fun geodesicDestination(start: Location, bearing: Double, distance: Double): Location {
            val latRads = Math.toRadians(start.latitude)
            val lngRads = Math.toRadians(start.longitude) //(Degrees * Math.PI) / 180.0;
            //final double bearing = azimuth;//location.getBearing(); //created weighted vector with bearing...?
            val R = 6371.0 //radius of the Earth
            val angDst = distance / R // d/R distance to point B over Earth's radius
            val lat2 = asin(
                sin(latRads) * cos(angDst) +
                        cos(latRads) * sin(angDst) * cos(bearing)
            )
            val lng2 = lngRads + atan2(
                sin(bearing) * sin(angDst) * cos(latRads),
                cos(angDst) - sin(latRads) * sin(lat2)
            )
            val l = Location("end point")
            l.latitude = Math.toDegrees(lat2)
            l.longitude = Math.toDegrees(lng2)
            return l
        }

        //https://www.movable-type.co.uk/scripts/latlong.html
        private fun angleFromCoordinate(
            lat1: Double, long1: Double,
            lat2: Double, long2: Double
        ): Double {
            //get difference between longitudes
            val deltaLong = Math.toRadians(long2-long1)
            //convert latitudes to radians before trig functions
            val lat1Rads = Math.toRadians(lat1)
            val lat2Rads = Math.toRadians(lat2)
            //convert to x/y coordinates
            val y = sin(deltaLong)*cos(lat2Rads)
            val x = cos(lat1Rads)*sin(lat2Rads)-sin(lat1Rads)*cos(lat2Rads)*cos(deltaLong)
            //return degrees
            return (Math.toDegrees(atan2(y, x))+360) % 360
        }

        /**
         * Truncates the coordinate string based on the fix value.
         * https://gis.stackexchange.com/questions/8650/measuring-accuracy-of-latitude-and-longitude
         * basically: normal gps ~4decimal places, differential 5, rtk 8
         */
        fun truncateFixQuality(x: String, fix: String): String = try {

            val tokens = x.split(".")

            val head = tokens[0]
            val tail = tokens[1]

            "$head." + when (fix) {
                "RTK", "manual input mode" -> if (tail.length > 8) tail.substring(0, 8) else tail
                "DGPS", "Float RTK" -> if (tail.length > 5) tail.substring(0, 5) else tail
                "internal" -> if (tail.length > 4) tail.substring(0, 4) else tail
                else -> if (tail.length > 3) tail.substring(0, 3) else tail
            }

        } catch (e: Exception) {

            e.printStackTrace()

            x
        }

        /**
         * Smooth two float arrays using a low pass filter.
         */
        fun lowPassFilter(input: FloatArray, output: FloatArray?): FloatArray =
            output?.mapIndexed { index, fl -> fl + 0.5f * (input[index] - fl) }?.toFloatArray() ?: input
    }
}