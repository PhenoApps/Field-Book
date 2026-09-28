package org.phenoapps.labelprint.zpl

/**
 * Immutable intermediate representation of a parsed ZPL document
 * containing one or more labels.
 */
data class ZplDocument(
    val labels: List<ZplLabel>
)

/**
 * A single label within a ZPL document, with dimensions in dots and positioned elements.
 */
data class ZplLabel(
    val widthDots: Int = 812,
    val heightDots: Int = 1218,
    val elements: List<ZplElement>
)

/**
 * Result of parsing a list of ZPL tokens into a document.
 */
sealed class ParseResult {
    data class Success(val document: ZplDocument) : ParseResult()
    data class Error(val errors: List<ZplParseError>) : ParseResult()
}

/**
 * Describes a parse error with position and context information.
 */
data class ZplParseError(
    val message: String,
    val line: Int,
    val column: Int,
    val command: String? = null
)
