package org.phenoapps.labelprint.service

import org.phenoapps.labelprint.zpl.ZplGenerator
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main entry point for label printing logic in the library.
 * Coordinates between template management and printer connectivity.
 */
@Singleton
class LabelPrintManager @Inject constructor(
    private val templateProvider: LabelTemplateProvider,
    private val printerConnector: PrinterConnector
) {

    companion object {
        /**
         * Regex pattern matching template placeholders.
         * Supports both formats:
         * - Single-brace: {text1}, {qrcode1}, {barcode1}
         * - Double-brace: {{F1}}, {{B1}}, {{Q1}}
         */
        private val PLACEHOLDER_PATTERN = Regex("""\{\{[^}]+\}\}|\{[^}]+\}""")

        /**
         * Extracts all unique placeholder names from a ZPL template string.
         */
        fun extractPlaceholders(templateZpl: String): List<String> {
            val placeholders = mutableListOf<String>()
            for (match in PLACEHOLDER_PATTERN.findAll(templateZpl)) {
                val placeholder = match.value
                if (placeholder !in placeholders && !placeholder.startsWith("{date")) {
                    placeholders.add(placeholder)
                }
            }
            return placeholders
        }

        /** Field data between ^FD and ^FS, where placeholders live. */
        private val FIELD_DATA_PATTERN = Regex("""\^FD(.*?)\^FS""", RegexOption.DOT_MATCHES_ALL)

        /** Hex escape indicator used with ^FH (the ZPL default). */
        private const val HEX_INDICATOR = '_'

        /**
         * Resolves all placeholders in a ZPL template using the provided assignments map,
         * date placeholders resolve to the current date. Unassigned placeholders are left as is.
         *
         * Values are data, so ZPL command characters in them (^ and ~) are hex escaped:
         * the field gets ^FH, and underscores already in the field are escaped to keep their meaning.
         */
        fun applyPlaceholderAssignments(templateZpl: String, assignments: Map<String, String>): String {
            val dateStr = currentDateString()

            fun valueFor(placeholder: String): String? =
                assignments[placeholder] ?: if (placeholder.startsWith("{date")) dateStr else null

            return FIELD_DATA_PATTERN.replace(templateZpl) { field ->
                val data = field.groupValues[1]
                val placeholders = PLACEHOLDER_PATTERN.findAll(data).toList()
                val values = placeholders.mapNotNull { valueFor(it.value) }
                if (values.isEmpty()) return@replace field.value

                val precedingZpl = templateZpl.substring(0, field.range.first).trimEnd()
                val alreadyHex = precedingZpl.endsWith("^FH") || precedingZpl.endsWith("^FH$HEX_INDICATOR")
                val addHex = !alreadyHex && values.any { it.hasZplCommandChars() }
                val escapeValues = alreadyHex || addHex

                val resolved = StringBuilder()
                var index = 0
                for (match in placeholders) {
                    val literal = data.substring(index, match.range.first)
                    resolved.append(if (addHex) literal.hexEscape(onlyIndicator = true) else literal)

                    val value = valueFor(match.value)
                    resolved.append(
                        when {
                            value == null -> match.value
                            escapeValues -> value.hexEscape()
                            else -> value
                        }
                    )
                    index = match.range.last + 1
                }
                val tail = data.substring(index)
                resolved.append(if (addHex) tail.hexEscape(onlyIndicator = true) else tail)

                (if (addHex) "^FH^FD" else "^FD") + resolved + "^FS"
            }
        }

        private fun String.hasZplCommandChars() = contains('^') || contains('~')

        /**
         * Escapes characters for a ^FH field: the indicator itself, and optionally ^ and ~.
         */
        private fun String.hexEscape(onlyIndicator: Boolean = false): String = buildString {
            for (c in this@hexEscape) {
                val escape = c == HEX_INDICATOR || (!onlyIndicator && (c == '^' || c == '~'))
                if (escape) append(HEX_INDICATOR).append("%02X".format(c.code)) else append(c)
            }
        }

        /**
         * Returns the current date formatted as yyyy-MM-dd.
         */
        fun currentDateString(): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return dateFormat.format(Calendar.getInstance().time)
        }
    }

    /**
     * Prints labels using a template ID and resolved assignments.
     */
    fun printFromTemplate(
        templateId: String,
        copies: Int,
        resolver: (String) -> String, // placeholder -> resolved value
        onResult: (Boolean, String?) -> Unit
    ) {
        val template = templateProvider.getTemplate(templateId) ?: run {
            onResult(false, "Template not found")
            return
        }

        val placeholders = extractPlaceholders(template.zpl)
        val assignments = placeholders.associateWith { resolver(it) }
        
        val resolvedZpl = applyPlaceholderAssignments(template.zpl, assignments)
        val labels = List(copies) { resolvedZpl }

        printerConnector.print(labels, onResult)
    }
    
    /**
     * Resolves a template design into a list of DesignElements for editing.
     */
    fun getTemplateDesign(templateId: String): ZplGenerator.ParsedTemplate? {
        val template = templateProvider.getTemplate(templateId) ?: return null
        return ZplGenerator.parseTemplate(template.zpl)
    }
}
