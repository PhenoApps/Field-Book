package com.fieldbook.shared.utilities

private class IosDocumentFile : DocumentFile {
    override fun createDirectory(name: String): DocumentFile? = TODO("Not yet implemented")
    override fun createFile(mimeType: String, name: String): DocumentFile? = TODO("Not yet implemented")
    override fun findFile(name: String): DocumentFile? = TODO("Not yet implemented")
    override fun exists(): Boolean = TODO("Not yet implemented")
    override fun isDirectory(): Boolean = TODO("Not yet implemented")
    override fun uri(): String = TODO("Not yet implemented")
    override fun readBytes(): ByteArray = TODO("Not yet implemented")
    override fun writeBytes(byteArray: ByteArray) = TODO("Not yet implemented")
    override fun name(): String? = TODO("Not yet implemented")
}

actual fun createDir(parent: String, child: String): DocumentFile? = TODO("Not yet implemented")
actual fun getExportDirectory(): DocumentFile? = TODO("Not yet implemented")
actual fun getArchiveDirectory(): DocumentFile? = TODO("Not yet implemented")
actual fun getTraitDirectory(): DocumentFile? = TODO("Not yet implemented")
actual fun listFiles(dir: DocumentFile): List<DocumentFile> = TODO("Not yet implemented")
actual fun copyFileToDirectory(source: DocumentFile, destinationDir: DocumentFile, newFileName: String): DocumentFile? =
    TODO("Not yet implemented")
actual fun zipFiles(files: List<DocumentFile>, zipFileName: String): DocumentFile? = TODO("Not yet implemented")
actual fun shareFile(file: DocumentFile) = TODO("Not yet implemented")
actual fun deleteFile(file: DocumentFile) = TODO("Not yet implemented")
actual fun exportDeviceName(): String = "iOS"
