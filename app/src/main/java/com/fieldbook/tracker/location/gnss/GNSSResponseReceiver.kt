package com.fieldbook.tracker.location.gnss

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

//Receives GPS updates from a bluetooth device or the phone GPS
abstract class GNSSResponseReceiver : BroadcastReceiver() {

    private val parser = NmeaParser()

    companion object {

        const val MESSAGE_OUTPUT_CODE = 333
        const val MESSAGE_OUTPUT_FAIL = 332

        const val MESSAGE_STRING_EXTRA_KEY = "org.phenoapps.tracker.fieldbook.gnss.GNSS_OUTPUT"

        const val ACTION_BROADCAST_GNSS_ROVER = "org.phenoapps.tracker.fieldbook.gnss.ACTION_BROADCAST_GNSS_ROVER"

        const val ACTION_BROADCAST_GNSS_TRAIT = "org.phenoapps.tracker.fieldbook.gnss.ACTION_BROADCAST_GNSS_TRAIT"
    }

    abstract fun onGNSSParsed(parser: NmeaParser)

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.hasExtra(MESSAGE_STRING_EXTRA_KEY)) {

            val raw = intent.getStringExtra(MESSAGE_STRING_EXTRA_KEY)

            try {

                parser.parse(raw?.trim() ?: "")

                onGNSSParsed(parser)

            } catch (e: Exception) {

                e.printStackTrace()

//                mFirebaseAnalytics.logEvent("PARSERERROR", Bundle().apply {
//                    putString("ERROR", e.stackTrace.toString())
//                })
            }
        }
    }
}