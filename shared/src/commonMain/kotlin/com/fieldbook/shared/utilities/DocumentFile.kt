package com.fieldbook.shared.utilities

import no.synth.kmpzip.io.ByteArrayOutputStream
import no.synth.kmpzip.zip.ZipEntry
import no.synth.kmpzip.zip.ZipOutputStream
import org.jetbrains.compose.resources.StringResource

interface DocumentFile {
    fun createDirectory(name: String): DocumentFile?
    fun createFile(mimeType: String, name: String): DocumentFile?
    fun findFile(name: String): DocumentFile?
    fun exists(): Boolean
    fun isDirectory(): Boolean

    fun uri(): String
    fun readBytes(): ByteArray
    fun writeBytes(byteArray: ByteArray)
    fun name(): String?
}

expect fun createDir(parent: String, child: String): DocumentFile?
expect fun getFileByPath(path: String): DocumentFile?

expect fun getDirectory(directory: StringResource): DocumentFile?
expect fun listFiles(dir: DocumentFile): List<DocumentFile>
expect fun copyFileToDirectory(source: DocumentFile, destinationDir: DocumentFile, newFileName: String): DocumentFile?
expect fun shareFile(file: DocumentFile)
expect fun deleteFile(file: DocumentFile)
expect fun exportDeviceName(): String

fun zipFiles(files: List<DocumentFile>, destinationDir: DocumentFile, zipFileName: String): DocumentFile? {
    val outputName = if (zipFileName.endsWith(".zip")) zipFileName else "$zipFileName.zip"
    val zipFile = destinationDir.createFile("application/zip", outputName) ?: return null
    val zipBytes = ByteArrayOutputStream().use { byteOutput ->
        ZipOutputStream(byteOutput).use { zipOutput ->
            files.forEach { file ->
                addToZip(zipOutput, file, file.name().orEmpty())
            }
        }
        byteOutput.toByteArray()
    }
    zipFile.writeBytes(zipBytes)
    return zipFile
}

private fun addToZip(zipOutput: ZipOutputStream, file: DocumentFile, entryName: String) {
    val safeEntryName = entryName.trim('/').takeIf { it.isNotBlank() } ?: return

    if (file.isDirectory()) {
        val children = listFiles(file)
        if (children.isEmpty()) {
            zipOutput.putNextEntry(ZipEntry("$safeEntryName/"))
            zipOutput.closeEntry()
        } else {
            children.forEach { child ->
                val childName = child.name() ?: return@forEach
                addToZip(zipOutput, child, "$safeEntryName/$childName")
            }
        }
        return
    }

    val bytes = file.readBytes()
    zipOutput.putNextEntry(ZipEntry(safeEntryName))
    zipOutput.write(bytes, 0, bytes.size)
    zipOutput.closeEntry()
}
