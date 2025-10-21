package com.fieldbook.tracker.utilities

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.io.IOException
import java.io.ObjectOutputStream
import kotlin.jvm.Throws

@RequiresApi(Build.VERSION_CODES.KITKAT)
class DocumentTreeUtil: BaseDocumentTreeUtil() {

    /**
     * Static functions to be used to handle exports.
     * These functions will attempt to create these directories if they do not exist.
     */
    companion object {

        const val TAG = "DocumentTreeUtil"

        const val FIELD_AUDIO_MEDIA = "field_audio"
        const val FIELD_GNSS_LOG = "field_gnss_log"
        const val FIELD_GNSS_LOG_FILE_NAME = "field_gnss.csv"

        /**
         * Gets a specific directory for the currently chosen plot.
         * @param traitName: trait name of the folder, also photos and audio
         */
        fun getFieldMediaDirectory(context: Context?, traitName: String): DocumentFile? {

            if (context != null) {

                val sanitizedTraitName = FileUtil.sanitizeFileName(traitName)

                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val field = prefs.getString(GeneralKeys.FIELD_FILE, "") ?: ""

                if (field.isNotBlank()) {
                    val plotDataDirName = context.getString(R.string.dir_plot_data)
                    val fieldDir = createDir(context, plotDataDirName, field)
                    if (fieldDir != null) {
                        var traitDir = fieldDir.findFile(sanitizedTraitName)
                        if (traitDir == null || !traitDir.exists()) {
                            fieldDir.createDirectory(sanitizedTraitName)
                        }
                        traitDir = fieldDir.findFile(sanitizedTraitName)
                        if (traitDir != null && traitDir.findFile(".nomedia")?.exists() != true) {
                            traitDir.createFile("*/*", ".nomedia")
                        }
                        return traitDir
                    }
                } else return null
            }

            return null
        }

        /**
         * Gets a specific directory for the currently chosen field.
         * @param attributeName: attribute name of the folder
         * currently used for field_audio, can be used in future for other types od field data
         */
        fun getFieldDataDirectory(context: Context?, attributeName: String): DocumentFile? {

            if (context != null) {

                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val field = prefs.getString(GeneralKeys.FIELD_FILE, "") ?: ""

                if (field.isNotBlank()) {
                    val fieldDataDirName = context.getString(R.string.dir_field_data)
                    val fieldDir = createDir(context, fieldDataDirName, field)
                    if (fieldDir != null) {
                        var attributeDir = fieldDir.findFile(attributeName)
                        if (attributeDir == null || !attributeDir.exists()) {
                            fieldDir.createDirectory(attributeName)
                        }
                        attributeDir = fieldDir.findFile(attributeName)
                        if (attributeDir != null && attributeDir.findFile(".nomedia")?.exists() != true) {
                            attributeDir.createFile("*/*", ".nomedia")
                        }
                        return attributeDir
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
         * Export preferences by encoding in a file in the preferences directory
         *
         * @param context Context
         * @param filename Name of the preferences file to create
         * @return DocumentFile
         * @throws IOException If file operations fail
         */
        @Throws(IOException::class)
        fun exportPreferences(context: Context?, filename: String, exportOnlySettingsKeys: Boolean): DocumentFile? {
            try {
                context?.let { ctx ->
                    val preferencesDir = getDirectory(ctx, R.string.dir_preferences)
                    val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)

                    if (preferencesDir != null) {
                        val prefDoc = preferencesDir.findFile(filename)
                        if (prefDoc != null && prefDoc.exists()) {
                            prefDoc.delete()
                        }

                        val preferenceFile = preferencesDir.createFile("*/*", filename)

                        if (preferenceFile != null) {
                            val tempStream = getFileOutputStream(ctx, R.string.dir_preferences,filename)
                            val objectStream = ObjectOutputStream(tempStream)
                            objectStream.writeObject(if (exportOnlySettingsKeys) getSettingsPreferences(prefs.all) else prefs.all)

                            objectStream.close()
                            tempStream?.close()
                            return preferenceFile
                        }
                    }
                }
                return null
            } catch (e: Exception) {
                e.message?.let { Log.e(TAG, it) }
                throw IOException("Failed to export preferences", e)
            }
        }

        /**
         * Helper method to extract only the preferences defined in PreferenceKeys class
         * @return Map containing only preferences that belong to settings
         */
        private fun getSettingsPreferences(allPrefs: MutableMap<String, *>): Map<String, Any?> {
            val settingsPrefs: MutableMap<String, Any?> = HashMap()
            val preferenceKeys: Set<String> = PreferenceKeys.SETTINGS_KEYS

            Log.d(TAG, "Found " + preferenceKeys.size + " settings keys")

            for (key in preferenceKeys) {
                if (allPrefs.containsKey(key)) {
                    settingsPrefs[key] = allPrefs[key]
                }
            }

            return settingsPrefs
        }
    }
}