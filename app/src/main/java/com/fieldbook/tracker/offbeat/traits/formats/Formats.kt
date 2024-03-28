package com.fieldbook.tracker.offbeat.traits.formats

import android.content.Context
import com.fieldbook.tracker.offbeat.traits.formats.contracts.AudioFormat
import com.fieldbook.tracker.offbeat.traits.formats.contracts.BooleanFormat
import com.fieldbook.tracker.offbeat.traits.formats.contracts.BrapiFormat
import com.fieldbook.tracker.offbeat.traits.formats.contracts.CategoricalFormat
import com.fieldbook.tracker.offbeat.traits.formats.contracts.CounterFormat
import com.fieldbook.tracker.offbeat.traits.formats.contracts.DateFormat
import com.fieldbook.tracker.offbeat.traits.formats.contracts.DiseaseRatingFormat
import com.fieldbook.tracker.offbeat.traits.formats.contracts.GnssFormat
import com.fieldbook.tracker.offbeat.traits.formats.contracts.GoProFormat
import com.fieldbook.tracker.offbeat.traits.formats.contracts.LocationFormat
import com.fieldbook.tracker.offbeat.traits.formats.contracts.MultiCategoricalFormat
import com.fieldbook.tracker.offbeat.traits.formats.contracts.NumericFormat
import com.fieldbook.tracker.offbeat.traits.formats.contracts.PercentFormat
import com.fieldbook.tracker.offbeat.traits.formats.contracts.PhotoFormat
import com.fieldbook.tracker.offbeat.traits.formats.contracts.TextFormat
import com.fieldbook.tracker.offbeat.traits.formats.contracts.UsbCameraFormat
import com.fieldbook.tracker.offbeat.traits.formats.contracts.ZebraLabelPrintFormat

enum class Formats(val type: Types = Types.SYSTEM, val isCamera: Boolean = false) {

    //SYSTEM formats
    AUDIO, BOOLEAN, CAMERA(isCamera = true), CATEGORICAL, MULTI_CATEGORICAL, COUNTER, DATE, LOCATION, NUMERIC, PERCENT, TEXT,

    //CUSTOM formats
    DISEASE_RATING(Types.CUSTOM), GNSS(Types.CUSTOM), USB_CAMERA(Types.CUSTOM, isCamera = true), GO_PRO(Types.CUSTOM, isCamera = true),
    LABEL_PRINT(Types.CUSTOM), BRAPI(Types.CUSTOM);

    companion object {
        fun isCameraTrait(format: String) = format in setOf("photo", "usb camera", "gopro", "canon")

        fun getCameraFormats() = entries.filter { it.isCamera }

        fun getMainFormats() = entries - listOf(USB_CAMERA, GO_PRO)
    }

    fun getTraitFormatDefinition() = when (this) {
        AUDIO -> AudioFormat()
        BOOLEAN -> BooleanFormat()
        CAMERA -> PhotoFormat()
        USB_CAMERA -> UsbCameraFormat()
        GO_PRO -> GoProFormat()
        CATEGORICAL -> CategoricalFormat()
        MULTI_CATEGORICAL -> MultiCategoricalFormat()
        COUNTER -> CounterFormat()
        DATE -> DateFormat()
        LOCATION -> LocationFormat()
        GNSS -> GnssFormat()
        NUMERIC -> NumericFormat()
        PERCENT -> PercentFormat()
        DISEASE_RATING -> DiseaseRatingFormat()
        LABEL_PRINT -> ZebraLabelPrintFormat()
        BRAPI -> BrapiFormat()
        else -> TextFormat()
    }

    /**
     * This is the trait format DISPLAY name, which tends to be uppercase: Categorical, Numerical
     */
    fun getName(ctx: Context): String {

        val formatDefinition = getTraitFormatDefinition()
        val stringResource = ctx.getString(formatDefinition.nameStringResourceId)

        return formatDefinition.stringNameAux?.let { it(ctx) } ?: stringResource
    }

    /**
     *  FB classically enforces lower cased names in the database, when querying make sure to
     *  use this function
     */
    fun getDatabaseName(ctx: Context) = getName(ctx).lowercase()

    fun getIcon() = getTraitFormatDefinition().iconDrawableResourceId

}