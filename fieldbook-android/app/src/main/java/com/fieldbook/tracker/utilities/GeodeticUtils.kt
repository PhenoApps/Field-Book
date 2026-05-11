package com.fieldbook.tracker.utilities

import android.content.SharedPreferences
import android.location.Location
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.models.ObservationUnitModel
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.StringUtil.escape
import com.google.gson.Gson
import math.geom2d.Point2D
import math.geom2d.line.Line2D
import java.io.IOException
import java.io.OutputStreamWriter
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GeodeticUtils {

    companion object {

        /**
         * GeoNav log is a user preference within Settings/GeoNav
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
         *  "start latitude, start longitude, UTC, end latitude, end longitude, azimuth, teslas,
         *      bearing, distance, closest, unique id, primary id, secondary id
         *  UTC: time given by the GPS, refers to the time the start location was received
         *  primary/secondary/unique: the primary/secondary/unique ids of the current iteration
         *  start: the lat/lng coordinates of the rover
         *  end: the lat/lng coordinates of the target
         *  azimuth: the direction the user is facing in degrees from 0-360 like a compass
         *  teslas: the amount of noise calculated from the device's sensors
         *  bearing: the angle between the start and end coordinates
         *  distance: the distance between the start and end coordinates
         *  closest: either 0, 1, or 2.
         *      0: not closest or within the trapezoid
         *      1: updated to closest and within trapezoid
         *      2: the closest point that IZ returns
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
         *
         *  (1) and (2) are a bit outdated in terms of column order (look at the headers above for most up to date)
         *
         *
         *  Update (8/2/23): "fix" has been added as a header to the log file, it is the tenth item. This can be any value GPS, RTK, or RTK Float
         */
        fun writeGeoNavLog(
            preferences: SharedPreferences,
            log: OutputStreamWriter?,
            geoNavLine: GeoNavHelper.GeoNavLine,
            isHeader: Boolean = false
        ) {

            log?.let { geonav ->

                if (!isHeader) {
                    //update the geonav log line with the shared preference parameters
                    //set update interval from the preferences can be 1s, 5s or 10s
                    val interval = preferences.getString(PreferenceKeys.UPDATE_INTERVAL, "1") ?: "1"
                    //find the mac address of the device, if not found then start the internal GPS
                    val address: String =
                        (preferences.getString(PreferenceKeys.PAIRED_DEVICE_ADDRESS, "internal") ?: "")
                            .replace(":".toRegex(), "-")
                            .replace("\\s".toRegex(), "_")
                    //the angle of the IZ algorithm to use, see Geodetic util class for more details
                    val theta: String = preferences.getString(PreferenceKeys.SEARCH_ANGLE, "0") ?: "0"
                    val geoNavMethod: String =
                        preferences.getString(PreferenceKeys.GEONAV_SEARCH_METHOD, "0") ?: "0"
                    val d1: Double =
                        preferences.getString(PreferenceKeys.GEONAV_PARAMETER_D1, "0.001")?.toDouble()
                            ?: 0.001
                    val d2: Double =
                        preferences.getString(PreferenceKeys.GEONAV_PARAMETER_D2, "0.01")?.toDouble()
                            ?: 0.01

                    geoNavLine.address = address
                    geoNavLine.interval = interval
                    geoNavLine.thetaParameter = theta
                    geoNavLine.method = geoNavMethod
                    geoNavLine.d1 = d1.toString()
                    geoNavLine.d2 = d2.toString()

                    //escape the fb id's
                    if (geoNavLine.uniqueId != null) {
                        geoNavLine.uniqueId = "\"${geoNavLine.uniqueId.escape()}\""
                    }

                    if (geoNavLine.primaryId != null) {
                        geoNavLine.primaryId = "\"${geoNavLine.primaryId.escape()}\""
                    }

                    if (geoNavLine.secondaryId != null) {
                        geoNavLine.secondaryId = "\"${geoNavLine.secondaryId.escape()}\""
                    }
                }

                try {

                    geonav.append(geoNavLine.toString() + "\n")

                    geonav.flush()

                } catch (io: IOException) { }
            }
        }

        private const val NOT_CLOSEST = 0
        private const val CLOSEST_UPDATE = 1
        private const val CLOSEST_FINAL = 2

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
        fun impactZoneSearch(
            limitedLog: OutputStreamWriter?,
            fullLog: OutputStreamWriter?,
            preferences: SharedPreferences,
            currentLoggingMode: String,
            start: Location,
            coordinates: Array<ObservationUnitModel>,
            azimuth: Double?,
            theta: Double,
            teslas: Double,
            geoNavMethod: String,
            d1: Double,
            d2: Double,
            distanceThreshold: Double
        ): Pair<ObservationUnitModel?, Double> {

            //greedy algorithm to find closest point, first point is set to inf
            var closestDistance = Double.MAX_VALUE
            var closestPoint: ObservationUnitModel? = null

            val izLogArray = arrayListOf<GeoNavHelper.GeoNavLine>()

            coordinates.forEach { coordinate ->

                val location = parseGeoCoordinate(coordinate.geo_coordinates)

                if (location != null) {

                    val distance: Double = distanceHaversine(start, location)

                    val bearing: Double = angleFromCoordinate(start.latitude, start.longitude, location.latitude, location.longitude)

                    val fix = start.extras?.getString("fix") ?: "invalid"

                    val loggedString = GeoNavHelper.GeoNavLine(
                        utc = start.time.toString(),
                        uniqueId = coordinate.observation_unit_db_id, primaryId = coordinate.primary_id, secondaryId = coordinate.secondary_id,
                        startLat = start.latitude.toString(), startLng = start.longitude.toString(),
                        endLat = location.latitude.toString(), endLng = location.longitude.toString(),
                        azimuth = azimuth.toString(), teslas = teslas.toString(), bearing = bearing.toString(),
                        distance = distance.toString(), closest = NOT_CLOSEST.toString(), fix = fix)

                    if (geoNavMethod == "0") { //default distance based method

                        if (closestDistance > distance) {

                            //update the closest value to updated
                            loggedString.closest = CLOSEST_UPDATE.toString()
                            closestDistance = distance
                            closestPoint = coordinate

                        }

                    } else { //trapezoidal method

                        azimuth?.let { nonNullAzimuth ->

                            if (isInZone(start, location, nonNullAzimuth, theta, d1, d2)) {

                                if (closestDistance > distance) {

                                    loggedString.closest = CLOSEST_UPDATE.toString()
                                    closestDistance = distance
                                    closestPoint = coordinate

                                }
                            }
                        }
                    }

                    //add line to array, will updated the final closest after a full run
                    izLogArray.add(loggedString)
                }
            }

            //after a full run of IZ, update the last CLOSEST_UPDATE to CLOSEST_FINAL
            izLogArray.findLast { it.closest == CLOSEST_UPDATE.toString() }?.closest = CLOSEST_FINAL.toString()

            if (closestDistance/1000 < distanceThreshold) {

                // limited mode
                if (currentLoggingMode == GeoNavHelper.GeoNavLoggingMode.LIMITED.value || currentLoggingMode == GeoNavHelper.GeoNavLoggingMode.BOTH.value) {
                    //print only the closest plant to the log
                    izLogArray.forEach {
                        if (it.closest == CLOSEST_FINAL.toString()) writeGeoNavLog(
                            preferences,
                            limitedLog,
                            it
                        )
                    }
                }

                // full mode
                if (currentLoggingMode == GeoNavHelper.GeoNavLoggingMode.FULL.value || currentLoggingMode == GeoNavHelper.GeoNavLoggingMode.BOTH.value) {
                    //print the entire array to log
                    izLogArray.forEach { writeGeoNavLog(preferences, fullLog, it) }
                }
            }

            return closestPoint to closestDistance
        }

        private fun Location.toPoint2D() = Point2D(latitude, longitude)

        /**
         * Builds a trapezoid from the parameters and checks if location parameter is within the trapezoid.
         *  w _______x
         *  \        |
         *   \      | d2
         *   u\____|v
         *      d1
         */
        private fun isInZone(start: Location, location: Location,
                             azimuth: Double, theta: Double,
                             distance1: Double, distance2: Double): Boolean {

            //make a point s' that is a very small distance in front of start
            val s1 = geodesicDestination(start, azimuth, 0.0001)

            //create points u and v which are on the same latitude as s' but are distance1 apart
            //creating this line improves finding nearby points vs using a cone which has a small
            //area of interest near its vertex
            val u = geodesicDestination(s1, (azimuth - 90.0) % 360.0, distance1/2.0)
            val v = geodesicDestination(s1, (azimuth + 90.0) % 360.0, distance1/2.0)

            //points w and x define the larger base of the trapezoid
            val w = geodesicDestination(u, (azimuth - theta/2.0) % 360.0, distance2)
            val x = geodesicDestination(v, (azimuth + theta/2.0) % 360.0, distance2)

            //now form the line segments uv, uw, wx, and vx
            //these are the lines of the trapezoid to check intersections with
            val uv = Line2D(u.toPoint2D(), v.toPoint2D())
            val uw = Line2D(u.toPoint2D(), w.toPoint2D())
            val wx = Line2D(w.toPoint2D(), x.toPoint2D())
            val vx = Line2D(v.toPoint2D(), x.toPoint2D())

            //now the line of interest is start -> location which we will call sl
            val sl = Line2D(start.toPoint2D(), location.toPoint2D())

            //now check:
            //1. sl should always intersect uv, which would make it inside the trapezoid
            //2. sl should not intersect uw, wx, or xv which would be outside the trapezoid
            //intersections are handled by Java2D library for line segments
            return isIntersecting(sl, uv)
                    && !isIntersecting(sl, uw)
                    && !isIntersecting(sl, wx)
                    && !isIntersecting(sl, vx)
        }

        /**
         * Geocoordinates in FB can be stored as GeoJson or semi colon delimited strings.
         * This function parses the string and creates a Location object.
         * If the parsing fails then null is returned.
         */
        fun parseGeoCoordinate(latLng: String?): Location? {

            val coords =
                (if (latLng.isNullOrBlank()) ""
                else latLng)

            val location = Location("search")

            //first try parsing as geojson, then try semi colon delimited
            var nonJson = false
            var failed = false

            try {

                val geoJson = Gson().fromJson(coords, GeoJsonUtil.GeoJSON::class.java)

                location.latitude = geoJson.geometry.coordinates[1]

                location.longitude = geoJson.geometry.coordinates[0]

                geoJson.properties?.get("fix")?.let { fix ->

                    location.extras?.putString("fix", fix)

                }

            } catch (e: Exception) {  //could be a NPE, number format exception, index out of bounds or json syntax exception,

                failed = true
                nonJson = true

            }

            if (nonJson) { //check semi colon delimited values can be parsed to doubles

                val latLngTokens = coords.split(";")

                if (latLngTokens.size >= 2) {

                    try {

                        location.latitude = latLngTokens[1].toDouble()

                        location.longitude = latLngTokens[0].toDouble()

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

        fun isIntersecting(u: Line2D, v: Line2D): Boolean = Line2D.intersects(u, v)

        fun geodesicDestination(start: Location, bearing: Double, distance: Double): Location {
            val latRads = Math.toRadians(start.latitude)
            val lngRads = Math.toRadians(start.longitude) //(Degrees * Math.PI) / 180.0;
            //final double bearing = azimuth;//location.getBearing(); //created weighted vector with bearing...?
            val bearingRads = Math.toRadians(bearing)
            val R = 6371.0 //radius of the Earth
            val angDst = distance / R // d/R distance to point B over Earth's radius
            val lat2 = asin(
                sin(latRads) * cos(angDst) +
                        cos(latRads) * sin(angDst) * cos(bearingRads)
            )
            val lng2 = lngRads + atan2(
                sin(bearingRads) * sin(angDst) * cos(latRads),
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
//        fun truncateFixQuality(x: String, fix: String): String = try {
//
//            val tokens = x.split(".")
//
//            val head = tokens[0]
//            val tail = tokens[1]
//
//            "$head." + when (fix) {
//                "RTK", "manual input mode" -> if (tail.length > 8) tail.substring(0, 8) else tail
//                "DGPS", "Float RTK" -> if (tail.length > 5) tail.substring(0, 5) else tail
//                "internal" -> if (tail.length > 4) tail.substring(0, 4) else tail
//                else -> if (tail.length > 3) tail.substring(0, 3) else tail
//            }
//
//        } catch (e: Exception) {
//
//            e.printStackTrace()
//
//            x
//        }

        /**
         * As of issue #477 v5.3, standardize truncation to 7 digits
         */
        fun truncateFixQuality(x: String): String = try {

            val tokens = x.split(".")
            val head = tokens[0]
            val tail = tokens[1]

            val n = tail.length

            "$head." + if (n > 7) {

                tail.substring(0, 7)

            } else tail

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