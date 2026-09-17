package org.phenoapps.labelprint.zpl

/**
 * Default implementation of [ZplParser].
 */
class ZplParserImpl : ZplParser {

    override fun parse(tokens: List<ZplToken>): ParseResult {
        val labels = mutableListOf<ZplLabel>()
        val errors = mutableListOf<ZplParseError>()
        var state = ParserState()
        var elements = mutableListOf<ZplElement>()
        var inLabel = false
        var i = 0
        val consumedFdIndices = mutableSetOf<Int>()

        while (i < tokens.size) {
            val token = tokens[i]

            val handled = handleBarcodeCommand(
                token, tokens, i, inLabel, state, elements, errors, consumedFdIndices
            )
            if (handled != null) {
                state = handled
                i++
                continue
            }

            when (token.command) {
                "XA" -> {
                    if (inLabel) {
                        labels.add(ZplLabel(state.labelWidth, state.labelHeight, elements.toList()))
                    }
                    inLabel = true
                    state = ParserState()
                    elements = mutableListOf()
                }
                "XZ" -> {
                    if (inLabel) {
                        labels.add(ZplLabel(state.labelWidth, state.labelHeight, elements.toList()))
                        inLabel = false
                    }
                }
                "FO" -> {
                    parseFieldOrigin(token.parameters)?.let { (x, y) ->
                        state = state.copy(currentX = x, currentY = y)
                    }
                }
                "A" -> {
                    parseFontCommand(token.parameters)?.let { (name, height, width) ->
                        state = state.copy(
                            defaultFontName = name,
                            defaultFontHeight = height,
                            defaultFontWidth = width
                        )
                    }
                }
                "CF" -> {
                    parseCfCommand(token.parameters)?.let { (name, height, width) ->
                        state = state.copy(
                            defaultFontName = name,
                            defaultFontHeight = height,
                            defaultFontWidth = width
                        )
                    }
                }
                "FB" -> {
                    parseFieldBlock(token.parameters)?.let { fb ->
                        state = state.copy(
                            fieldBlockWidth = fb.width,
                            fieldBlockMaxLines = fb.maxLines,
                            fieldBlockLineSpacing = fb.lineSpacing,
                            fieldBlockJustify = fb.justify
                        )
                    }
                }
                "FD" -> {
                    if (i in consumedFdIndices) {
                        i++
                        continue
                    }
                    if (inLabel) {
                        if (state.fieldBlockWidth != null) {
                            elements.add(
                                ZplElement.TextBlock(
                                    x = state.currentX,
                                    y = state.currentY,
                                    data = token.parameters,
                                    blockWidth = state.fieldBlockWidth!!,
                                    maxLines = state.fieldBlockMaxLines,
                                    lineSpacing = state.fieldBlockLineSpacing,
                                    justify = state.fieldBlockJustify,
                                    fontHeight = state.defaultFontHeight,
                                    fontWidth = state.defaultFontWidth,
                                    fontName = state.defaultFontName
                                )
                            )
                            state = state.copy(fieldBlockWidth = null)
                        } else {
                            elements.add(
                                ZplElement.Text(
                                    x = state.currentX,
                                    y = state.currentY,
                                    data = token.parameters,
                                    fontHeight = state.defaultFontHeight,
                                    fontWidth = state.defaultFontWidth,
                                    fontName = state.defaultFontName
                                )
                            )
                        }
                    }
                }
                "PW" -> {
                    val width = token.parameters.trim().toIntOrNull()
                    if (width != null && width > 0) {
                        state = state.copy(labelWidth = width)
                    }
                }
                "LL" -> {
                    val height = token.parameters.trim().toIntOrNull()
                    if (height != null && height > 0) {
                        state = state.copy(labelHeight = height)
                    }
                }
            }
            i++
        }

        if (inLabel) {
            labels.add(ZplLabel(state.labelWidth, state.labelHeight, elements.toList()))
        }

        return if (labels.isNotEmpty()) {
            ParseResult.Success(ZplDocument(labels))
        } else if (errors.isNotEmpty()) {
            ParseResult.Error(errors)
        } else {
            ParseResult.Success(ZplDocument(emptyList()))
        }
    }

    private fun handleBarcodeCommand(
        token: ZplToken,
        tokens: List<ZplToken>,
        index: Int,
        inLabel: Boolean,
        state: ParserState,
        elements: MutableList<ZplElement>,
        errors: MutableList<ZplParseError>,
        consumedFdIndices: MutableSet<Int>
    ): ParserState? {
        return when {
            token.command.startsWith("BY") -> {
                val fullParams = reconstructParams(token.command, "BY", token.parameters)
                var newState = state
                parseBarcodeDefaults(fullParams, state)?.let { (moduleWidth, ratio, height) ->
                    newState = state.copy(
                        barcodeModuleWidth = moduleWidth,
                        barcodeRatio = ratio,
                        barcodeHeight = height
                    )
                }
                newState
            }
            token.command.startsWith("BQ") -> {
                if (inLabel) {
                    val fullParams = reconstructParams(token.command, "BQ", token.parameters)
                    val qrParams = parseQrParams(fullParams)
                    val fdIndex = findNextFdIndex(tokens, index)
                    if (fdIndex != null) {
                        consumedFdIndices.add(fdIndex)
                        elements.add(
                            ZplElement.QrCode(
                                x = state.currentX,
                                y = state.currentY,
                                data = tokens[fdIndex].parameters,
                                model = qrParams.model,
                                magnification = qrParams.magnification
                            )
                        )
                    }
                }
                state
            }
            token.command.startsWith("BC") -> {
                if (inLabel) {
                    val fullParams = reconstructParams(token.command, "BC", token.parameters)
                    val bcParams = parseBarcodeParams(fullParams, state)
                    val fdIndex = findNextFdIndex(tokens, index)
                    if (fdIndex != null) {
                        consumedFdIndices.add(fdIndex)
                        elements.add(
                            ZplElement.Barcode128(
                                x = state.currentX,
                                y = state.currentY,
                                data = tokens[fdIndex].parameters,
                                height = bcParams.height,
                                orientation = bcParams.orientation,
                                printInterpretation = bcParams.printInterpretation,
                                interpretationAbove = bcParams.interpretationAbove,
                                moduleWidth = state.barcodeModuleWidth,
                                ratio = state.barcodeRatio
                            )
                        )
                    }
                }
                state
            }
            else -> null
        }
    }

