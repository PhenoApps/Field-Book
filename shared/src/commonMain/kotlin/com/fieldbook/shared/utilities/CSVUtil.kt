package com.fieldbook.shared.utilities

import app.softwork.serialization.csv.CSVFormat
import com.fieldbook.shared.database.models.TraitObject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

object CSVUtil {
    fun readRows(bytes: ByteArray): List<List<String>> {
        return parseCsvRows(bytes.decodeToString())
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun parseTraits(bytes: ByteArray, positionOffset: Int = 0): List<TraitObject> {
        val csv = bytes.decodeToString()
        val rows = CSVFormat.decodeFromString(ListSerializer(TraitCsvRow.serializer()), csv)

        return rows.mapNotNull { row ->
            if (row.trait.isNullOrBlank() || row.format.isNullOrBlank()) {
                return@mapNotNull null
            }

            val realPosition = row.realPosition?.toIntOrNull() ?: return@mapNotNull null

            TraitObject(
                name = row.trait,
                format = row.format,
                defaultValue = row.defaultValue.orEmpty(),
                minimum = row.minimum.orEmpty(),
                maximum = row.maximum.orEmpty(),
                details = row.details.orEmpty(),
                categories = row.categories.orEmpty(),
                visible = if (row.isVisible.equals("true", ignoreCase = true)) "true" else "false",
                realPosition = positionOffset + realPosition,
                traitDataSource = "local"
            )
        }
    }

    private fun parseCsvRows(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val value = StringBuilder()
        var index = 0
        var inQuotes = false

        fun endValue() {
            row.add(value.toString())
            value.clear()
        }

        fun endRow() {
            endValue()
            if (row.any { it.isNotEmpty() }) {
                rows.add(row.toList())
            }
            row.clear()
        }

        while (index < input.length) {
            val char = input[index]

            when {
                char == '"' && inQuotes && input.getOrNull(index + 1) == '"' -> {
                    value.append('"')
                    index++
                }

                char == '"' -> {
                    inQuotes = !inQuotes
                }

                char == ',' && !inQuotes -> {
                    endValue()
                }

                (char == '\n' || char == '\r') && !inQuotes -> {
                    endRow()
                    if (char == '\r' && input.getOrNull(index + 1) == '\n') {
                        index++
                    }
                }

                else -> {
                    value.append(char)
                }
            }

            index++
        }

        if (value.isNotEmpty() || row.isNotEmpty()) {
            endRow()
        }

        return rows
    }
}

@Serializable
private data class TraitCsvRow(
    @SerialName("trait")
    val trait: String? = null,
    @SerialName("format")
    val format: String? = null,
    @SerialName("defaultValue")
    val defaultValue: String? = null,
    @SerialName("minimum")
    val minimum: String? = null,
    @SerialName("maximum")
    val maximum: String? = null,
    @SerialName("details")
    val details: String? = null,
    @SerialName("categories")
    val categories: String? = null,
    @SerialName("isVisible")
    val isVisible: String? = null,
    @SerialName("realPosition")
    val realPosition: String? = null,
)
