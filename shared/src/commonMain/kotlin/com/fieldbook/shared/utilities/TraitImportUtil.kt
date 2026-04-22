package com.fieldbook.shared.utilities

import com.fieldbook.shared.database.models.TraitObject

object TraitImportUtil {
    private const val UTF8_BOM = "\uFEFF"

    fun parseTraits(bytes: ByteArray, positionOffset: Int = 0): List<TraitObject> {
        val rows = CsvRowsReader(bytes.decodeToString()).readAll()
        if (rows.isEmpty()) return emptyList()

        return rows
            .drop(1) // skip header
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

    private class CsvRowsReader(csv: String) {
        private val source = csv.removePrefix(UTF8_BOM)
        private var index = 0

        fun readAll(): List<List<String>> {
            val rows = mutableListOf<List<String>>()
            while (index < source.length) {
                readNextRow()?.let(rows::add)
            }
            return rows
        }

        private fun readNextRow(): List<String>? {
            if (index >= source.length) return null

            val row = mutableListOf<String>()
            val cell = StringBuilder()
            var inQuotes = false

            while (index < source.length) {
                val current = source[index]

                when {
                    current == '"' -> {
                        val next = source.getOrNull(index + 1)
                        if (inQuotes && next == '"') {
                            cell.append('"')
                            index += 2
                        } else {
                            inQuotes = !inQuotes
                            index++
                        }
                    }

                    current == ',' && !inQuotes -> {
                        row += cell.toString()
                        cell.clear()
                        index++
                    }

                    (current == '\n' || current == '\r') && !inQuotes -> {
                        row += cell.toString()
                        cell.clear()
                        consumeLineBreak()
                        return row
                    }

                    else -> {
                        cell.append(current)
                        index++
                    }
                }
            }

            row += cell.toString()
            return row
        }

        private fun consumeLineBreak() {
            if (source.getOrNull(index) == '\r') index++
            if (source.getOrNull(index) == '\n') index++
        }
    }
}
