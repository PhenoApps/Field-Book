package com.fieldbook.tracker.activities.brapi.hackathon.cropontology.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.brapi.v2.model.BrAPIDataLink
import org.brapi.v2.model.BrAPIExternalReference
import org.brapi.v2.model.core.BrAPIContact
import org.brapi.v2.model.core.BrAPIEnvironmentParameter
import org.brapi.v2.model.pheno.BrAPIObservationUnitHierarchyLevel
import org.threeten.bp.LocalDateTime
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZoneOffset

class CategoryConverter {


    @TypeConverter
    fun offsetDateTimeToString(contacts: OffsetDateTime): Long {
        return contacts.toEpochSecond()
    }

    @TypeConverter
    fun jsonStringToOffsetDateTime(value: Long): OffsetDateTime {
        return OffsetDateTime.of(LocalDateTime.ofEpochSecond(value, 0, ZoneOffset.UTC), ZoneOffset.UTC)
    }

    @TypeConverter
    fun obsUnitHierarchyLevelString(contacts: List<BrAPIObservationUnitHierarchyLevel>): String {
        return Gson().toJson(contacts)
    }

    @TypeConverter
    fun jsonStringToObsUnitHierarchyLevelList(value: String): List<BrAPIObservationUnitHierarchyLevel> {
        val objects = Gson().fromJson(value, Array<BrAPIObservationUnitHierarchyLevel>::class.java)
        return objects.toList()
    }

    @TypeConverter
    fun externalReferencesString(contacts: List<BrAPIExternalReference>): String {
        return Gson().toJson(contacts)
    }

    @TypeConverter
    fun jsonStringToExternalReferencesList(value: String): List<BrAPIExternalReference> {
        val objects = Gson().fromJson(value, Array<BrAPIExternalReference>::class.java)
        return objects.toList()
    }

    @TypeConverter
    fun enParametersToJsonString(contacts: List<BrAPIEnvironmentParameter>): String {
        return Gson().toJson(contacts)
    }

    @TypeConverter
    fun jsonStringToEnvParamsList(value: String): List<BrAPIEnvironmentParameter> {
        val objects = Gson().fromJson(value, Array<BrAPIEnvironmentParameter>::class.java)
        return objects.toList()
    }

    @TypeConverter
    fun dataLinksToJsonString(contacts: List<BrAPIDataLink>): String {
        return Gson().toJson(contacts)
    }

    @TypeConverter
    fun jsonStringToDataLinksList(value: String): List<BrAPIDataLink> {
        val objects = Gson().fromJson(value, Array<BrAPIDataLink>::class.java)
        return objects.toList()
    }

    @TypeConverter
    fun contactsToJsonString(contacts: List<BrAPIContact>): String {
        return Gson().toJson(contacts)
    }

    @TypeConverter
    fun jsonStringToContactsList(value: String): List<BrAPIContact> {
        val objects = Gson().fromJson(value, Array<BrAPIContact>::class.java)
        return objects.toList()
    }

    @TypeConverter
    fun jsonObjectToString(obj: JsonObject): String {
        return obj.asString
    }

    @TypeConverter
    fun stringToJsonObject(str: String): JsonObject {
        return Gson().fromJson(str, JsonObject::class.java)
    }

    @TypeConverter
    fun jsonArrayToString(cat: JsonArray?): String {
        return cat?.joinToString(",") { it.asString } ?: ""
    }

    @TypeConverter
    fun stringToJson(commaDelim: String?): JsonArray {
        return JsonArray().apply {
            commaDelim?.split(",")?.forEach {
                this.add(it)
            }
        }
    }
}