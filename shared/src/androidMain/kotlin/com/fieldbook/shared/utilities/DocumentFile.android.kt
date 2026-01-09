package com.fieldbook.shared.utilities

import com.fieldbook.shared.AndroidAppContextHolder
import org.phenoapps.utils.BaseDocumentTreeUtil
import androidx.documentfile.provider.DocumentFile as AndroidXDocumentFile

class AndroidDocumentFile(val file: AndroidXDocumentFile) : DocumentFile {
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

    override fun name(): String? = file.name
}

actual fun createDir(parent: String, child: String): DocumentFile? {
    val dir = BaseDocumentTreeUtil.createDir(AndroidAppContextHolder.context, parent, child)
    return dir?.let { AndroidDocumentFile(it) }
}

actual fun getExportDirectory(): DocumentFile? {
    val ctx = AndroidAppContextHolder.context
    val exportDir = BaseDocumentTreeUtil.getDirectory(ctx, ctx.resources.getIdentifier("dir_field_export", "string", ctx.packageName))
    return exportDir?.let { AndroidDocumentFile(it) }
}

actual fun openOutputStream(file: DocumentFile): Any? {
    val androidFile = (file as? AndroidDocumentFile)?.file ?: return null
    return AndroidAppContextHolder.context.contentResolver.openOutputStream(androidFile.uri)
}

actual fun listFiles(dir: DocumentFile): List<DocumentFile> {
    val androidDir = (dir as? AndroidDocumentFile)?.file ?: return emptyList()
    return androidDir.listFiles().map { AndroidDocumentFile(it) }
}

actual fun copyFileToDirectory(source: DocumentFile, destinationDir: DocumentFile, newFileName: String): DocumentFile? {
    val src = (source as? AndroidDocumentFile)?.file ?: return null
    val dest = (destinationDir as? AndroidDocumentFile)?.file ?: return null
    val newFile = dest.createFile(src.type ?: "*/*", newFileName)
    if (newFile != null) {
        AndroidAppContextHolder.context.contentResolver.openInputStream(src.uri)?.use { ins ->
            AndroidAppContextHolder.context.contentResolver.openOutputStream(newFile.uri)?.use { outs ->
                ins.copyTo(outs)
            }
        }
        return AndroidDocumentFile(newFile)
    }
    return null
}

actual fun zipFiles(files: List<DocumentFile>, zipFileName: String): DocumentFile? {
    val ctx = AndroidAppContextHolder.context
    val exportDir = getExportDirectory() as? AndroidDocumentFile ?: return null
    val zipFile = exportDir.file.createFile("application/zip", "$zipFileName.zip")
    zipFile?.let { zf ->
        val zfName = zf.name ?: return AndroidDocumentFile(zf)
        val os = BaseDocumentTreeUtil.getFileOutputStream(ctx, ctx.resources.getIdentifier("dir_field_export", "string", ctx.packageName), zfName)
        os?.let { outStream ->
            // placeholder: real zipping should be implemented using ZipUtil
            outStream.close()
            return AndroidDocumentFile(zf)
        }
    }
    return null
}

actual fun shareFile(file: DocumentFile) {
    val androidFile = (file as? AndroidDocumentFile)?.file ?: return
    val intent = android.content.Intent()
    intent.action = android.content.Intent.ACTION_SEND
    intent.type = androidFile.type ?: "*/*"
    intent.putExtra(android.content.Intent.EXTRA_STREAM, androidFile.uri)
    intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    AndroidAppContextHolder.context.startActivity(android.content.Intent.createChooser(intent, "Export"))
}

actual fun deleteFile(file: DocumentFile) {
    (file as? AndroidDocumentFile)?.file?.delete()
}
