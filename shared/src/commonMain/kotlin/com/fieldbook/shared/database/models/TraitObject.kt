package com.fieldbook.shared.database.models

data class TraitObject(
    var name: String = "",
    var format: String = "",
    var defaultValue: String? = null,
    var minimum: String? = null,
    var maximum: String? = null,
    var details: String? = null,
    var categories: String? = null,
    var realPosition: Int = 0,
    var id: String? = null,
    var visible: Boolean? = null,
    var externalDbId: String? = null,
    var traitDataSource: String? = null,
    var additionalInfo: String? = null,
    var closeKeyboardOnOpen: Boolean = false,
    var cropImage: Boolean = false,
    var observationLevelNames: List<String>? = null
) {
    /**
     * Checks if the inputCategory is a valid categorical value.
     * This is a stub for multiplatform; implement platform-specific logic as needed.
     */
    fun isValidCategoricalValue(inputCategory: String): Boolean {
        // TODO: Implement actual category validation logic for multiplatform
        // For now, always return false
        return false
    }

    fun clone(): TraitObject = this.copy()
}
