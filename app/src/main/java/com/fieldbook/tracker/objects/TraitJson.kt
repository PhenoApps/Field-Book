package com.fieldbook.tracker.objects

import com.fieldbook.tracker.database.models.TraitAttributes
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
    @SerialName("printTemplate")
    val printTemplate: String? = null,
    @SerialName("printTemplateName")
    val printTemplateName: String? = null,
)

fun TraitObject.toTraitJson(
    printTemplate: String? = null,
    printTemplateName: String? = null,
): TraitJson {
    val attributes = toAttributeJsonMap().toMutableMap().also { map ->
        if (printTemplate != null) {
            map.remove(TraitAttributes.PRINT_TEMPLATE_ID.key)
        }
    }.ifEmpty { null }

    return TraitJson(
        name = name,
        alias = alias,
        synonyms = synonyms,
        format = format,
        defaultValue = defaultValue,
        details = details,
        visible = visible,
        position = realPosition,
        attributes = attributes,
        printTemplate = printTemplate,
        printTemplateName = printTemplateName,
    )
}