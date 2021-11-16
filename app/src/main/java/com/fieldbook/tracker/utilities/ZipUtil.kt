package com.fieldbook.tracker.utilities

import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipUtil {

    companion object {

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

            } else try {

                val bufferSize = 8192 //default buffersize for BufferedWriter

                val fi = FileInputStream(file)

                val origin = BufferedInputStream(fi, bufferSize)

                val data = ByteArray(bufferSize)

                origin.use {

                    val path = file.path

                    val entry = ZipEntry(path.substring(path.lastIndexOf("/") + 1))

                    output.putNextEntry(entry)

                    var count: Int

                    while (it.read(data, 0, bufferSize).also { count = it } != -1) {

                        output.write(data, 0, count)

                    }
                }
            } catch (io: IOException) {

                io.printStackTrace()

            }
        }
    }
}