package com.fieldbook.tracker.traits.formats

import android.content.Context
import com.fieldbook.tracker.offbeat.traits.formats.contracts.AngleFormat

enum class Formats(val type: Types = Types.SYSTEM, val isCamera: Boolean = false) {

    //SYSTEM formats
    AUDIO, BOOLEAN, CAMERA(isCamera = true), CATEGORICAL, MULTI_CATEGORICAL, COUNTER, DATE, LOCATION, NUMERIC, PERCENT, TEXT, ANGLE, BASE_SPECTRAL,

    //CUSTOM formats
    DISEASE_RATING(Types.CUSTOM), GNSS(Types.CUSTOM), STOP_WATCH(Types.CUSTOM),
    BASE_PHOTO(Types.CUSTOM), USB_CAMERA(Types.CUSTOM, isCamera = true), GO_PRO(Types.CUSTOM, isCamera = true), CANON(Types.CUSTOM, isCamera = true),
    NIX(Types.CUSTOM), GREEN_SEEKER(Types.CUSTOM), SCALE(Types.CUSTOM),
    LABEL_PRINT(Types.CUSTOM), BRAPI(Types.CUSTOM);

    companion object {

        fun isSpectralFormat(format: String) = format in setOf("inno_spectra", "nix")

        fun isCameraTrait(format: String) = format in setOf("photo", "usb camera", "gopro", "canon")

        fun isExternalCameraTrait(format: String) = format in setOf("usb camera", "gopro", "canon")

        fun getSpectralFormats() = entries.filter { it in setOf(NIX, GREEN_SEEKER) }

        fun getCameraFormats() = entries.filter { it.isCamera }

        fun getMainFormats() = entries - getCameraFormats().toSet() - getSpectralFormats().toSet()

        fun findTrait(format: String) = entries.find { it.getDatabaseName() == format }?.getTraitFormatDefinition()

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