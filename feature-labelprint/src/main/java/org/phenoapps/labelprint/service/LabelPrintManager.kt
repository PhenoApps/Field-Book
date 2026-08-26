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
                if (placeholder !in placeholders) {
                    placeholders.add(placeholder)
                }
            }
            return placeholders
        }

        /**
         * Resolves all placeholders in a ZPL template using the provided assignments map.
         */
        fun applyPlaceholderAssignments(templateZpl: String, assignments: Map<String, String>): String {
            var result = templateZpl
            for ((placeholder, value) in assignments) {
                result = result.replace(placeholder, value)
            }
            return result
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
