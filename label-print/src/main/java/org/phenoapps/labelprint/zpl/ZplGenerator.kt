package org.phenoapps.labelprint.zpl

import org.phenoapps.labelprint.model.LabelDesignElement
import org.phenoapps.labelprint.model.LabelDesignElementType

/**
 * Generates formatted ZPL from a list of visual design elements and label dimensions.
 */
object ZplGenerator {

    const val NORMALIZED_DPI = 203

    const val WIDTH_3X2 = 609
    const val HEIGHT_3X2 = 406
    const val WIDTH_2X1 = 406
    const val HEIGHT_2X1 = 203

    const val MEDIA_THERMAL_DIRECT = "Direct Thermal"
    const val MEDIA_THERMAL_TRANSFER = "Thermal Transfer"

    const val TRACKING_GAP = "Gap/Notch"
    const val TRACKING_CONTINUOUS = "Continuous"
    const val TRACKING_MARK = "Mark"

    fun generate(
        elements: List<LabelDesignElement>,
        labelWidthDots: Int = WIDTH_3X2,
        labelHeightDots: Int = HEIGHT_3X2,
        mediaType: String = MEDIA_THERMAL_DIRECT,
        mediaGap: String = TRACKING_GAP
    ): String {
        val lines = mutableListOf<String>()
        lines.add("^XA")
        lines.add("^POI")
        val mtCode = if (mediaType == MEDIA_THERMAL_TRANSFER) "T" else "D"
        lines.add("^MT$mtCode")
        val mnCode = when (mediaGap) {
            TRACKING_CONTINUOUS -> "Y"
            TRACKING_MARK -> "M"
            else -> "N"
        }
        lines.add("^MN$mnCode")
        lines.add("^PW$labelWidthDots")
        lines.add("^LL${labelHeightDots.toString().padStart(4, '0')}")

        for (element in elements) {
            when (element.type) {
                LabelDesignElementType.TEXT -> {
                    lines.add("^FO${element.x},${element.y}")
                    if (element.blockWidth > 0) {
                        lines.add("^FB${element.blockWidth},2,0,C,0")
                    }
                    lines.add("^A0,${element.fontSize},")
                    lines.add("^FD${element.prefix}${element.placeholder}${element.suffix}^FS")
                }
                LabelDesignElementType.QR_CODE -> {
                    lines.add("^FO${element.x},${element.y}")
                    lines.add("^BQ,,${element.magnification}")
                    lines.add("^FDMA,${element.placeholder}^FS")
                }
                LabelDesignElementType.BARCODE_128 -> {
                    lines.add("^FO${element.x},${element.y}")
                    lines.add("^BY${element.moduleWidth}")
                    lines.add("^BCN,${element.barcodeHeight},Y,N,N")
                    lines.add("^FD${element.placeholder}^FS")
                }
            }
        }

        lines.add("^XZ")
        return lines.joinToString("\n")
    }

    data class ParsedTemplate(
        val elements: List<LabelDesignElement>,
        val labelWidth: Int,
        val labelHeight: Int,
        val mediaType: String,
        val mediaGap: String = TRACKING_GAP
    )

