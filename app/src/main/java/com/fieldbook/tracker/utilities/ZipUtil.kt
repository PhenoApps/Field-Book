package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.util.Log
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipUtil {

    companion object {

        const val DATABASE_NAME = "fieldbook.db"

        /**
         * Improved zip function that handles folders recursively.
         * @param files: the array of string paths to zip
         * @param zipFile: the output file the zip is written to
         */
        @Throws(IOException::class)
        fun zip(files: Array<String>, zipFile: OutputStream?) {

            val parents = arrayListOf<String>()

            parents.add("Output")

            ZipOutputStream(BufferedOutputStream(zipFile)).use { output ->

                output.putNextEntry(ZipEntry("Output/"))

                for (i in files.indices) {

                    val file = File(files[i])

                    addZipEntry(output, file, parents)
                }
            }
        }

        /**
         * Adds the file as ZipEntry into the output parameter.
         * If the file is a directory, this function is called recursively on its children.
         *
         * @param output: the final zip output file
         * @param file: the directory or file to create a new zip entry
         */
        private fun addZipEntry(output: ZipOutputStream, file: File, parents: ArrayList<String>) {

            if (file.isHidden) return

            if (file.isDirectory) {

                val parent = parents.joinToString("/") { it }

                parents.add(file.name)

                writeZipEntry(output, file, parent)

                file.listFiles { dir, name ->

                    addZipEntry(output, File(dir, name), parents)

                    true
                }

                parents.removeLast()

            } else writeZipEntry(output, file, parents.joinToString("/") { it })
        }

        private fun writeZipEntry(output: ZipOutputStream, file: File, parentDir: String) {

            try {

                if (!file.isDirectory) {

                    val entry = ZipEntry("$parentDir/${file.name}")

                    output.putNextEntry(entry)

                    val bufferSize = 8192 //default buffersize for BufferedWriter

                    val fi = FileInputStream(file)

                    val origin = BufferedInputStream(fi, bufferSize)

                    val data = ByteArray(bufferSize)

                    origin.use {

                        var count: Int

                        while (it.read(data, 0, bufferSize).also { count = it } != -1) {

                            output.write(data, 0, count)

                        }
                    }

                    fi.close()

                    output.closeEntry()

                } else {

                    val path = file.name

                    val entry = ZipEntry("$parentDir/$path/")

                    output.putNextEntry(entry)

                    output.closeEntry()
                }

            } catch (io: IOException) {

                io.printStackTrace()
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