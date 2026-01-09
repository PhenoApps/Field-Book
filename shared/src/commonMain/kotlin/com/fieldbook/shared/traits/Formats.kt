package com.fieldbook.shared.traits


enum class Formats(
    val databaseName: String,
    val type: Types = Types.SYSTEM,
    val isCamera: Boolean = false
) {

    // SYSTEM formats
    AUDIO("audio"),
    BOOLEAN("boolean"),
    CAMERA("photo", isCamera = true),
    CATEGORICAL("categorical"),
    MULTI_CATEGORICAL("multicat"),
    COUNTER("counter"),
    DATE("date"),
    LOCATION("location"),
    NUMERIC("numeric"),
    PERCENT("percent"),
    TEXT("text"),
    ANGLE("angle"),

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
        fun findTrait(format: String) =
            entries.find { it.databaseName == format }?.getTraitFormatDefinition()

        fun supportedFormats () = listOf(
            AUDIO,
            BOOLEAN,
            CATEGORICAL,
            MULTI_CATEGORICAL,
            COUNTER,
            DATE,
            NUMERIC,
            PERCENT,
            TEXT,
            ANGLE,
            CAMERA,
        )
    }

    fun getTraitFormatDefinition() = when (this) {
        AUDIO -> AudioFormat()
        BOOLEAN -> BooleanFormat()
        BASE_PHOTO -> BasePhotoFormat()
        CAMERA -> PhotoFormat()
        USB_CAMERA -> UsbCameraFormat()
        GO_PRO -> GoProFormat()
        CANON -> CanonFormat()
        CATEGORICAL -> CategoricalFormat()
        MULTI_CATEGORICAL -> MultiCategoricalFormat()
        COUNTER -> CounterFormat()
        DATE -> DateFormat()
        LOCATION -> LocationFormat()
        NUMERIC -> NumericFormat()
        PERCENT -> PercentFormat()
        TEXT -> TextFormat()
        ANGLE -> AngleFormat()
        GNSS -> GnssFormat()
        DISEASE_RATING -> DiseaseRatingFormat()
        LABEL_PRINT -> ZebraLabelPrintFormat()
        else -> TextFormat()
    }

    fun getIcon() = getTraitFormatDefinition().iconDrawableResource

}
