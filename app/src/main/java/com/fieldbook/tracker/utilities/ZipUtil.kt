package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.net.Uri
import android.util.Log
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipUtil {

    companion object {

        const val DATABASE_NAME = "fieldbook.db"

        //reference https://stackoverflow.com/questions/7485114/how-to-zip-and-unzip-the-files
        @Throws(IOException::class)
        fun zip(files: Array<String>, zipFile: OutputStream?) {

            ZipOutputStream(BufferedOutputStream(zipFile)).use { output ->

                var origin: BufferedInputStream? = null

                val bufferSize = 8192 //default buffersize for BufferedWriter
                val data = ByteArray(bufferSize)

                for (i in files.indices) {

                    val fi = FileInputStream(files[i])

                    origin = BufferedInputStream(fi, bufferSize)

                    try {

                        val entry = ZipEntry(files[i].substring(files[i].lastIndexOf("/") + 1))

                        output.putNextEntry(entry)

                        var count: Int

                        while (origin.read(data, 0, bufferSize).also { count = it } != -1) {

                            output.write(data, 0, count)

                        }
                    } finally {

                        origin.close()

                    }
                }
            }
        }

        //the expected zip file format contains two files
        //1. fieldbook.db this can be directly copied to the data dir
        //2. preferences_backup needs to:
        //  a. read and converted to a map <string to any (which is only boolean or string)>
        //  b. preferences should be cleared of the old values
        //  c. iterate over the converted map and populate the preferences
        @Throws(IOException::class)
        fun unzip(ctx: Context, zipFile: InputStream?, databaseStream: OutputStream) {

            try {

                ZipInputStream(zipFile).use { zin ->

                    var ze: ZipEntry? = null

                    while (zin.nextEntry.also { ze = it } != null) {

                        when (ze?.name) {

                            null -> throw IOException()

                            DATABASE_NAME -> {

                                databaseStream.use { output ->

                                    zin.copyTo(output)

                                }
                            }

                            else -> {

                                val prefs = ctx.getSharedPreferences("Settings", MODE_PRIVATE)

                                ObjectInputStream(zin).use { objectStream ->

                                    val prefMap = objectStream.readObject() as Map<*, *>

                                    with (prefs.edit()) {

                                        clear()

                                        //keys are always string, do a quick map to type cast
                                        //put values into preferences based on their types
                                        prefMap.entries.map { it.key as String to it.value }
                                            .forEach {

                                                val key = it.first

                                                when (val x = it.second) {

                                                    is Boolean -> putBoolean(key, x)

                                                    is String -> putString(key, x)
                                                }
                                            }

                                        apply()
                                    }
                                }
                            }
                        }
                    }
                }

            } catch (e: Exception) {

                Log.e("FileUtil", "Unzip exception", e)

            }
        }
    }
}