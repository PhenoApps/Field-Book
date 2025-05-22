package com.fieldbook.tracker.objects

import com.fieldbook.tracker.database.dao.TraitAttributeValuesHelper
import com.fieldbook.tracker.database.models.TraitAttributes
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import java.util.*

/**
 * Simple wrapper class for trait data
 */
class TraitObject {
    var name: String = ""
    var format: String = ""
    var defaultValue: String = ""
    var details: String = ""
    var realPosition: Int = 0
    var id: String = ""
    var visible: Boolean = true
    var externalDbId: String = ""
    var traitDataSource: String = ""
    var additionalInfo: String = ""

    var observationLevelNames: List<String>? = null

    // do not bind this map tightly with a traitId
    // traitId is null until the trait object inserted into the database
    // but binding the UI inputs to attributes (minimum, maximum, etc) happens in
    // ParameterScrollView's merge function BEFORE insertion into the db is done
    private val attributeValues = TraitAttributeValuesHelper()

    var minimum: String
        get() = attributeValues.getString(TraitAttributes.MIN_VALUE)
        set(value) = attributeValues.setValue(TraitAttributes.MIN_VALUE, value)

    var maximum: String
        get() = attributeValues.getString(TraitAttributes.MAX_VALUE)
        set(value) = attributeValues.setValue(TraitAttributes.MAX_VALUE, value)

    var categories: String
        get() = attributeValues.getString(TraitAttributes.CATEGORIES)
        set(value) = attributeValues.setValue(TraitAttributes.CATEGORIES, value)

    var closeKeyboardOnOpen: Boolean
        get() = attributeValues.getBoolean(TraitAttributes.CLOSE_KEYBOARD)
        set(value) = attributeValues.setValue(TraitAttributes.CLOSE_KEYBOARD, value.toString())

    var cropImage: Boolean
        get() = attributeValues.getBoolean(TraitAttributes.CROP_IMAGE)
        set(value) = attributeValues.setValue(TraitAttributes.CROP_IMAGE, value.toString())


    fun loadAttributeAndValues() {
        attributeValues.traitId = id
        attributeValues.load()
    }

    fun saveAttributeValues() {
        attributeValues.traitId = id
        attributeValues.save()
    }

    fun isValidCategoricalValue(inputCategory: String): Boolean {
        // Check if it's the new JSON format
        try {
            val c = CategoryJsonUtil.decode(inputCategory)

            if (c.isNotEmpty()) {
                // Get the value from the single-sized array
                val labelVal = c[0]

                // Check that this pair is a valid label/val pair in the category
                return CategoryJsonUtil.contains(c, labelVal)
            }
        } catch (e: Exception) {
            e.printStackTrace() // If it fails to decode, assume it's an old string
        }

        return false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val that = other as TraitObject

        return realPosition == that.realPosition &&
                name == that.name &&
                format == that.format &&
                defaultValue == that.defaultValue &&
                minimum == that.minimum &&
                maximum == that.maximum &&
                details == that.details &&
                categories == that.categories &&
                id == that.id &&
                visible == that.visible &&
                externalDbId == that.externalDbId &&
                traitDataSource == that.traitDataSource &&
                additionalInfo == that.additionalInfo &&
                observationLevelNames == that.observationLevelNames &&
                closeKeyboardOnOpen == that.closeKeyboardOnOpen &&
                cropImage == that.cropImage
    }

    override fun hashCode(): Int {
        return Objects.hash(
            name, format, defaultValue, minimum, maximum, details, categories,
            realPosition, id, visible, externalDbId, traitDataSource,
            additionalInfo, observationLevelNames, closeKeyboardOnOpen, cropImage
        )
    }

    fun clone(): TraitObject {
        val t = TraitObject()
        t.name = this.name
        t.format = this.format
        t.defaultValue = this.defaultValue
        t.minimum = this.minimum
        t.maximum = this.maximum
        t.details = this.details
        t.categories = this.categories
        t.realPosition = this.realPosition
        t.id = this.id
        t.visible = this.visible
        t.externalDbId = this.externalDbId
        t.traitDataSource = this.traitDataSource
        t.additionalInfo = this.additionalInfo
        t.observationLevelNames = this.observationLevelNames
        t.closeKeyboardOnOpen = this.closeKeyboardOnOpen
        t.cropImage = this.cropImage

        return t
    }
}