    fun parseTemplate(zpl: String): ParsedTemplate {
        val elements = mutableListOf<LabelDesignElement>()
        var labelWidth = WIDTH_3X2
        var labelHeight = HEIGHT_3X2
        var mediaType = MEDIA_THERMAL_DIRECT
        var mediaGap = TRACKING_GAP

        val flat = zpl.replace("\n", "")

        val pwMatch = Regex("""\^PW(\d+)""").find(flat)
        if (pwMatch != null) {
            labelWidth = pwMatch.groupValues[1].toIntOrNull() ?: labelWidth
        }

        val llMatch = Regex("""\^LL(\d+)""").find(flat)
        if (llMatch != null) {
            labelHeight = llMatch.groupValues[1].toIntOrNull() ?: labelHeight
        }

        val mtMatch = Regex("""\^MT([DT])""").find(flat)
        if (mtMatch != null) {
            mediaType = if (mtMatch.groupValues[1] == "T") MEDIA_THERMAL_TRANSFER else MEDIA_THERMAL_DIRECT
        }

        val mnMatch = Regex("""\^MN([NYM])""").find(flat)
        if (mnMatch != null) {
            mediaGap = when (mnMatch.groupValues[1]) {
                "Y" -> TRACKING_CONTINUOUS
                "M" -> TRACKING_MARK
                else -> TRACKING_GAP
            }
        }

        val segments = flat.split("^FO").drop(1)

        var idCounter = 0
        for (segment in segments) {
            val posMatch = Regex("""^(\d+),(\d+)""").find(segment) ?: continue
            val x = posMatch.groupValues[1].toIntOrNull() ?: 0
            val y = posMatch.groupValues[2].toIntOrNull() ?: 0

            if (segment.contains("^BQ")) {
                val magMatch = Regex("""\^BQ,,(\d+)""").find(segment)
                val mag = magMatch?.groupValues?.get(1)?.toIntOrNull() ?: 5
                val dataMatch = Regex("""\^FDMA,(.+?)\^FS""").find(segment)
                val placeholder = dataMatch?.groupValues?.get(1) ?: "{qrcode1}"

                elements.add(
                    LabelDesignElement(
                        id = "elem_${idCounter++}",
                        type = LabelDesignElementType.QR_CODE,
                        x = x,
                        y = y,
                        placeholder = placeholder,
                        magnification = mag
                    )
                )
            } else if (segment.contains("^BC")) {
                val byMatch = Regex("""\^BY(\d+)""").find(segment)
                val modWidth = byMatch?.groupValues?.get(1)?.toIntOrNull() ?: 2
                val bcMatch = Regex("""\^BCN,(\d+)""").find(segment)
                val height = bcMatch?.groupValues?.get(1)?.toIntOrNull() ?: 80
                val dataMatch = Regex("""\^FD(.+?)\^FS""").find(segment)
                val placeholder = dataMatch?.groupValues?.get(1) ?: "{barcode1}"

                elements.add(
                    LabelDesignElement(
                        id = "elem_${idCounter++}",
                        type = LabelDesignElementType.BARCODE_128,
                        x = x,
                        y = y,
                        placeholder = placeholder,
                        barcodeHeight = height,
                        moduleWidth = modWidth
                    )
                )
            } else if (segment.contains("^FD")) {
                val fontMatch = Regex("""\^A0,(\d+),""").find(segment)
                val fontSize = fontMatch?.groupValues?.get(1)?.toIntOrNull() ?: 28
                val blockMatch = Regex("""\^FB(\d+),""").find(segment)
                val blockWidth = blockMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val dataMatch = Regex("""\^FD(.+?)\^FS""").find(segment)
                val fullText = dataMatch?.groupValues?.get(1) ?: "text"
                val placeholderMatch = Regex("""(\{.+?\})""").find(fullText)
                val placeholder = placeholderMatch?.groupValues?.get(1) ?: fullText
                val prefix = if (placeholderMatch != null) fullText.substringBefore(placeholder) else ""
                val suffix = if (placeholderMatch != null) fullText.substringAfter(placeholder) else ""
                val isDate = placeholder.startsWith("{date")

                elements.add(
                    LabelDesignElement(
                        id = "elem_${idCounter++}",
                        type = LabelDesignElementType.TEXT,
                        x = x,
                        y = y,
                        placeholder = placeholder,
                        fontSize = fontSize,
                        blockWidth = blockWidth,
                        prefix = prefix,
                        suffix = suffix,
                        isDate = isDate
                    )
                )
            }
        }

        return ParsedTemplate(
            elements = elements,
            labelWidth = labelWidth,
            labelHeight = labelHeight,
            mediaType = mediaType,
            mediaGap = mediaGap
        )
    }
}
