package com.fieldbook.tracker.objects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
    val defaultValue: String = "",
    val details: String = "",

    @SerialName("isVisible")
    val visible: Boolean = true,

    @SerialName("realPosition")
    val position: Int = 0,
    val attributes: Map<String, JsonElement>? = null,
)

fun TraitObject.toTraitJson(): TraitJson {
    return TraitJson(
        name = name,
        alias = alias,
        synonyms = synonyms,
        format = format,
        defaultValue = defaultValue,
        details = details,
        visible = visible,
        position = realPosition,
        attributes = toAttributeJsonMap().ifEmpty { null }
    )
}