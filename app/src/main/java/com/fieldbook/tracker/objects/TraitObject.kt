package com.fieldbook.tracker.objects

import android.database.Cursor
import com.fieldbook.tracker.database.Migrator.ObservationVariable
import com.fieldbook.tracker.database.dao.TraitAttributeValuesHelper
import com.fieldbook.tracker.database.models.AttributeDefinition
import com.fieldbook.tracker.database.models.TraitAttributes
import com.fieldbook.tracker.utilities.SynonymsUtil.deserializeSynonyms
import com.fieldbook.tracker.utilities.SynonymsUtil.serializeSynonyms
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.*

/**
 * Simple wrapper class for trait data
 */
class TraitObject {
    var name: String = ""
    var alias: String = ""
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

    private var _synonyms: String = ""
    var synonyms: List<String>
        get() = deserializeSynonyms(_synonyms)
        set(value) { _synonyms = serializeSynonyms(value) }

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

    var useDayOfYear: Boolean
        get() = attributeValues.getBoolean(TraitAttributes.USE_DAY_OF_YEAR)
        set(value) = attributeValues.setValue(TraitAttributes.USE_DAY_OF_YEAR, value.toString())

    var categoryDisplayValue: Boolean
        get() = attributeValues.getBoolean(TraitAttributes.CATEGORY_DISPLAY_VALUE)
        set(value) = attributeValues.setValue(TraitAttributes.CATEGORY_DISPLAY_VALUE, value.toString())

    var resourceFile: String
        get() = attributeValues.getString(TraitAttributes.RESOURCE_FILE)
        set(value) = attributeValues.setValue(TraitAttributes.RESOURCE_FILE, value)

    var maxDecimalPlaces: String
        get() = attributeValues.getString(TraitAttributes.DECIMAL_PLACES_REQUIRED)
        set(value) = attributeValues.setValue(TraitAttributes.DECIMAL_PLACES_REQUIRED, value)

    var mathSymbolsEnabled: Boolean
        get() = attributeValues.getBoolean(TraitAttributes.MATH_SYMBOLS_ENABLED)
        set(value) = attributeValues.setValue(TraitAttributes.MATH_SYMBOLS_ENABLED, value.toString())

    var allowMulticat: Boolean
        get() = attributeValues.getBoolean(TraitAttributes.ALLOW_MULTICAT)
        set(value) = attributeValues.setValue(TraitAttributes.ALLOW_MULTICAT, value.toString())

    var repeatedMeasures: Boolean
        get() = attributeValues.getBoolean(TraitAttributes.REPEATED_MEASURES)
        set(value) = attributeValues.setValue(TraitAttributes.REPEATED_MEASURES, value.toString())

    var autoSwitchPlot: Boolean
        get() = attributeValues.getBoolean(TraitAttributes.AUTO_SWITCH_PLOT)
        set(value) = attributeValues.setValue(TraitAttributes.AUTO_SWITCH_PLOT, value.toString())

    var unit: String
        get() = attributeValues.getString(TraitAttributes.UNIT)
        set(value) = attributeValues.setValue(TraitAttributes.UNIT, value)

    var invalidValues: Boolean
        get() = attributeValues.getBoolean(TraitAttributes.INVALID_VALUES)
        set(value) = attributeValues.setValue(TraitAttributes.INVALID_VALUES, value.toString())

    var multiMediaPhoto: Boolean
        get() = attributeValues.getBoolean(TraitAttributes.MULTI_MEDIA_PHOTO)
        set(value) = attributeValues.setValue(TraitAttributes.MULTI_MEDIA_PHOTO, value.toString())

    var multiMediaVideo: Boolean
        get() = attributeValues.getBoolean(TraitAttributes.MULTI_MEDIA_VIDEO)
        set(value) = attributeValues.setValue(TraitAttributes.MULTI_MEDIA_VIDEO, value.toString())

