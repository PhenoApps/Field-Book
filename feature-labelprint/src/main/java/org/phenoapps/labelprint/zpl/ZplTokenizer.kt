package org.phenoapps.labelprint.zpl

/**
 * Interface for tokenizing raw ZPL strings into structured command tokens.
 */
interface ZplTokenizer {
    /**
     * Splits a raw ZPL input string into a list of [ZplToken]s.
     */
    fun tokenize(input: String): TokenizeResult
}
