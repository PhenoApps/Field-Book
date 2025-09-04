package com.fieldbook.tracker.objects

import android.database.Cursor
import com.fieldbook.tracker.database.Migrator.ObservationVariable
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
    var externalDbId: String? = null
    var traitDataSource: String = ""
    var additionalInfo: String? = null

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

    var saveImage: Boolean
        get() = attributeValues.getBoolean(TraitAttributes.SAVE_IMAGE)
        set(value) = attributeValues.setValue(TraitAttributes.SAVE_IMAGE, value.toString())

    fun loadAttributeAndValues() {
        attributeValues.traitId = id
        attributeValues.load()
    }

    fun saveAttributeValues() {
        attributeValues.traitId = id
        attributeValues.save()
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

    fun loadFromCursor(cursor: Cursor) {

        val nameIndex = cursor.getColumnIndex("observation_variable_name")
        val formatIndex = cursor.getColumnIndex("observation_variable_field_book_format")
        val defaultValueIndex = cursor.getColumnIndex("default_value")
        val detailsIndex = cursor.getColumnIndex("observation_variable_details")
        val idIndex = cursor.getColumnIndex(ObservationVariable.PK)
        val externalDbIdIndex = cursor.getColumnIndex("external_db_id")
        val realPositionIndex = cursor.getColumnIndex("position")
        val visibleIndex = cursor.getColumnIndex("visible")
        val additionalInfoIndex = cursor.getColumnIndex("additional_info")
        val traitDataSourceIndex = cursor.getColumnIndex("trait_data_source")

        if (nameIndex == -1 || formatIndex == -1 || defaultValueIndex == -1 ||
            detailsIndex == -1 || idIndex == -1 || externalDbIdIndex == -1 ||
            realPositionIndex == -1 || visibleIndex == -1 || additionalInfoIndex == -1 ||
            traitDataSourceIndex == -1) {
            return
        }

        name = cursor.getString(nameIndex) ?: ""
        format = cursor.getString(formatIndex) ?: ""
        defaultValue = cursor.getString(defaultValueIndex) ?: ""
        details = cursor.getString(detailsIndex) ?: ""
        id = cursor.getString(idIndex) ?: ""
        externalDbId = cursor.getString(externalDbIdIndex) ?: ""
        realPosition = cursor.getInt(realPositionIndex)
        visible = cursor.getString(visibleIndex) == "true"
        additionalInfo = cursor.getString(additionalInfoIndex) ?: ""
        traitDataSource = cursor.getString(traitDataSourceIndex) ?: ""

        // loadAttributeAndValues()
    }
}