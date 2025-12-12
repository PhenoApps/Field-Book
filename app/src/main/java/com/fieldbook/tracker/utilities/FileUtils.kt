package com.fieldbook.tracker.utilities

import android.content.Context
import android.net.Uri
import org.phenoapps.utils.BaseDocumentTreeUtil

object FileUtils {
    fun Uri.copyToDirectory(context: Context, dirRes: Int, fileName: String): Uri? =
        runCatching {
            val traitDir = BaseDocumentTreeUtil.Companion.getDirectory(context, dirRes) ?: return null
            val destination = traitDir.createFile("*/*", fileName) ?: return null

            context.contentResolver.openInputStream(this)?.use { input ->
                BaseDocumentTreeUtil.Companion.getFileOutputStream(context, dirRes, fileName)?.use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            destination.uri
        }.getOrNull()
}