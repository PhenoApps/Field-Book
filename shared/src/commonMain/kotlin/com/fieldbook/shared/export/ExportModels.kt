package com.fieldbook.shared.export

data class ExportOptions(
    val formatDb: Boolean = false,
    val formatTable: Boolean = false,
    val onlyUnique: Boolean = false,
    val allColumns: Boolean = false,
    val activeTraits: Boolean = true,
    val allTraits: Boolean = false,
    val bundleMedia: Boolean = false,
    val overwrite: Boolean = false,
    val fileName: String = "",
    val multipleFields: Boolean = false
)

sealed class ExportResult {
    data class Success(val message: String): ExportResult()
    data class Failure(val error: Throwable): ExportResult()
    object NoData: ExportResult()
}

// A minimal platform-agnostic DocumentFile representation used by shared code
interface PlatformDocumentFile {
    val name: String?
    val isDirectory: Boolean
    // on JVM actual this can expose uri if needed
}

