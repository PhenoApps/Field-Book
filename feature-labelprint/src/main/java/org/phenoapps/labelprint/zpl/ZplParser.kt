package org.phenoapps.labelprint.zpl

/**
 * Interface for parsing a list of [ZplToken]s into a structured [ZplDocument] IR.
 */
interface ZplParser {
    /**
     * Parses a list of tokens into a [ParseResult] containing a [ZplDocument].
     */
    fun parse(tokens: List<ZplToken>): ParseResult
}
