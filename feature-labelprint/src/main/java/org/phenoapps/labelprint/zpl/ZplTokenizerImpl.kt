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

            val cmdBuilder = StringBuilder()
            while (pos < input.length && input[pos].isUpperCase() && cmdBuilder.length < 3) {
                cmdBuilder.append(input[pos])
                pos++
                col++
                if (cmdBuilder.length == 2 && (cmdBuilder.toString() == "FD" || cmdBuilder.toString() == "FX")) {
                    break
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
