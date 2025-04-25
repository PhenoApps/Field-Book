package com.fieldbook.tracker.database

object SpectralProtocolTable {
    const val TABLE_NAME = "spectral_dim_protocol"
    const val ID = "id"
    const val EXTERNAL_ID = "external_id"
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val WAVELENGTH_START = "wave_start"
    const val WAVELENGTH_END = "wave_end"
    const val WAVELENGTH_STEP = "wave_step"
}

object SpectralDeviceTable {
    const val TABLE_NAME = "spectral_dim_device"
    const val ID = "id"
    const val ADDRESS = "address"
    const val NAME = "name"
}

object SpectralUriTable {
    const val TABLE_NAME = "spectral_dim_uri"
    const val ID = "id"
    const val URI = "uri"
}

object SpectralFactTable {
    const val TABLE_NAME = "facts_spectral"
    const val ID = "id"
    const val PROTOCOL_ID = "protocol_id"
    const val URI_ID = "uri_id"
    const val DEVICE_ID = "device_id"
    const val OBSERVATION_ID = "observation_id"
    const val DATA = "data"
    const val COLOR = "color"
    const val COMMENT = "comment"
    const val CREATED_AT = "created_at"
}