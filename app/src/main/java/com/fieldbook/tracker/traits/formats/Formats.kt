package com.fieldbook.tracker.traits.formats

import android.content.Context

enum class Formats {

    //SYSTEM formats
    ANGLE, AUDIO, BOOLEAN,
    BASE_PHOTO, CAMERA, VIDEO, USB_CAMERA, GO_PRO, CANON,
    CATEGORICAL, COUNTER, DATE,
    HARDWARE, SCALE, LABEL_PRINT,
    LOCATION, GNSS, NUMERIC, PERCENT, STOP_WATCH,
    BASE_SPECTRAL, NIX, INNO_SPECTRA_SENSOR, GREEN_SEEKER,
    TEXT,
    CUSTOM, DISEASE_RATING;

    companion object {

        fun isSpectralFormat(format: String) = format in setOf("inno_spectra", "nix")

        fun isCameraTrait(format: String) = format in getCameraFormats().map { it.getDatabaseName() }

        fun isExternalCameraTrait(format: String) = format in setOf("usb camera", "gopro", "canon")

        fun getHardwareFormats() = entries.filter { it in setOf(SCALE, LABEL_PRINT) }
        
        fun getCustomFormats() = entries.filter { it in setOf(DISEASE_RATING) }
        
        fun getSpectralFormats() = entries.filter { it in setOf(NIX, GREEN_SEEKER, INNO_SPECTRA_SENSOR) }

        fun getCameraFormats() = entries.filter { it in setOf(CAMERA, USB_CAMERA, GO_PRO, CANON, VIDEO) }

        fun getMainFormats() = entries - getCameraFormats().toSet() - getSpectralFormats().toSet() - getHardwareFormats().toSet() - getCustomFormats().toSet()

        fun getBaseFormats() = setOf(BASE_PHOTO, BASE_SPECTRAL, HARDWARE, CUSTOM)

        fun findTrait(format: String) = entries.find { it.getDatabaseName() == format }?.getTraitFormatDefinition()

        fun isGeoFormat(format: String) = format in setOf(LOCATION.getDatabaseName(), GNSS.getDatabaseName())
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
        COUNTER -> CounterFormat()
        DATE -> DateFormat()
        LOCATION -> LocationFormat()
        ANGLE -> AngleFormat()
        GNSS -> GnssFormat()
        NUMERIC -> NumericFormat()
        PERCENT -> PercentFormat()
        DISEASE_RATING -> DiseaseRatingFormat()
        LABEL_PRINT -> ZebraLabelPrintFormat()
        BASE_SPECTRAL -> BaseSpectralFormat()
        NIX -> NixSensorFormat()
        STOP_WATCH -> StopWatchFormat()
        GREEN_SEEKER -> GreenSeekerFormat()
        SCALE -> ScaleFormat()
        VIDEO -> VideoFormat()
        INNO_SPECTRA_SENSOR -> InnoSpectraSensorFormat()
        CUSTOM -> CustomFormat()
        HARDWARE -> HardwareFormat()
        else -> TextFormat()
    }

    fun getReadableName(ctx: Context): String {
        val formatDefinition = getTraitFormatDefinition()
        val stringResource = ctx.getString(formatDefinition.nameStringResourceId)

        return formatDefinition.stringNameAux?.let { it(ctx) } ?: stringResource
    }

    /**
     * This is the trait format DISPLAY name, which tends to be uppercase: Categorical, Numerical
     */
    fun getName(ctx: Context): String {

        val formatDefinition = getTraitFormatDefinition()

        return ctx.getString(formatDefinition.nameStringResourceId)
    }

    /**
     *  FB classically enforces lower cased names in the database, when querying make sure to
     *  use this function
     */
    fun getDatabaseName() = getTraitFormatDefinition().databaseName

    fun getIcon() = getTraitFormatDefinition().iconDrawableResourceId

}