package com.fieldbook.tracker.location.gnss

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.ParseException

/**
 * NMEA 0183 parser.
 * Usage:
 * val parser = NmeaParser()
 * parser.parse(raw?.trim() ?: "") where raw is the nmea string
 *
 * After parsing, the parser object fields are populated with the NMEA properties.
 */
class NmeaParser {

    var gsv = ArrayList<GSV>(4)
    var numGsv = 4
    var gsvSatsInView = 0

    var mPrevSentence: String = String()
    var satellites: String = String()
    var utc: String = String()
    var latitude: String = String()
    var longitude: String = String()
    var fix: String = String()
    var hdop: String = String()
    var altitude: String = String()
    var meanSeaLevel: String = String()
    var speed: String = String()
    var accuracy: String = String()
    var bearing: String = String()

    var pdop: String = String()
    var vdop: String = String()
    private var satArray: List<String> = listOf()
    private var receiverMode: String = String()
    private var fixMode = String()
    private var rmcStatus = String()
    private var course = String()
    private var modeIndicator = String()

    private fun verifyChecksum(nmea: String): Boolean {

        if (nmea.contains("*")) {
            val split = nmea.split("*") //$NMEA,,,,,,,*33 <- getting the checksum
            val checksum = split[1].trim().toByte(16) //erase newline and line feed
            val msg = if ("$" in split[0]) split[0].substring(2)
            else split[0].substring(0)
            val start = split[0].toCharArray()[1]
            var sum: Int = start.toInt()
            msg.forEach {
                sum = sum xor(it.toInt()) //exclusive or of each character
            }
            //Log.d("NMEA", "${Integer.toHexString(sum)}")
            if (checksum.toInt() == sum) return true
        }
        return false
    }

    /**
     * The main function of this class that takes a raw nmea string.
     * Parses NMEA 0183 sentences, but mainly uses the GGA talkers.
     * The function first calculates the checksum to verify integrity, then attempts to
     * delegate parsing to different functions based on talker id.
     */
    fun parse(nmea: String) {

        if (verifyChecksum(nmea)) {

            //split sentence from checksum
            nmea.split("*")[1]
            var nmeaType = String()
            val sentence = nmea.split(",")

            //check that the sentence is not empty and extract the talker id
            if (sentence.isNotEmpty()) {
                nmeaType = sentence[0]
                //Log.d("TALKER", nmeaType)
                //All messages should have  $TI where TI is the talker identifier
                //followed by a 3-4(?) character nmea message type
                if (nmeaType.length == 6) {
                    //val talkerId = nmeaType.substring(0, 2)
                    nmeaType = nmeaType.substring(3)
                }
            }

            //use the nmea type to lead the parsing
            when {
                "GGA" in nmeaType || "ZDA" in nmeaType -> {
                    mPrevSentence = "GGA"
                    try {
                        //utc = format.parse(sentence[1]).toString().split(" ")[3] ?: ""
                        utc = sentence[1]//.toString().split(" ")[3]
                    } catch (e: ParseException) {
                        e.printStackTrace()
                    }
                    latitude = parseLatitude(sentence[3], sentence[2])
                    longitude = parseLongitude(sentence[5], sentence[4])
                    parseFixQuality(sentence[6])
                    satellites = sentence[7]
                    hdop = sentence[8]
                    altitude = sentence[9] + sentence[10]
                    meanSeaLevel = sentence[11] + sentence[12]
                }
                "RMC" in nmeaType -> { //recommended minimum specific gnss data
                    if (sentence.size == 14) {

                        mPrevSentence = "RMC"

                        /*try {
                            utc = format.parse(sentence[9] + sentence[1])?.toString() ?: ""
                        } catch (e: ParseException) {

                        }*/
                        rmcStatus = sentence[2]
                        latitude = parseLatitude(sentence[4], sentence[3])
                        longitude = parseLongitude(sentence[6], sentence[5])
                        speed = sentence[7]
                        bearing = sentence[8]
                        modeIndicator = sentence[9]
                    }
                }
                "GSA" in nmeaType -> {
                    //GPS receiver operating mode
                    mPrevSentence = "GSA"
                    if (sentence.size == 19) {
                        receiverMode = sentence[1]
                        fixMode = sentence[2]
                        satArray = sentence.subList(3, 14)
                        pdop = sentence[15]
                        hdop = sentence[16]
                        vdop = sentence[17]
                    }
                }
                //TODO parse GSV messages
                "GSV" in nmeaType -> {
                    if (sentence.size >= 13) {
                        mPrevSentence = "GSV"
                        if (numGsv != sentence[1].toInt()) {
                            numGsv = sentence[1].toInt()
                            gsv = ArrayList()
                        }

                        //var index = sentence[2].toInt()
                        gsvSatsInView = sentence[3].toInt()

                        for (i in 4 until 12 step 4) {
                            val thisGSV = GSV(
                                    //id of the satellite in view
                                    prn = sentence[i],
                                    //elevation in degrees
                                    elevationDeg = sentence[i + 1],
                                    //azimuth in degrees
                                    azimuthDeg = sentence[i + 2],
                                    //signal strength, 0 to 99, 0 means not being tracked
                                    snr = sentence[i + 3]
                            )

                            if (thisGSV.prn.isNotBlank() && thisGSV.snr.isNotBlank()) {
                                if (!gsv.any { thisGSV.prn == it.prn })
                                    gsv.add(thisGSV)
                            }
                        }
                    }
                }
                "GLL" in nmeaType -> {
                    mPrevSentence = "GLL"
                    latitude = if (sentence[2] == "S") "-" + sentence[1] else sentence[1]
                    longitude = if (sentence[4] == "W") "-" + sentence[3] else sentence[3]
                    try {
                        //utc = format.parse(sentence[5])?.toString() ?: ""
                        utc = sentence[5]
                    } catch (e: ParseException) {

                    }
                }
                "NVTG" in nmeaType || "NGST" in nmeaType -> {
                    //println("Todo")
                    //TODO
                }
            }
        }
    }

