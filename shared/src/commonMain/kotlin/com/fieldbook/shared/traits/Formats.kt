package com.fieldbook.shared.traits


enum class Formats(
    val databaseName: String,
    val type: Types = Types.SYSTEM,
    val isCamera: Boolean = false
) {

    // SYSTEM formats
    AUDIO("audio"),
    BOOLEAN("boolean"),
    CAMERA("camera", isCamera = true),
    CATEGORICAL("categorical"),
    MULTI_CATEGORICAL("multicat"),
    COUNTER("counter"),
    DATE("date"),
    LOCATION("location"),
    NUMERIC("numeric"),
    PERCENT("percent"),
    TEXT("text"),
    ANGLE("angle"),
    PHOTO("photo", isCamera = true),

    // CUSTOM formats
    DISEASE_RATING("rust rating", Types.CUSTOM),
    GNSS("gnss", Types.CUSTOM),
    BASE_PHOTO("photo", Types.CUSTOM),
    USB_CAMERA("usb camera", Types.CUSTOM, isCamera = true),
    GO_PRO("gopro", Types.CUSTOM, isCamera = true),
    CANON("canon", Types.CUSTOM, isCamera = true),
    LABEL_PRINT("zebra label print", Types.CUSTOM),
    ;

    companion object {
        fun isCameraTrait(format: String) = format in setOf(
            "photo",
            "usb camera",
            "gopro",
            "canon"
        )

        fun isExternalCameraTrait(format: String) = format in setOf("usb camera", "gopro", "canon")

        fun getCameraFormats() = entries.filter { it.isCamera }

        // fun getMainFormats() = entries - listOf(CAMERA, USB_CAMERA, GO_PRO, CANON)

        fun findTrait(format: String) =
            entries.find { it.databaseName == format }?.getTraitFormatDefinition()

    }

    fun getTraitFormatDefinition() = when (this) {
        AUDIO -> AudioFormat()
        BOOLEAN -> BooleanFormat()
        CAMERA -> BasePhotoFormat()
        CATEGORICAL -> CategoricalFormat()
        MULTI_CATEGORICAL -> MultiCategoricalFormat()
        COUNTER -> CounterFormat()
        DATE -> DateFormat()
        LOCATION -> LocationFormat()
        NUMERIC -> NumericFormat()
        PERCENT -> PercentFormat()
        TEXT -> TextFormat()
        ANGLE -> AngleFormat()
        PHOTO -> BasePhotoFormat()
        GNSS -> GnssFormat()
        DISEASE_RATING -> DiseaseRatingFormat()
        LABEL_PRINT -> ZebraLabelPrintFormat()
        USB_CAMERA -> UsbCameraFormat()
        GO_PRO -> GoProFormat()
        CANON -> CanonFormat()
        else -> TextFormat()
    }

    fun getIcon() = getTraitFormatDefinition().iconDrawableResource

}
