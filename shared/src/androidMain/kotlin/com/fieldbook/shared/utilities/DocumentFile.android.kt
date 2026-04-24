package com.fieldbook.shared.utilities

import android.content.Intent
import android.os.Build
import com.fieldbook.shared.AndroidAppContextHolder
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.dir_archive
import com.fieldbook.shared.generated.resources.dir_field_export
import com.fieldbook.shared.generated.resources.dir_field_import
import com.fieldbook.shared.generated.resources.dir_trait
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.io.BufferedInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
    override fun isDirectory(): Boolean = file.isDirectory
    override fun uri(): String = file.uri.toString()

    override fun readBytes(): ByteArray {
        return AndroidAppContextHolder.context.contentResolver.openInputStream(file.uri)
            ?.use { it.readBytes() }
            ?: ByteArray(0)
    }

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
    val exportDir = BaseDocumentTreeUtil.getDirectory(
        ctx,
        ctx.resources.getIdentifier(Res.string.dir_field_export.key, "string", ctx.packageName)
    )
    return exportDir?.let { AndroidDocumentFile(it) }
}

actual fun getArchiveDirectory(): DocumentFile? {
    val ctx = AndroidAppContextHolder.context
    val archiveDir = BaseDocumentTreeUtil.getDirectory(
        ctx,
        ctx.resources.getIdentifier(Res.string.dir_archive.key, "string", ctx.packageName)
    )
    return archiveDir?.let { AndroidDocumentFile(it) }
}

actual fun getTraitDirectory(): DocumentFile? {
    val ctx = AndroidAppContextHolder.context
    val traitDir = BaseDocumentTreeUtil.getDirectory(
        ctx,
        ctx.resources.getIdentifier(Res.string.dir_trait.key, "string", ctx.packageName)
    )
    return traitDir?.let { AndroidDocumentFile(it) }
}

actual fun getFieldImportDirectory(): DocumentFile? {
    val ctx = AndroidAppContextHolder.context
    val fieldImportDir = BaseDocumentTreeUtil.getDirectory(
        ctx,
        ctx.resources.getIdentifier(Res.string.dir_field_import.key, "string", ctx.packageName)
    )
    return fieldImportDir?.let { AndroidDocumentFile(it) }
}

actual fun listFiles(dir: DocumentFile): List<DocumentFile> {
    val androidDir = (dir as? AndroidDocumentFile)?.file ?: return emptyList()
    return androidDir.listFiles().map { AndroidDocumentFile(it) }
}

actual fun copyFileToDirectory(source: DocumentFile, destinationDir: DocumentFile, newFileName: String): DocumentFile? {
    val src = (source as? AndroidDocumentFile)?.file ?: return null
    val dest = (destinationDir as? AndroidDocumentFile)?.file ?: return null

    return if (src.isDirectory) {
        val newDir = dest.createDirectory(newFileName) ?: return null
        src.listFiles().forEach { child ->
            copyFileToDirectory(AndroidDocumentFile(child), AndroidDocumentFile(newDir), child.name ?: return@forEach)
        }
        AndroidDocumentFile(newDir)
    } else {
        val newFile = dest.createFile(src.type ?: "application/octet-stream", newFileName) ?: return null
        AndroidAppContextHolder.context.contentResolver.openInputStream(src.uri)?.use { ins ->
            AndroidAppContextHolder.context.contentResolver.openOutputStream(newFile.uri)?.use { outs ->
                ins.copyTo(outs)
            }
        }
        AndroidDocumentFile(newFile)
    }
}

actual fun zipFiles(files: List<DocumentFile>, zipFileName: String): DocumentFile? {
    val ctx = AndroidAppContextHolder.context
    val exportDir = getExportDirectory() as? AndroidDocumentFile ?: return null
    val zipFile = exportDir.file.createFile("application/zip", "$zipFileName.zip") ?: return null

    ctx.contentResolver.openOutputStream(zipFile.uri)?.use { outputStream ->
        ZipOutputStream(outputStream).use { zipOutput ->
            files.forEach { file ->
                addToZip(zipOutput, file, file.name().orEmpty())
            }
        }
    }

    return AndroidDocumentFile(zipFile)
}

private fun addToZip(zipOutput: ZipOutputStream, file: DocumentFile, entryName: String) {
    val androidFile = (file as? AndroidDocumentFile)?.file ?: return
    val safeEntryName = entryName.trim('/')

    if (androidFile.isDirectory) {
        val children = androidFile.listFiles()
        if (children.isEmpty()) {
            zipOutput.putNextEntry(ZipEntry("$safeEntryName/"))
            zipOutput.closeEntry()
        } else {
            children.forEach { child ->
                val childName = child.name ?: return@forEach
                addToZip(zipOutput, AndroidDocumentFile(child), "$safeEntryName/$childName")
            }
        }
        return
    }

    zipOutput.putNextEntry(ZipEntry(safeEntryName))
    AndroidAppContextHolder.context.contentResolver.openInputStream(androidFile.uri)?.use { input ->
        BufferedInputStream(input).copyTo(zipOutput)
    }
    zipOutput.closeEntry()
}

actual fun shareFile(file: DocumentFile) {
    val androidFile = (file as? AndroidDocumentFile)?.file ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = androidFile.type ?: "*/*"
        putExtra(Intent.EXTRA_STREAM, androidFile.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    AndroidAppContextHolder.context.startActivity(Intent.createChooser(intent, "Export").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

actual fun deleteFile(file: DocumentFile) {
    (file as? AndroidDocumentFile)?.file?.delete()
}

actual fun exportDeviceName(): String = Build.MODEL
