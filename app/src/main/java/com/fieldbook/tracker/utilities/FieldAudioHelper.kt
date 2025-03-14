package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaRecorder
import android.net.Uri
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.interfaces.TraitCsvWriter
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.DocumentTreeUtil.Companion.getFieldDataDirectory
import com.fieldbook.tracker.utilities.DocumentTreeUtil.Companion.getFieldMediaDirectory
import com.fieldbook.tracker.utilities.ZipUtil.Companion.zip
import dagger.hilt.android.qualifiers.ActivityContext
import org.phenoapps.utils.BaseDocumentTreeUtil.Companion.getDirectory
import org.phenoapps.utils.BaseDocumentTreeUtil.Companion.getFileOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject


/**
 * Helper class for recording field level audio
 * Used in collect activity.
 *
 * Stores the
 *      file audio in /field_sample/field_audio/
 *      trait audio in /field_sample/audio/
 *
 * Field level audio and log file together can be used
 * for digital phenotyping
 *
 * AudioTraitLayout also uses methods from this helper
 *
 * Zips field audio, all traits and geonav log files in /field_export/
 */

class FieldAudioHelper @Inject constructor(@ActivityContext private val context: Context) : TraitCsvWriter {

    private var mediaRecorder: MediaRecorder? = null

    private var recordingLocation: Uri? = null
    private val mPrefs = PreferenceManager.getDefaultSharedPreferences(context)

    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    private var buttonState = ButtonState.WAITING_FOR_RECORDING

    val isRecording: Boolean
        get() = buttonState != ButtonState.WAITING_FOR_RECORDING

