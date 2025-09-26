package com.fieldbook.tracker.database.models

import com.fieldbook.tracker.database.ObservationVariableAttributeDetailsView

/**
 * Define the attributes here for ObservationVariables
 */
object TraitAttributes {
    val MIN_VALUE = AttributeDefinition(
        key = ObservationVariableAttributeDetailsView.VALID_VALUES_MIN,
        valueType = ValueType.STRING
    )

    val MAX_VALUE = AttributeDefinition(
        key = ObservationVariableAttributeDetailsView.VALID_VALUES_MAX,
        valueType = ValueType.STRING
    )

    val CATEGORIES = AttributeDefinition(
        key = ObservationVariableAttributeDetailsView.CATEGORY,
        valueType = ValueType.STRING
    )

    val CLOSE_KEYBOARD = AttributeDefinition(
        key = ObservationVariableAttributeDetailsView.CLOSE_KEYBOARD_ON_OPEN,
        valueType = ValueType.BOOLEAN,
        defaultValue = "false"
    )

    val CROP_IMAGE = AttributeDefinition(
        key = ObservationVariableAttributeDetailsView.CROP_IMAGE,
        valueType = ValueType.BOOLEAN,
        defaultValue = "false"
    )

    val SAVE_IMAGE = AttributeDefinition(
        key = ObservationVariableAttributeDetailsView.SAVE_IMAGE,
        valueType = ValueType.BOOLEAN,
        defaultValue = "true"
    )

    val USE_DAY_OF_YEAR = AttributeDefinition(
        key = ObservationVariableAttributeDetailsView.USE_DAY_OF_YEAR,
        valueType = ValueType.BOOLEAN,
        defaultValue = "false"
    )

    val CATEGORY_DISPLAY_VALUE = AttributeDefinition(
        key = ObservationVariableAttributeDetailsView.CATEGORY_DISPLAY_VALUE,
        valueType = ValueType.BOOLEAN,
        defaultValue = "false"
    )

    val RESOURCE_FILE = AttributeDefinition(
        key = ObservationVariableAttributeDetailsView.RESOURCE_FILE,
        valueType = ValueType.STRING,
    )

    val VARIABLE_SYNONYMS = AttributeDefinition(
        key = ObservationVariableAttributeDetailsView.VARIABLE_SYNONYMS,
        valueType = ValueType.STRING,
    )

    val DECIMAL_PLACES_REQUIRED = AttributeDefinition(
        key = ObservationVariableAttributeDetailsView.DECIMAL_PLACES_REQUIRED,
        valueType = ValueType.STRING,
        defaultValue = "-1"
    )

    val MATH_SYMBOLS_ENABLED = AttributeDefinition(
        key = ObservationVariableAttributeDetailsView.MATH_SYMBOLS_ENABLED,
        valueType = ValueType.BOOLEAN,
        defaultValue = "true"
    )

    val ALLOW_MULTICAT = AttributeDefinition(
        key = ObservationVariableAttributeDetailsView.ALLOW_MULTICAT,
        valueType = ValueType.BOOLEAN,
        defaultValue = "false"
    )

    val REPEATED_MEASURES = AttributeDefinition(
        key = ObservationVariableAttributeDetailsView.REPEATED_MEASURES,
        valueType = ValueType.BOOLEAN,
        defaultValue = "false"
    )

    val ALL = listOf(MIN_VALUE, MAX_VALUE, CATEGORIES, CLOSE_KEYBOARD, CROP_IMAGE, SAVE_IMAGE,
        USE_DAY_OF_YEAR, CATEGORY_DISPLAY_VALUE, RESOURCE_FILE, VARIABLE_SYNONYMS,
        DECIMAL_PLACES_REQUIRED, MATH_SYMBOLS_ENABLED, ALLOW_MULTICAT, REPEATED_MEASURES
    )

    fun byKey(key: String): AttributeDefinition? = ALL.find { it.key == key }
}

enum class ValueType {
    STRING, BOOLEAN
}

data class AttributeDefinition(
    val key: String,
    val valueType: ValueType,
    val defaultValue: String = ""
)