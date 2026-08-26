package org.phenoapps.labelprint.service

import org.phenoapps.labelprint.model.LabelDesignElement

/**
 * Data class representing a ZPL label template.
 */
data class LabelTemplate(
    val id: String,
    val name: String,
    val zpl: String,
    val assignments: Map<String, String> = emptyMap(),
    val defaultValues: Map<String, String> = emptyMap()
)

/**
 * Interface for providing and persisting label templates.
 * Implement this in the app to use database or other storage.
 */
interface LabelTemplateProvider {
    fun getAllTemplates(): Map<String, String> // id -> name
    fun getTemplate(id: String): LabelTemplate?
    fun saveTemplate(template: LabelTemplate): String // returns id
    fun deleteTemplate(id: String)
    
    // Built-in defaults
    fun getBuiltInTemplates(): Map<String, String>
}

/**
 * Interface for printer connectivity.
 */
interface PrinterConnector {
    fun isBluetoothEnabled(): Boolean
    fun getConnectedPrinterName(): String?
    fun connectToPrinter(onResult: (Boolean) -> Unit)
    fun print(zplLabels: List<String>, onResult: (Boolean, String?) -> Unit)
    fun calibrate(onResult: (Boolean) -> Unit)
}

/**
 * Interface for resolving template placeholders to actual values.
 */
interface PropertyResolver {
    fun resolve(placeholder: String, propertyKey: String): String
}
