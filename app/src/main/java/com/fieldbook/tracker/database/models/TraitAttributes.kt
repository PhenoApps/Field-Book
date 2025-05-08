package com.fieldbook.tracker.database.models

/**
 * Define the attributes here for ObservationVariables
 */
object TraitAttributes {
    val MIN_VALUE = AttributeDefinition(
        key = "validValuesMin",
        valueType = ValueType.STRING
    )

    val MAX_VALUE = AttributeDefinition(
        key = "validValuesMax",
        valueType = ValueType.STRING
    )

    val CATEGORIES = AttributeDefinition(
        key = "category",
        valueType = ValueType.STRING
    )

    val CLOSE_KEYBOARD = AttributeDefinition(
        key = "closeKeyboardOnOpen",
        valueType = ValueType.BOOLEAN,
        defaultValue = "false"
    )

    val CROP_IMAGE = AttributeDefinition(
        key = "cropImage",
        valueType = ValueType.BOOLEAN,
        defaultValue = "false"
    )

    val ALLOW_DUPLICATES = AttributeDefinition(
        key = "allowDuplicates",
        valueType = ValueType.BOOLEAN,
        defaultValue = "false"
    )


    val ALL = listOf(MIN_VALUE, MAX_VALUE, CATEGORIES, CLOSE_KEYBOARD)

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