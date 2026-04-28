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

object GroupsTable {
    const val TABLE_NAME = "groups"
    const val ID = "id"
    const val GROUP_NAME = "group_name"
    const val IS_EXPANDED = "is_expanded"
    const val GROUP_TYPE = "group_type"

    const val FK = "group_id"

    object Type {
        const val STUDY = "study"

        fun getTableName(groupType: String): String {
            return when (groupType) {
                STUDY -> "studies"
                else -> throw IllegalArgumentException("Unknown group type: $groupType")
            }
        }
    }
}

object ObservationVariableAttributeDetailsView {
    const val VIEW_NAME = "observation_variable_attribute_details_view"
    const val INTERNAL_ID = "internal_id_observation_variable"
    const val VARIABLE_NAME = "observation_variable_name"
    const val VALID_VALUES_MAX = "validValuesMax"
    const val VALID_VALUES_MIN = "validValuesMin"
    const val CATEGORY = "category"
    const val CLOSE_KEYBOARD_ON_OPEN = "closeKeyboardOnOpen"
    const val CROP_IMAGE = "cropImage"
    const val SAVE_IMAGE = "saveImage"
}