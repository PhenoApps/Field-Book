package com.fieldbook.shared.utilities

import com.fieldbook.shared.database.models.TraitObject
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader

object CSVUtil {
    fun readRows(bytes: ByteArray): List<List<String>> {
        return csvReader().readAll(bytes.decodeToString())
    }

    fun parseTraits(bytes: ByteArray, positionOffset: Int = 0): List<TraitObject> {
        return readRows(bytes)
            .drop(1)
            .mapNotNull { row ->
                if (row.size <= 1 || row[0].isBlank() || row[1].isBlank()) {
                    return@mapNotNull null
                }

                val realPosition = row.getOrNull(8)?.toIntOrNull() ?: return@mapNotNull null

                TraitObject(
                    name = row[0],
                    format = row[1],
                    defaultValue = row.getOrNull(2).orEmpty(),
                    minimum = row.getOrNull(3).orEmpty(),
                    maximum = row.getOrNull(4).orEmpty(),
                    details = row.getOrNull(5).orEmpty(),
                    categories = row.getOrNull(6).orEmpty(),
                    visible = if (row.getOrNull(7).equals("true", ignoreCase = true)) "true" else "false",
                    realPosition = positionOffset + realPosition,
                    traitDataSource = "local"
                )
            }
    }
}