    var multiMediaAudio: Boolean
        get() = attributeValues.getBoolean(TraitAttributes.MULTI_MEDIA_AUDIO)
        set(value) = attributeValues.setValue(TraitAttributes.MULTI_MEDIA_AUDIO, value.toString())

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
                alias == that.alias &&
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
                cropImage == that.cropImage &&
                saveImage == that.saveImage &&
                useDayOfYear == that.useDayOfYear &&
                categoryDisplayValue == that.categoryDisplayValue &&
                resourceFile == that.resourceFile &&
                synonyms == that.synonyms &&
                maxDecimalPlaces == that.maxDecimalPlaces &&
                mathSymbolsEnabled == that.mathSymbolsEnabled &&
                allowMulticat == that.allowMulticat &&
                repeatedMeasures == that.repeatedMeasures &&
                autoSwitchPlot == that.autoSwitchPlot &&
                unit == that.unit &&
                invalidValues == that.invalidValues &&
                multiMediaAudio == that.multiMediaAudio &&
                multiMediaPhoto == that.multiMediaPhoto &&
                multiMediaVideo == that.multiMediaVideo
    }

    override fun hashCode(): Int {
        return Objects.hash(
            name, alias, format, defaultValue, minimum, maximum, details, categories,
            realPosition, id, visible, externalDbId, traitDataSource,
            additionalInfo, observationLevelNames, closeKeyboardOnOpen, cropImage,
            saveImage, useDayOfYear, categoryDisplayValue, resourceFile, synonyms,
            maxDecimalPlaces, mathSymbolsEnabled, allowMulticat, repeatedMeasures,
            autoSwitchPlot, unit, invalidValues, multiMediaAudio, multiMediaPhoto, multiMediaVideo
        )
    }

    fun clone(): TraitObject {
        val t = TraitObject()
        t.name = this.name
        t.alias = this.alias
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
        t.saveImage = this.saveImage
        t.useDayOfYear = this.useDayOfYear
        t.categoryDisplayValue = this.categoryDisplayValue
        t.resourceFile = this.resourceFile
        t.synonyms = this.synonyms
        t.maxDecimalPlaces = this.maxDecimalPlaces
        t.mathSymbolsEnabled = this.mathSymbolsEnabled
        t.allowMulticat = this.allowMulticat
        t.repeatedMeasures = this.repeatedMeasures
        t.autoSwitchPlot = this.autoSwitchPlot
        t.unit = this.unit
        t.invalidValues = this.invalidValues
        t.multiMediaAudio = this.multiMediaAudio
        t.multiMediaPhoto = this.multiMediaPhoto
        t.multiMediaVideo = this.multiMediaVideo

        return t
    }

    fun loadFromCursor(cursor: Cursor) {

        val nameIndex = cursor.getColumnIndex("observation_variable_name")
        val aliasIndex = cursor.getColumnIndex("observation_variable_alias")
        val formatIndex = cursor.getColumnIndex("observation_variable_field_book_format")
        val defaultValueIndex = cursor.getColumnIndex("default_value")
        val detailsIndex = cursor.getColumnIndex("observation_variable_details")
        val idIndex = cursor.getColumnIndex(ObservationVariable.PK)
        val externalDbIdIndex = cursor.getColumnIndex("external_db_id")
        val realPositionIndex = cursor.getColumnIndex("position")
        val visibleIndex = cursor.getColumnIndex("visible")
        val additionalInfoIndex = cursor.getColumnIndex("additional_info")
        val traitDataSourceIndex = cursor.getColumnIndex("trait_data_source")
        val synonymsIndex = cursor.getColumnIndex("variable_synonyms")

        if (nameIndex == -1 || aliasIndex == -1 || formatIndex == -1 || defaultValueIndex == -1 ||
            detailsIndex == -1 || idIndex == -1 || externalDbIdIndex == -1 ||
            realPositionIndex == -1 || visibleIndex == -1 || additionalInfoIndex == -1 ||
            traitDataSourceIndex == -1 || synonymsIndex == -1) {
            return
        }

        name = cursor.getString(nameIndex) ?: ""
        alias = cursor.getString(aliasIndex) ?: ""
        format = cursor.getString(formatIndex) ?: ""
        defaultValue = cursor.getString(defaultValueIndex) ?: ""
        details = cursor.getString(detailsIndex) ?: ""
        id = cursor.getString(idIndex) ?: ""
        externalDbId = cursor.getString(externalDbIdIndex) ?: ""
        realPosition = cursor.getInt(realPositionIndex)
        visible = cursor.getString(visibleIndex) == "true"
        additionalInfo = cursor.getString(additionalInfoIndex) ?: ""
        traitDataSource = cursor.getString(traitDataSourceIndex) ?: ""
        _synonyms = cursor.getString(synonymsIndex) ?: ""

        loadAttributeAndValues()
    }

    fun setAttributeValue(attribute: AttributeDefinition, value: String) {
        attributeValues.setValue(attribute, value)
    }

    fun applyAttributeJson(attrs: Map<String, JsonElement>) {
        attrs.forEach { (key, jsonValue) ->
            val def = TraitAttributes.byKey(key) ?: return@forEach

            val value: String = when (jsonValue) {
                is JsonPrimitive -> jsonValue.content // for strings/numbers/boolean
                else -> jsonValue.toString() // for arrays
            }

            setAttributeValue(def, value)
        }
    }

    fun toAttributeJsonMap(): Map<String, JsonElement> {
        loadAttributeAndValues()

        val map = mutableMapOf<String, JsonElement>()

        for (def in TraitAttributes.ALL) {
            val value = attributeValues.getString(def)
            if (value.isNotEmpty() && value != def.defaultValue) {
                map[def.key] = JsonPrimitive(value)
            }
        }

        return map
    }
}