    data class GSV(val prn: String, val elevationDeg: String, val azimuthDeg: String, val snr: String)

    // Convert latitude from DMS to decimal format
    private fun parseLatitude(hemisphere: String, latString: String): String {
        if (latString.isNotBlank()) {
            val minutes = BigDecimal(latString.substring(2)).divide(BigDecimal(60.0),8, RoundingMode.HALF_UP)
            val lat = latString.substring(0, 2) + minutes.toString().substring(1)
            return when (hemisphere.contains("S")) {
                true -> "-$lat"
                else -> lat
            }
        }
        return ""
    }

    // Convert longitude from DMS to decimal format
    private fun parseLongitude(hemisphere: String, longString: String): String {
        if (longString.isNotBlank()) {
            val minutes = BigDecimal(longString.substring(3)).divide(BigDecimal(60.0), 8, RoundingMode.HALF_UP)
            val lng = longString.substring(0, 3) + minutes.toString().substring(1)
            return when (hemisphere.contains("W")) {
                true -> "-$lng"
                else -> lng
            }
        }
        return ""
    }

    private fun parseFixQuality(f: String) {
        fix = when (f) {
            "1" -> "GPS"
            "2" -> "DGPS"
            "3" -> "PPS"
            "4" -> "RTK"
            "5" -> "Float RTK"
            "6" -> "estimated"
            "7" -> "manual input mode"
            "8" -> "simulation"
            else -> "invalid"
        }
    }

    /**
     * For now just display GPS, RTK, Float RTK, or invalid
     */
    fun getSimpleFix() = when (fix) {
        "GPS", "DGPS", "PPS" -> "GPS"
        "RTK" -> "RTK"
        "invalid" -> "invalid"
        else -> "Float RTK"
    }

    fun compareFix(fix: String, precisionThresh: String): Boolean {

        if (precisionThresh == "Any") return true

        if (precisionThresh == "Float RTK" && fix == "Float RTK") return true

        if (precisionThresh == "RTK" && (fix == "RTK" || fix == "Float RTK")) return true

        if (precisionThresh == "GPS" && (fix == "GPS" || fix == "RTK" || fix == "Float RTK")) return true

        return false
    }
}
