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

    val ALL = listOf(MIN_VALUE, MAX_VALUE, CATEGORIES, CLOSE_KEYBOARD, CROP_IMAGE, SAVE_IMAGE)

    fun byKey(key: String): AttributeDefinition? = ALL.find { it.key == key }
}

enum class ValueType {
    STRING, BOOLEAN,
}

data class AttributeDefinition(
    val key: String,
    val valueType: ValueType,
    val defaultValue: String = ""
)