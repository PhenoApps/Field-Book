package com.fieldbook.shared.utilities

import com.fieldbook.shared.AndroidAppContextHolder
import org.phenoapps.utils.BaseDocumentTreeUtil
import androidx.documentfile.provider.DocumentFile as AndroidXDocumentFile

class AndroidDocumentFile(private val file: AndroidXDocumentFile) : DocumentFile {
    override fun createDirectory(name: String): DocumentFile? {
        val dir = file.createDirectory(name)
        return dir?.let { AndroidDocumentFile(it) }
    }

    override fun createFile(mimeType: String, name: String): DocumentFile? {
        val f = file.createFile(mimeType, name)
        return f?.let { AndroidDocumentFile(it) }
    }

    override fun findFile(name: String): DocumentFile? {
        val f = file.findFile(name)
        return f?.let { AndroidDocumentFile(it) }
    }

    override fun exists(): Boolean = file.exists()

    override fun uri(): String = file.uri.toString()

    override fun writeBytes(byteArray: ByteArray) {
        AndroidAppContextHolder.context.contentResolver.openOutputStream(file.uri)
            ?.use { outputStream ->
                outputStream.write(byteArray)
            }
    }
}


/**
 * Creates a child directory within a parent directory.
 * Based on org.phenoapps.utils.BaseDocumentTreeUtil.Companion.createDir(android.content.Context, java.lang.String, java.lang.String)
 */
actual fun createDir(
    parent: String,
    child: String
): DocumentFile? {
    val dir = BaseDocumentTreeUtil.createDir(AndroidAppContextHolder.context, parent, child)
    return dir?.let { AndroidDocumentFile(it) }
}
