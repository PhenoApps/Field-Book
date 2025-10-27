package com.fieldbook.tracker.utilities

import android.content.Context
import android.net.Uri
import android.util.Log
import com.fieldbook.tracker.enums.FileFormat
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.io.BufferedReader
import java.io.InputStreamReader

object TraitFileFormatUtil {

    private const val TAG = "TraitFileFormatUtil"

    /**
     * Detects the format of a trait file by examining its content
     */
    fun detectTraitFileFormat(context: Context, fileUri: Uri): FileFormat {
        return try {
            val inputStream = BaseDocumentTreeUtil.getUriInputStream(context, fileUri)
                ?: return FileFormat.UNKNOWN

            val reader = BufferedReader(InputStreamReader(inputStream))
            val firstLine = reader.readLine()?.trim()
            reader.close()
            inputStream.close()

            when {
                firstLine == null -> FileFormat.UNKNOWN
                firstLine.startsWith("{") || firstLine.startsWith("[") -> FileFormat.JSON
                firstLine.contains(",") && !firstLine.startsWith("{") -> FileFormat.CSV
                else -> FileFormat.UNKNOWN
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting file format", e)
            FileFormat.UNKNOWN
        }
    }

    /**
     * Generates appropriate file name based on format
     */
    fun generateExportFileName(format: FileFormat): String {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd-hh-mm-ss", java.util.Locale.getDefault())
            .format(java.util.Calendar.getInstance().time)

        return when (format) {
            FileFormat.JSON -> "trait_export_json_$timestamp.trt"
            FileFormat.CSV -> "trait_export_$timestamp.trt"
            FileFormat.UNKNOWN -> "trait_export_$timestamp.trt"
        }
    }

    /**
     * Gets MIME type for the format
     */
    fun getMimeType(format: FileFormat): String {
        return when (format) {
            FileFormat.JSON -> "application/json"
            FileFormat.CSV -> "text/csv"
            FileFormat.UNKNOWN -> "*/*"
        }
    }
}