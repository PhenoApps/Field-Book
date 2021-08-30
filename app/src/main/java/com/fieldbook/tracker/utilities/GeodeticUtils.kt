package com.fieldbook.tracker.utilities

import android.location.Location
import android.util.Log
import com.fieldbook.tracker.database.models.ObservationModel
import kotlin.math.*

class GeodeticUtils {

    companion object {

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
         * @return a object representing the returned location and it's distance
         **/
        fun impactZoneSearch(start: Location,
                             coordinates: List<ObservationModel>,
                             azimuth: Double,
                             theta: Double): Pair<ObservationModel?, Double> {

            //Log.d("GeoNav", "User bearing: $azimuth")
            //greedy algorithm to find closest point, first point is set to inf
            var closestDistance = Double.MAX_VALUE
            var closestPoint: ObservationModel? = null

            coordinates.forEach { coordinate ->
                val location = Location("search")
                val latLngString =
                    (if (coordinate.geo_coordinates == null
                        || coordinate.geo_coordinates?.isBlank() == true
                    ) coordinate.value
                    else coordinate.geo_coordinates) ?: ""
                val latLngTokens = latLngString.split(";")
                if (latLngTokens.size == 2) {
                    location.latitude = latLngTokens[0].toDouble()
                    location.longitude = latLngTokens[1].toDouble()
                    //Log.d("GeoNav", "${coordinate.observation_unit_id}")
                    if (checkThetaThreshold(start, location, azimuth, theta)) {
                        val distance: Double = distanceHaversine(start, location)
                        if (closestDistance > distance) {
                            closestDistance = distance
                            closestPoint = coordinate
                        }
                    }
                }
            }

            return closestPoint to closestDistance
        }

        /**
         * Checks whether an object lies within the user's field of view.
         * @param start: the user's location
         * @param end: the object's location
         * @param azimuth: defined by the compass, it's where the user is pointing
         * @param thetaThresh: a preference-defined double value that defines the field of view for the detection
         * @return true/false depending if this object is within the field of view
         */
        private fun checkThetaThreshold(start: Location, end: Location, azimuth: Double, thetaThresh: Double): Boolean {

            //find the direction from user to target.
            val userToMarkerBearing = start.bearingTo(end).toDouble()

            //Log.d("GeoNav", userToMarkerBearing.toString())

            //test if the direction found above is within a threshold from our user's azimuth
            return userToMarkerBearing >= azimuth - thetaThresh && userToMarkerBearing <= azimuth + thetaThresh

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

        private fun angleFromCoordinate(
            lat1: Double, long1: Double, lat2: Double,
            long2: Double
        ): Double {
            val dLon = long2 - long1
            val y = Math.sin(dLon) * Math.cos(lat2)
            val x = Math.cos(lat1) * Math.sin(lat2) - (Math.sin(lat1)
                    * Math.cos(lat2) * Math.cos(dLon))
            var brng = Math.atan2(y, x)
            brng = Math.toDegrees(brng)
            brng = (brng + 360) % 360
            brng = 360 - brng
            return brng
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
                else -> if (tail.length > 4) tail.substring(0, 4) else tail
            }

        } catch (e: Exception) {

            e.printStackTrace()

            x
        }
    }
}