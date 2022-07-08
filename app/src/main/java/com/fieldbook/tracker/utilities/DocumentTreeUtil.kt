package com.fieldbook.tracker.utilities

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.lang.UnsupportedOperationException

@RequiresApi(Build.VERSION_CODES.KITKAT)
class DocumentTreeUtil: BaseDocumentTreeUtil() {

    /**
     * Static functions to be used to handle exports.
     * These functions will attempt to create these directories if they do not exist.
     */
    companion object {

        const val TAG = "DocumentTreeUtil"

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
         * Gets a specific directory for the currently chosen plot.
         * @param format: trait format, either photos or audio
         */
        fun getFieldMediaDirectory(context: Context?, format: String): DocumentFile? {

            if (context != null) {

                val prefs = context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0)
                val field = prefs.getString(GeneralKeys.FIELD_FILE, "") ?: ""

                if (field.isNotBlank()) {
                    val plotDataDirName = context.getString(R.string.dir_plot_data)
                    val fieldDir = createDir(context, plotDataDirName, field)
                    if (fieldDir != null) {
                        val photosDir = fieldDir.findFile(format)
                        if (photosDir == null || !photosDir.exists()) {
                            fieldDir.createDirectory(format)
                        }
                        return fieldDir.findFile(format)
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

        fun getTraitMediaDir(context: Context?, trait: String, format: String): DocumentFile? {

            var traitDir: DocumentFile? = null

            getFieldMediaDirectory(context, format)?.let { mediaDir ->

                traitDir = mediaDir.findFile(trait)

                if (traitDir == null) {

                    traitDir = mediaDir.createDirectory(trait)

                }
            }

            return traitDir
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
    }
}