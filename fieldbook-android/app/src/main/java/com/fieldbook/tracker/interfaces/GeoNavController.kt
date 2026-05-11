package com.fieldbook.tracker.interfaces

import com.fieldbook.tracker.adapters.AttributeAdapter

interface GeoNavController {
    fun queryForLabelValue(
        plotId: String,
        attribute: AttributeAdapter.AttributeModel
    ): String

    fun getGeoNavPopupSpinnerItems(): List<AttributeAdapter.AttributeModel>
    fun logNmeaMessage(nmea: String)
}