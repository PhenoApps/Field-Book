package com.fieldbook.tracker.utilities

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.fieldbook.tracker.R
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.UnsupportedOperationException

@RequiresApi(Build.VERSION_CODES.KITKAT)
class DocumentTreeUtil {

    /**
     * Static functions to be used to handle exports.
     * These functions will attempt to create these directories if they do not exist.
     */
    companion object {

        const val TAG = "DocumentTreeUtil"

        /**
         * Creates the root directory structure for FB. Also copies assets over to the defined
         * structure.
         */
        fun DocumentFile.createFieldBookFolders(context: Context?) {
            context?.let { ctx ->
                arrayOf(ctx.getString(R.string.dir_archive), ctx.getString(R.string.dir_database),
                    ctx.getString(R.string.dir_field_export), ctx.getString(R.string.dir_field_import),
                    ctx.getString(R.string.dir_geonav), ctx.getString(R.string.dir_plot_data),
                    ctx.getString(R.string.dir_resources), ctx.getString(R.string.dir_trait),
                    ctx.getString(R.string.dir_updates)
                ).forEach { dir ->

                    val dirFile = findFile(dir)
                    if (dirFile == null || !dirFile.exists()) {
                        createDirectory(dir)
                    }
                }

                copyAsset(ctx, "field_import", R.string.dir_field_import)
                copyAsset(ctx, "trait", R.string.dir_trait)
                copyAsset(ctx, "database", R.string.dir_database)
            }
        }

        /**
         * Creates a media directory for a given plot, media directories contain photos and audio folders.
         */
        fun createFieldDir(context: Context?, fieldFileName: String) {

            context?.let { ctx ->

                getDirectory(ctx, R.string.dir_plot_data)?.let { dir ->

                    if (dir.exists()) {

                        dir.getOrCreate(fieldFileName)?.let { fieldDir ->

                            val photos = ctx.getString(R.string.dir_media_photos)
                            val audio = ctx.getString(R.string.dir_media_audio)
                            val thumbnails = ctx.getString(R.string.hidden_file_thumbnails)

                            val photosDir = fieldDir.getOrCreate(photos)
                            val audioDir = fieldDir.getOrCreate(audio)
                            val thumbnailsDir = photosDir?.getOrCreate(thumbnails)

                            photosDir?.logDirectoryExists(ctx, photos)
                            audioDir?.logDirectoryExists(ctx, audio)
                            thumbnailsDir?.logDirectoryExists(ctx, thumbnails)
                        }
                    }
                }
            }
        }

        /**
         * Copies one document file to another.
         */
        fun copy(context: Context?, from: DocumentFile?, to: DocumentFile?) {
            context?.let { ctx ->

                try {

                    to?.let { toFile ->
                        from?.let { fromFile ->
                            with (ctx.contentResolver) {
                                openOutputStream(toFile.uri)?.let { output ->
                                    openInputStream(fromFile.uri)?.copyTo(output)
                                }
                            }
                        }
                    }

                } catch (f: FileNotFoundException) {

                    f.printStackTrace()

                }
            }
        }

        /**
         * Returns a document file based on the FB directory and a filename.
         */
        fun getFile(context: Context?, dir: Int, fileName: String): DocumentFile? {

            context?.let { ctx ->

                getDirectory(ctx, dir)?.let { dir ->

                    dir.findFile(fileName)?.let { file ->

                        return file
                    }
                }
            }

            return null
        }

        /**
         * Returns the input stream for a given uri
         */
        fun getUriInputStream(context: Context?, uri: Uri): InputStream? {

            try {

                return context?.contentResolver?.openInputStream(uri)

            } catch (e: FileNotFoundException) {

                e.printStackTrace()

            }

            return null
        }

        /**
         * Returns the input stream for a given file
         */
        fun getFileInputStream(context: Context?, dir: Int, fileName: String): InputStream? {

            try {

                getFile(context, dir, fileName)?.let { file ->

                    return context?.contentResolver?.openInputStream(file.uri)

                }

            } catch (e: FileNotFoundException) {

                e.printStackTrace()

            }

            return null
        }

        /**
         * Returns an output stream for the given file.
         */
        fun getFileOutputStream(context: Context?, dir: Int, fileName: String): OutputStream? {

            try {

                getFile(context, dir, fileName)?.let { file ->

                    return context?.contentResolver?.openOutputStream(file.uri)
                }

            } catch (e: FileNotFoundException) {

                e.printStackTrace()

            }

            return null
        }

        /**
         * Returns one of the main sub directories within FB s.a field_import, field_export
         * All string id's are shown in res/values/directories.xml
         */
        fun getDirectory(context: Context?, id: Int): DocumentFile? {

            context?.let { ctx ->

                val directoryName = ctx.getString(id)

                return createDir(ctx, directoryName)
            }

            return null
        }

        /**
         * Gets a specific directory for the currently chosen plot. (s.a audio or photos)
         */
        fun getFieldMediaDirectory(context: Context?, trait: String): DocumentFile? {

            if (context != null) {

                val prefs = context.getSharedPreferences("Settings", 0)
                val field = prefs.getString(PrefsConstants.FIELD_FILE, "") ?: ""

                if (field.isNotBlank()) {
                    val plotDataDirName = context.getString(R.string.dir_plot_data)
                    val fieldDir = createDir(context, plotDataDirName, field)
                    if (fieldDir != null) {
                        val photosDir = fieldDir.findFile(trait)
                        if (photosDir == null || !photosDir.exists()) {
                            fieldDir.createDirectory(trait)
                        }
                        return fieldDir.findFile(trait)
                    }
                } else return null
            }

            return null
        }

        /**
         * Gets plot media for a given plot based on its extension
         */
        fun getPlotMedia(mediaDir: DocumentFile?, plot: String, ext: String): List<DocumentFile> {

            return getPlotMedia(mediaDir, plot).filter { it.name?.endsWith(ext) == true }
        }

        /**
         * Checks whether a persisted uri has been saved; therefore, defined by the user.
         */
        fun isEnabled(ctx: Context): Boolean {
            val persists = ctx.contentResolver.persistedUriPermissions
            return if (persists.isNotEmpty()) {
                DocumentFile.fromTreeUri(ctx, persists[0].uri)?.exists() == true
            } else false
        }

        /**
         * Creates a file at the given parent directory with the given name
         */
        fun createFile(ctx: Context, parent: String, name: String): DocumentFile? {

            val dir = createDir(ctx, parent)
            return dir?.createFile("*/*", name)

        }

        /**
         * Function that checks if the persisted folder exists.
         * If it does not exist, show a dialog asking the user to define it.
         * @param ctx the calling context
         * @param function the callback, true if the user selects to define a storage
         */
        fun checkDir(ctx: Context?, function: (Boolean) -> Unit) {

            var persisted = false

            ctx?.contentResolver?.persistedUriPermissions?.let { perms ->

                if (perms.isNotEmpty()) {

                    perms.first()?.uri?.let { uri ->

                        persisted = DocumentFile.fromTreeUri(ctx, uri)?.exists() ?: false

                    }
                }
            }

            if (!persisted) {

                AlertDialog.Builder(ctx)
                    .setNegativeButton(android.R.string.no) { dialog, which ->

                        dialog.dismiss()

                        function(false)

                    }
                    .setPositiveButton(android.R.string.yes) { dialog, which ->

                        dialog.dismiss()

                        function(true)
                    }
                    .setTitle(R.string.document_tree_undefined)
                    .create()
                    .show()

            } else {

                function(false)
            }
        }

        /**
         * Obtains the root FB directory defined in storage definer
         */
        fun getRoot(context: Context?): DocumentFile? {
            context?.let { ctx ->
                val persists = ctx.contentResolver.persistedUriPermissions
                if (persists.isNotEmpty()) {
                    val uri = persists.first().uri
                    return DocumentFile.fromTreeUri(ctx, uri)
                }
            }
            return null
        }

        /**
         * Function to copy asset files to SAF dir
         */
        private fun copyAsset(context: Context?, assetDir: String, dirId: Int) {

            context?.let { ctx ->

                try {

                    ctx.assets.list(assetDir)?.forEach { file ->

                        getDirectory(ctx, dirId)?.let { dir ->

                            if (dir.exists()) {

                                try {

                                    if (dir.findFile(file)?.exists() != true) {
                                        dir.createFile("*/*", file)?.let {

                                            context.contentResolver.openOutputStream(it.uri)?.let { outputStream ->
                                                context.assets.open("$assetDir/$file").copyTo(outputStream)
                                            }
                                        }
                                    }

                                } catch (e: UnsupportedOperationException) {

                                    e.printStackTrace()

                                } catch (f: FileNotFoundException) {

                                    f.printStackTrace()

                                } catch (io: IOException) {

                                    io.printStackTrace()

                                }
                            }
                        }
                    }

                } catch (fnf: FileNotFoundException) {

                    fnf.printStackTrace()

                }
            }
        }

        /**
         * Used to create or return a document file by name.
         */
        private fun DocumentFile.getOrCreate(name: String): DocumentFile? {

            try {

                val dir = findFile(name)
                if (dir == null || !dir.exists()) {
                    return createDirectory(name)
                }

            } catch (e: UnsupportedOperationException) {

                e.printStackTrace()

            }


            return null
        }

        /**
         * Logs whether or not the file exists
         */
        private fun DocumentFile?.logDirectoryExists(ctx: Context, name: String) {

            if (this == null || !exists()) {

                Log.d(TAG, ctx.getString(R.string.error_dtu_dir_not_created, name))
            }
        }

        /**
         * Returns an array of files that have been collected for a plot.
         * Field Book media file names always contain the plot name, which are unique to the study.
         */
        private fun getPlotMedia(mediaDir: DocumentFile?, plot: String): ArrayList<DocumentFile> {

            val mediaList = arrayListOf<DocumentFile>()

            try {

                mediaDir?.listFiles()?.forEach { file ->
                    if (file.exists()) {
                        file?.name?.let { fileName ->
                            if (plot in fileName) {
                                mediaList.add(file)
                            }
                        }
                    }
                }

            } catch (e: UnsupportedOperationException) {

                e.printStackTrace()

            }


            return mediaList
        }

        /**
         * Creates a child directory within a parent directory.
         */
        private fun createDir(ctx: Context, parent: String, child: String): DocumentFile? {

            val file = createDir(ctx, parent)
            return if (file?.findFile(child)?.isDirectory == true) {
                file.findFile(child)
            } else file?.createDirectory(child)
        }

        /**
         * Finds the persisted uri and creates the basic coordinate file structure if it doesn't exist.
         */
        private fun createDir(ctx: Context, parent: String): DocumentFile? {
            val persists = ctx.contentResolver.persistedUriPermissions
            if (persists.isNotEmpty()) {
                val uri = persists.first().uri
                DocumentFile.fromTreeUri(ctx, uri)?.let { tree ->
                    var exportDir = tree.findFile(parent)
                    if (exportDir == null) {
                        exportDir = tree.createDirectory(parent)
                    }

                    return exportDir
                }
            }

            return null
        }
    }
}