    fun startRecording(isFieldAudio: Boolean = true) {
        try {
            prepareRecorder(isFieldAudio)
            if (mediaRecorder != null) {
                mediaRecorder?.start()
                buttonState = ButtonState.RECORDING
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun zipFiles(ctx: Context, paths: ArrayList<DocumentFile?>, outputStream: OutputStream) {
        try {
            zip(ctx, paths.toArray(arrayOf()), outputStream)
            outputStream.close()
        } catch (io: IOException) {
            io.printStackTrace()
        }
    }

    /**
     * This function zips field audio, geonav log and all traits files
     */
    private fun zipAudioLogAndTraits(){
        try {
            val timeStamp = SimpleDateFormat(
                "yyyy-MM-dd-hh-mm-ss", Locale.getDefault()
            )
            val c = Calendar.getInstance()
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val fieldAlias = prefs.getString(GeneralKeys.FIELD_FILE, "")
            val traitFileName: String = "all_traits_" + timeStamp.format(c.time)    + ".csv"

            val audioDocumentFile = recordingLocation?.let {
                DocumentFile.fromSingleUri(context,
                    it
                )
            }

            // create a all traits csv file in /traits/
            val traitsDocument = writeAllTraitsToCsv(traitFileName, (context as CollectActivity))
            val traitsDocumentFile = traitsDocument?.let {
                DocumentFile.fromSingleUri(context,
                    it
                )
            }

            var geoNavLogWriter = context.getGeoNavHelper().getGeoNavLogLimitedUri()
            val limitedGeoNavFile = geoNavLogWriter?.let {
                DocumentFile.fromSingleUri(context,
                    it
                )
            }

            geoNavLogWriter = context.getGeoNavHelper().getGeoNavLogFullUri()
            val fullGeoNavFile = geoNavLogWriter?.let {
                DocumentFile.fromSingleUri(context,
                    it
                )
            }

            val paths = ArrayList<DocumentFile?>()
            paths.add(audioDocumentFile)
            if(limitedGeoNavFile != null)
                paths.add(limitedGeoNavFile)
            if(fullGeoNavFile != null)
                paths.add(fullGeoNavFile)
            paths.add(traitsDocumentFile)

            val mGeneratedName = "field_audio_log" + context.cRange.uniqueId + "_" + fieldAlias + " " + timeStamp.format(c.time)    + ".zip"

            val exportDir = getDirectory(context, R.string.dir_field_export)
            val zipFile = exportDir?.createFile("*/*", mGeneratedName)

            val output = getFileOutputStream(
                context, R.string.dir_field_export, mGeneratedName
            )

            if(output != null){
                zipFiles(context, paths, output)
            }
        }catch (e : Exception){
            e.printStackTrace()
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.stop()
            buttonState = ButtonState.WAITING_FOR_RECORDING
            releaseRecorder()
            // zip the field audio and log file only if logging is enabled
            val isLoggingEnabled = mPrefs.getString(PreferenceKeys.GEONAV_LOGGING_MODE, "0")
            if(isLoggingEnabled != "0"){
                zipAudioLogAndTraits()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // used in AudioTraitLayout
    fun getRecordingLocation(): Uri? {
        return recordingLocation
    }

    private fun setRecordingLocation(recordingName: String, isFieldAudio: Boolean) {

        val traitName = (context as CollectActivity).traitName
        val sanitizedTraitName = FileUtil.sanitizeFileName(traitName)

        // get directory based on type of audio being recorded
        val audioDir = if (isFieldAudio) getFieldDataDirectory(
            context, DocumentTreeUtil.FIELD_AUDIO_MEDIA
        ) else getFieldMediaDirectory(
            context, sanitizedTraitName
        )
        if (audioDir != null && audioDir.exists()) {
            val audioFile = audioDir.createFile("*/mp4", "$recordingName.mp4")
            if (audioFile != null) {
                recordingLocation = audioFile.uri
            }
        }
    }

    // Make sure we're not recording music playing in the background; ask the
    // MediaPlaybackService to pause playback
    private fun stopAllAudioForPlayback() {
        val i = Intent("com.android.music.musicservicecommand")
        i.putExtra("command", "pause")
        context.sendBroadcast(i)
    }

    // Reset the recorder to default state so it can begin recording
    private fun prepareRecorder(isFieldAudio: Boolean) {
        stopAllAudioForPlayback()
        mediaRecorder = MediaRecorder()
        mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
        val timeStamp = SimpleDateFormat(
            "yyyy-MM-dd-hh-mm-ss", Locale.getDefault()
        )
        val c = Calendar.getInstance()
        val mGeneratedName: String
        val fieldAlias = preferences.getString(GeneralKeys.FIELD_FILE, "")
        mGeneratedName = try {
            if (isFieldAudio) "field_audio_" + (context as CollectActivity).cRange.uniqueId + "_" + fieldAlias + " " + timeStamp.format(
                c.time
            )
            else (context as CollectActivity).cRange.uniqueId + " " + timeStamp.format(c.time)
        } catch (e: Exception) {
            "error " + timeStamp.format(c.time)
        }
        setRecordingLocation(mGeneratedName, isFieldAudio)
        try {
            val fd = recordingLocation?.let {
                context.contentResolver.openFileDescriptor(
                    it,
                    "rw"
                )
            }?.fileDescriptor
            mediaRecorder?.setOutputFile(fd)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
        try {
            mediaRecorder?.prepare()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // Remove the recorder resource
    private fun releaseRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder?.release()
        }
    }

    private enum class ButtonState(private val imageId: Int) {
        WAITING_FOR_RECORDING(R.drawable.ic_tb_field_mic_off), RECORDING(R.drawable.ic_tb_field_mic_on);
    }

    override fun writeAllTraitsToCsv(traitFileName: String, context: Context): Uri? {
        val collectActivityContext = context as CollectActivity

        try {
            val traitDir = getDirectory(context, R.string.dir_trait)
            if (traitDir != null && traitDir.exists()) {
                val exportDoc = traitDir.createFile("*/*", traitFileName)
                if (exportDoc != null && exportDoc.exists()) {
                    val output =
                        getFileOutputStream(context, R.string.dir_trait, traitFileName)
                    if (output != null) {
                        var osw: OutputStreamWriter? = null
                        var csvWriter: CSVWriter? = null
                        try {
                            osw = OutputStreamWriter(output)
                            csvWriter = CSVWriter(
                                osw,
                                collectActivityContext.getDatabase().allTraitObjectsForExport
                            )
                            csvWriter.writeTraitFile(collectActivityContext.getDatabase().allTraitsForExport?.columnNames)
                        } catch (e: java.lang.Exception) {
                            e.printStackTrace()
                        } finally {
                            try {
                                csvWriter?.close()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                            try {
                                osw?.close()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                            try {
                                output.close()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                        return exportDoc.uri
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                collectActivityContext,
                R.string.field_audio_zip_error,
                Toast.LENGTH_SHORT
            ).show()
        }
        return null
    }
}