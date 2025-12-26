package com.fieldbook.shared.utilities

interface DocumentFile {

    fun createDirectory(name: String): DocumentFile?
    fun createFile(mimeType: String, name: String): DocumentFile?
    fun findFile(name: String): DocumentFile?
    fun exists(): Boolean

    fun uri(): String
    fun writeBytes(byteArray: ByteArray)
}

expect fun createDir(parent: String, child: String): DocumentFile?
