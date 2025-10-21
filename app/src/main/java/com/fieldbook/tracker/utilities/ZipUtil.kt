package com.fieldbook.tracker.utilities

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory


class ZipUtil {

    companion object {

        const val DATABASE_NAME = "fieldbook.db"

        /**
         * Improved zip function that handles folders recursively.
         * @param files: the array of string paths to zip
         * @param zipFile: the output file the zip is written to
         */
        @Throws(IOException::class)
        fun zip(ctx: Context, files: Array<DocumentFile>, zipFile: OutputStream?) {

            ZipOutputStream(BufferedOutputStream(zipFile)).use { output ->

                files.forEach { f ->

                    // no parents for files or directories present in root of the zip file
                    // send an empty list for parents
                    addZipEntry(ctx, output, f, arrayListOf())

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
        private fun addZipEntry(ctx: Context, output: ZipOutputStream, file: DocumentFile, parents: ArrayList<String>) {

            if (file.name?.startsWith(".") == true) return

            if (file.isDirectory) {

                val parent = parents.joinToString("/") { it }

                file.name?.let { fileName ->

                    parents.add(fileName)

                    writeZipEntry(ctx, output, file, parent)

                    file.listFiles().forEach { child ->

                        addZipEntry(ctx, output, child, parents)

                    }

                    parents.removeAt(parents.lastIndex)

                }

            } else writeZipEntry(ctx, output, file, parents.joinToString("/") { it })
        }

        private fun writeZipEntry(ctx: Context, output: ZipOutputStream, file: DocumentFile, parentDir: String) {

            try {

                if (!file.isDirectory) {

                    ctx.contentResolver?.openInputStream(file.uri)?.let { inputStream ->

                        // if no parent directory, add the current file to the root of the zip
                        // if there was a parent directory, add the current file inside parent directory
                        var entry : ZipEntry = if (parentDir.isEmpty()){
                            ZipEntry("${file.name}")
                        }else{
                            ZipEntry("$parentDir/${file.name}")
                        }

                        output.putNextEntry(entry)

                        val bufferSize = 8192 //default buffer size for BufferedWriter

                        val origin = BufferedInputStream(inputStream, bufferSize)

                        val data = ByteArray(bufferSize)

                        origin.use {

                            var count: Int

                            while (it.read(data, 0, bufferSize).also { count = it } != -1) {

                                output.write(data, 0, count)

                            }
                        }

                        inputStream.close()

                        output.closeEntry()
                    }

                } else {

                    val path = file.name

                    // if no parent directory, add the current directory to the root of the zip
                    // if there was a parent directory, add the current directory inside parent directory
                    var entry : ZipEntry = if (parentDir.isEmpty()){
                        ZipEntry("$path/")
                    }else{
                        ZipEntry("$parentDir/$path/")
                    }

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
        //  a. read and converted to a map <string to any (which is int, boolean or string)>
        //  b. preferences should be cleared of the old values
        //  c. iterate over the converted map and populate the preferences
        @Throws(IOException::class)
        fun unzip(ctx: Context, zipFile: InputStream?, databaseStream: OutputStream, isSampleDb: Boolean) {

            try {

                ZipInputStream(zipFile).use { zin ->

                    var ze: ZipEntry? = null

                    while (zin.nextEntry.also { ze = it } != null) {

                        // Earlier, for creating a zip file, Field Book created an
                        // Output folder which was placed at the root of the zip file.
                        // Files and folders were stored inside of this Output folder

                        // For fixing Issue 878, the zip file stopped including
                        // `Output` folder from being in the zip file.
                        // But the code below still involves "Output" to keep the
                        // unzip functionality working with files that contained
                        // both, the presence or the absence of the Output folder at the root
                        Log.d("ZipUtil", "Unzip - Processing file: ${ze?.name}")

                        when (ze?.name) {

                            "Output/" -> continue

                            "Output/$DATABASE_NAME", DATABASE_NAME -> {

                                databaseStream.use { output ->

                                    zin.copyTo(output)

                                }
                            }

                            null -> throw IOException()

                            else -> {

                                // only process .db files (not .xml) for sample_db.zip
                                if (isSampleDb) {
                                    Log.d("ZipUtil", "Skip processing ${ze.name} for sample_db.zip")
                                    continue
                                }

                                var prefMap: Map<*, *>

                                val zipEntry = ze.name

                                // if the preferences are stored in .xml file
                                if (zipEntry != null && zipEntry.endsWith(".xml")){
                                    val tempZipFile = File.createTempFile("temp", ".xml", ctx.cacheDir)
                                    tempZipFile.outputStream().use { output ->
                                        zin.copyTo(output)
                                    }
                                    // Create a DocumentFile from the temp file
                                    try{
                                        val documentFile = DocumentFile.fromFile(tempZipFile)
                                        prefMap = readXML(ctx, documentFile)
                                    } finally {
                                        if (tempZipFile.exists()){
                                            tempZipFile.delete()
                                        }
                                    }
                                } else{
                                    // if the preferences are encoded in a file
                                    Log.d("ZipUtil", "Unzip - Found encoded preference file: ${ze.name}")

                                    // Read the entry into a temporary byte array to avoid corrupting the ZipInputStream, then process it
                                    val tempData = zin.readBytes()
                                    ByteArrayInputStream(tempData).use { byteStream ->
                                        ObjectInputStream(byteStream).use { objectStream ->
                                            prefMap = objectStream.readObject() as Map<*, *>
                                        }
                                    }
                                }

                                updatePreferences(ctx, prefMap)
                            }
                        }
                    }

                    Log.d("ZipUtil", "Unzip - Completed processing all files.")
                }

            } catch (e: Exception) {

                Log.e("FileUtil", "Unzip exception", e)

            }
        }

        /**
         * Updates the preferences
         */
        fun updatePreferences(ctx: Context, prefMap: Map<*, *>) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            with (prefs.edit()) {

                Log.d("ZipUtil", "UpdatePreferences - Replacing existing preferences with preferences from file")
                clear()

                //keys are always string, do a quick map to type cast
                //put values into preferences based on their types
                prefMap.entries.map { it.key as String to it.value }
                    .forEach {

                        val key = it.first

                        when (val x = it.second) {

                            is Boolean -> putBoolean(key, x)

                            is String -> putString(key, x)

                            is Int -> putInt(key, x)

                            is Set<*> -> {

                                val newStringSet = hashSetOf<String>()
                                newStringSet.addAll(x.map { value -> value.toString() })
                                putStringSet(key, newStringSet)
                            }
                        }
                    }

                apply()
            }
        }

        /**
         * returns xml file as a map
         * @param prefDoc: DocumentFile object of preference.xml
         * @return Map<String></String>, ?>
         */
        fun readXML(ctx: Context, prefDoc: DocumentFile): Map<String, *> {
            val factory = DocumentBuilderFactory.newInstance()
            return try {
                val inputStream: InputStream? =
                    ctx.contentResolver.openInputStream(prefDoc.uri)
                val builder = factory.newDocumentBuilder()
                val document: Document = builder.parse(inputStream)
                document.documentElement.normalize()
                inputStream?.close()
                traverseNodes(document.documentElement)
            } catch (e: java.lang.Exception) {
                throw RuntimeException(e)
            }
        }

        /**
         * traverses the xml file to return the map
         */
        private fun traverseNodes(node: Element): Map<String, *> {
            val map: MutableMap<String, Any> = HashMap()
            val childNodes: NodeList = node.childNodes
            for (i in 0 until childNodes.length) {
                val childNode: Node = childNodes.item(i)
                if (childNode is Element) {
                    val childElement: Element = childNode

                    // the expected xml file has a maximum of four types of tags
                    // i.e. string, boolean, int, set
                    // handle each case
                    if (childElement.tagName
                            .equals("string") && childElement.hasAttribute("name")
                    ) {
                        val name: String = childElement.getAttribute("name")
                        val value: String = childElement.textContent.trim()
                        map[name] = value
                    } else if (childElement.tagName
                            .equals("boolean") && childElement.hasAttribute("name") && childElement.hasAttribute(
                            "value"
                        )
                    ) {
                        val name: String = childElement.getAttribute("name")
                        val value: Boolean = childElement.getAttribute("value").toBoolean()
                        map[name] = value
                    } else if (childElement.tagName.equals("int") && childElement.hasAttribute(
                            "name"
                        ) && childElement.hasAttribute("value")
                    ) {
                        val name: String = childElement.getAttribute("name")
                        val value: Int = childElement.getAttribute("value").toInt()
                        map[name] = value
                    } else if (childElement.tagName.equals("set") && childElement.hasAttribute(
                            "name"
                        )
                    ) {
                        val name: String = childElement.getAttribute("name")
                        val set: MutableSet<String> = HashSet()
                        val setChildNodes: NodeList = childElement.childNodes
                        for (j in 0 until setChildNodes.length) {
                            val setChildNode: Node = setChildNodes.item(j)
                            if (setChildNode is Element && setChildNode.tagName
                                    .equals("string")
                            ) {
                                set.add(setChildNode.textContent.trim())
                            }
                        }
                        map[name] = set
                    }
                }
            }
            return map
        }
    }
}