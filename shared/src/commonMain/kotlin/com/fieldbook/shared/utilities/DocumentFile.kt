package com.fieldbook.shared.utilities

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

expect fun getDirectory(directory: StringResource): DocumentFile?
expect fun listFiles(dir: DocumentFile): List<DocumentFile>
expect fun copyFileToDirectory(source: DocumentFile, destinationDir: DocumentFile, newFileName: String): DocumentFile?
expect fun zipFiles(files: List<DocumentFile>, zipFileName: String): DocumentFile?
expect fun shareFile(file: DocumentFile)
expect fun deleteFile(file: DocumentFile)
expect fun exportDeviceName(): String
