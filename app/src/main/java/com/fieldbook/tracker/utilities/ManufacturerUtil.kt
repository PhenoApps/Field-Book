package com.fieldbook.tracker.utilities

import android.os.Build

class ManufacturerUtil {

    companion object {

        /**
         * The BOOX Palma Manufacturer is ONYX
         */
        fun isEInk() = Build.MANUFACTURER in setOf(
            "ONYX",
        )
    }
}