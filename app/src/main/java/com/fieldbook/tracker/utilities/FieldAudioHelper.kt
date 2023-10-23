package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaRecorder
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.DocumentTreeUtil.Companion.getFieldMediaDirectory
import com.fieldbook.tracker.utilities.ZipUtil.Companion.zip
import dagger.hilt.android.qualifiers.ActivityContext
import org.phenoapps.utils.BaseDocumentTreeUtil.Companion.getDirectory
import org.phenoapps.utils.BaseDocumentTreeUtil.Companion.getFileOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
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
 */
class FieldAudioHelper @Inject constructor(@ActivityContext private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null

    private var recordingLocation: Uri? = null


    private val mPrefs = PreferenceManager.getDefaultSharedPreferences(context)

    private val ep: SharedPreferences =
        context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0)

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

    private fun zipAudioAndLogFiles(){
        try {
            val audioDocumentFile = recordingLocation?.let {
                DocumentFile.fromSingleUri(context,
                    it
                )
            }

            val geoNavLogWriter = (context as CollectActivity).getGeoNavHelper().getGeoNavLogWriterUri()
            val geoNavFile = geoNavLogWriter?.let {
                DocumentFile.fromSingleUri(context,
                    it
                )
            }

            val timeStamp = SimpleDateFormat(
                "yyyy-MM-dd-hh-mm-ss", Locale.getDefault()
            )
            val c = Calendar.getInstance()
            val mGeneratedName: String
            val fieldAlias = ep.getString(GeneralKeys.FIELD_FILE, "")
            mGeneratedName = "field_audio_log" + context.cRange.plot_id + "_" + fieldAlias + " " + timeStamp.format(c.time)    + ".zip"

            val paths = ArrayList<DocumentFile?>()
            paths.add(audioDocumentFile)
            paths.add(geoNavFile)

            val exportDir = getDirectory(context, R.string.dir_field_audio_log_zip)
            val zipFile = exportDir?.createFile("*/*", mGeneratedName)

            val output = getFileOutputStream(
                context, R.string.dir_field_audio_log_zip, mGeneratedName
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
            val isLoggingEnabled = mPrefs.getBoolean(GeneralKeys.GEONAV_LOG, false)
            if(isLoggingEnabled){
                zipAudioAndLogFiles()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // used in AudioTraitLayout
    fun getRecordingLocation(): Uri? {
        return recordingLocation
    }
    private fun setRecordingLocation(recordingName: String, storageDirectory: String) {
        val audioDir = getFieldMediaDirectory(context, storageDirectory)
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
        val fieldAlias = ep.getString(GeneralKeys.FIELD_FILE, "")
        mGeneratedName = try {
            if (isFieldAudio)
                "field_audio_" + (context as CollectActivity).cRange.plot_id + "_" + fieldAlias + " " + timeStamp.format(
                    c.time
                )
            else
                (context as CollectActivity).cRange.plot_id + " " + timeStamp.format(c.time)
        } catch (e: Exception) {
            "error " + timeStamp.format(c.time)
        }
        setRecordingLocation(mGeneratedName, if (isFieldAudio) "field_audio" else "audio")
        try {
            val fd = recordingLocation?.let { context.contentResolver.openFileDescriptor(it, "rw") }
                ?.fileDescriptor
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
}