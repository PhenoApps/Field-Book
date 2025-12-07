package com.fieldbook.tracker.utilities

import android.content.Context
import android.net.Uri
import com.fieldbook.tracker.enums.FileFormat

object TraitImportFileUtil {

    private const val TAG = "TraitFileFormatUtil"

    /**
     * Detects the format of a trait file by examining its content
     */
    fun detectTraitFileFormat(context: Context, fileUri: Uri): FileFormat {
        return runCatching {
            context.contentResolver.openInputStream(fileUri)?.bufferedReader()?.use {
                val firstLine = it.readLine()?.trim().orEmpty()
                when {
                    firstLine.startsWith("{") || firstLine.startsWith("[") -> FileFormat.JSON
                    firstLine.contains("\"trait\"") -> FileFormat.CSV
                    else -> FileFormat.UNKNOWN
                }
            } ?: FileFormat.UNKNOWN
        }.getOrDefault(FileFormat.UNKNOWN) // if something fails
    }
}