    private fun reconstructParams(command: String, prefix: String, parameters: String): String {
        val extraChars = command.substring(prefix.length)
        return if (extraChars.isEmpty()) {
            parameters
        } else {
            extraChars + parameters
        }
    }

    private fun parseFieldOrigin(params: String): Pair<Int, Int>? {
        val parts = params.split(",")
        if (parts.size < 2) return null
        val x = parts[0].trim().toIntOrNull() ?: return null
        val y = parts[1].trim().toIntOrNull() ?: return null
        if (x < 0 || y < 0) return null
        return Pair(x, y)
    }

    private fun parseFontCommand(params: String): Triple<Char, Int, Int>? {
        if (params.isEmpty()) return null

        val fontName = params[0]
        if (!fontName.isLetterOrDigit()) return null

        val rest = if (params.length > 1 && params[1].isUpperCase()) {
            params.substring(2)
        } else {
            params.substring(1)
        }

        val trimmed = rest.trimStart(',')
        val parts = trimmed.split(",")

        val height = parts.getOrNull(0)?.trim()?.toIntOrNull()
        val width = parts.getOrNull(1)?.trim()?.toIntOrNull()

        if (height == null || height <= 0) return null

        return Triple(fontName, height, width ?: 0)
    }

    private fun parseCfCommand(params: String): Triple<Char, Int, Int>? {
        if (params.isEmpty()) return null

        val fontName = params[0]
        if (!fontName.isLetterOrDigit()) return null

        val rest = params.substring(1).trimStart(',')
        val parts = rest.split(",")

        val height = parts.getOrNull(0)?.trim()?.toIntOrNull()
        val width = parts.getOrNull(1)?.trim()?.toIntOrNull()

        if (height == null || height <= 0) return null

        return Triple(fontName, height, width ?: 0)
    }

    private data class FieldBlockParams(
        val width: Int,
        val maxLines: Int = 1,
        val lineSpacing: Int = 0,
        val justify: Justify = Justify.LEFT
    )

    private fun parseFieldBlock(params: String): FieldBlockParams? {
        val parts = params.split(",")
        val width = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        if (width <= 0) return null

        val maxLines = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val lineSpacing = parts.getOrNull(2)?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val justify = when (parts.getOrNull(3)?.trim()) {
            "C" -> Justify.CENTER
            "R" -> Justify.RIGHT
            "L", "", null -> Justify.LEFT
            else -> Justify.LEFT
        }

        return FieldBlockParams(width, maxLines, lineSpacing, justify)
    }

    private fun findNextFdIndex(tokens: List<ZplToken>, currentIndex: Int): Int? {
        for (j in (currentIndex + 1) until tokens.size) {
            when (tokens[j].command) {
                "FD" -> return j
                "XZ", "XA" -> return null
            }
        }
        return null
    }

    private data class QrParams(
        val model: Int = 2,
        val magnification: Int = 3
    )

    private fun parseQrParams(params: String): QrParams {
        if (params.isBlank()) return QrParams()

        val parts = params.split(",")
        val model = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 2
        val magnification = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 3

        return QrParams(
            model = if (model in 1..2) model else 2,
            magnification = if (magnification in 1..10) magnification else 3
        )
    }

    private data class BarcodeParams(
        val orientation: Char = 'N',
        val height: Int = 100,
        val printInterpretation: Boolean = true,
        val interpretationAbove: Boolean = false
    )

    private fun parseBarcodeParams(params: String, state: ParserState): BarcodeParams {
        if (params.isBlank()) return BarcodeParams(height = state.barcodeHeight)

        val parts = params.split(",")

        val orientation = parts.getOrNull(0)?.trim()?.firstOrNull() ?: 'N'
        val height = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: state.barcodeHeight
        val printInterpretation = when (parts.getOrNull(2)?.trim()) {
            "N" -> false
            "Y", "", null -> true
            else -> true
        }
        val interpretationAbove = when (parts.getOrNull(3)?.trim()) {
            "Y" -> true
            "N", "", null -> false
            else -> false
        }

        return BarcodeParams(
            orientation = if (orientation in "NRIB") orientation else 'N',
            height = if (height > 0) height else state.barcodeHeight,
            printInterpretation = printInterpretation,
            interpretationAbove = interpretationAbove
        )
    }

    private fun parseBarcodeDefaults(params: String, state: ParserState): Triple<Int, Float, Int>? {
        if (params.isBlank()) return null

        val parts = params.split(",")
        val moduleWidth = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        if (moduleWidth !in 1..10) return null

        val ratio = parts.getOrNull(1)?.trim()?.toFloatOrNull() ?: state.barcodeRatio
        val height = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: state.barcodeHeight

        return Triple(
            moduleWidth,
            if (ratio > 0f) ratio else state.barcodeRatio,
            if (height > 0) height else state.barcodeHeight
        )
    }
}
