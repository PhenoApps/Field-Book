package org.phenoapps.labelprint.zpl

/**
 * Represents a single tokenized ZPL command with its parameters and source position.
 */
data class ZplToken(
    val command: String,
    val parameters: String,
    val line: Int,
    val column: Int
)

/**
 * Result of tokenizing a ZPL input string.
 */
sealed class TokenizeResult {
    data class Success(val tokens: List<ZplToken>) : TokenizeResult()
    data class Error(val message: String, val line: Int, val column: Int) : TokenizeResult()
}
