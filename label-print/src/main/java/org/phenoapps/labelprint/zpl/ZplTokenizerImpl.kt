package org.phenoapps.labelprint.zpl

/**
 * Default implementation of [ZplTokenizer].
 */
class ZplTokenizerImpl : ZplTokenizer {

    override fun tokenize(input: String): TokenizeResult {
        val tokens = mutableListOf<ZplToken>()
        var pos = 0
        var line = 1
        var col = 1

        while (pos < input.length) {
            if (input[pos] != '^') {
                if (input[pos] == '\n') {
                    line++
                    col = 1
                } else {
                    col++
                }
                pos++
                continue
            }

            val cmdLine = line
            val cmdCol = col
            pos++
            col++

            // commands are two characters (e.g. ^FO, ^CF, ^B3), except ^A, whose next characters
            // are its font name and orientation (^A0N, ^ADN) and belong to its parameters
            val cmdBuilder = StringBuilder()
            if (pos < input.length && input[pos].isUpperCase()) {
                cmdBuilder.append(input[pos])
                pos++
                col++
                if (cmdBuilder[0] != 'A' && pos < input.length
                    && (input[pos].isUpperCase() || input[pos].isDigit())
                ) {
                    cmdBuilder.append(input[pos])
                    pos++
                    col++
                }
            }
            val command = cmdBuilder.toString()

            if (command.isEmpty()) continue

            val paramBuilder = StringBuilder()
            when (command) {
                "FX" -> {
                    while (pos < input.length && input[pos] != '^') {
                        if (input[pos] == '\n') {
                            line++
                            col = 1
                        } else {
                            col++
                        }
                        pos++
                    }
                }
                "FD" -> {
                    while (pos < input.length) {
                        if (input[pos] == '^'
                            && pos + 2 < input.length
                            && input[pos + 1] == 'F'
                            && input[pos + 2] == 'S'
                        ) {
                            break
                        }
                        paramBuilder.append(input[pos])
                        if (input[pos] == '\n') {
                            line++
                            col = 1
                        } else {
                            col++
                        }
                        pos++
                    }
                }
                else -> {
                    while (pos < input.length && input[pos] != '^') {
                        paramBuilder.append(input[pos])
                        if (input[pos] == '\n') {
                            line++
                            col = 1
                        } else {
                            col++
                        }
                        pos++
                    }
                }
            }

            tokens.add(
                ZplToken(
                    command = command,
                    parameters = paramBuilder.toString().trim(),
                    line = cmdLine,
                    column = cmdCol
                )
            )
        }

        return TokenizeResult.Success(tokens)
    }
}
