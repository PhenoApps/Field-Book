package com.fieldbook.tracker.objects

import com.fieldbook.tracker.database.ObservationVariableAttributeDetailsView
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TraitImportFile(
    val traits: List<TraitJson>
)

@Serializable
data class TraitJson(
    val name: String,
    val alias: String? = null,
    val synonyms: List<String> = emptyList(),
    val format: String,

    @SerialName("defaultValue")
    val defaultValue: String = "",

    val details: String = "",

    @SerialName("isVisible")
    val visible: Boolean = true,

    @SerialName("realPosition")
    val position: Int = 0,

    val attributes: TraitAttributesJson? = null
)

@Serializable
data class TraitAttributesJson(
    @SerialName(ObservationVariableAttributeDetailsView.VALID_VALUES_MIN)
    val minValue: String? = null,

    @SerialName(ObservationVariableAttributeDetailsView.VALID_VALUES_MAX)
    val maxValue: String? = null,

    @SerialName(ObservationVariableAttributeDetailsView.CATEGORY)
    val categories: String? = null,

    @SerialName(ObservationVariableAttributeDetailsView.CLOSE_KEYBOARD_ON_OPEN)
    val closeKeyboard: Boolean? = null,

    @SerialName(ObservationVariableAttributeDetailsView.CROP_IMAGE)
    val cropImage: Boolean? = null,

    @SerialName(ObservationVariableAttributeDetailsView.SAVE_IMAGE)
    val saveImage: Boolean? = null,

    @SerialName(ObservationVariableAttributeDetailsView.USE_DAY_OF_YEAR)
    val useDayOfYear: Boolean? = null,

    @SerialName(ObservationVariableAttributeDetailsView.CATEGORY_DISPLAY_VALUE)
    val categoryDisplayValue: Boolean? = null,

    @SerialName(ObservationVariableAttributeDetailsView.RESOURCE_FILE)
    val resourceFile: String? = null,

    @SerialName(ObservationVariableAttributeDetailsView.DECIMAL_PLACES_REQUIRED)
    val decimalPlaces: String? = null,

    @SerialName(ObservationVariableAttributeDetailsView.MATH_SYMBOLS_ENABLED)
    val mathSymbolsEnabled: Boolean? = null,

    @SerialName(ObservationVariableAttributeDetailsView.ALLOW_MULTICAT)
    val allowMulticat: Boolean? = null,

    @SerialName(ObservationVariableAttributeDetailsView.REPEATED_MEASURES)
    val repeatedMeasures: Boolean? = null,

    @SerialName(ObservationVariableAttributeDetailsView.AUTO_SWITCH_PLOT)
    val autoSwitchPlot: Boolean? = null,

    @SerialName(ObservationVariableAttributeDetailsView.UNIT)
    val unit: String? = null,

    @SerialName(ObservationVariableAttributeDetailsView.INVALID_VALUES)
    val invalidValues: Boolean? = null
)