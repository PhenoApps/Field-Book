package com.fieldbook.shared.screens.collect.traits

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import com.fieldbook.shared.AndroidAppContextHolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow

actual class PlatformAudioController {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null

    actual fun startRecording(outputUri: String): Boolean {
        return try {
            stopPlayback()
            mediaRecorder?.release()
            mediaRecorder = MediaRecorder()
            val uri = Uri.parse(outputUri)
            val fd = AndroidAppContextHolder.context.contentResolver
                .openFileDescriptor(uri, "rw")
                ?.fileDescriptor
                ?: return false

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
                setOutputFile(fd)
                prepare()
                start()
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    actual fun stopRecording() {
        try {
            mediaRecorder?.stop()
        } catch (_: Throwable) {
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    actual fun startPlayback(inputUri: String, onPlaybackCompleted: () -> Unit): Boolean {
        return try {
            stopPlayback()
            val uri = Uri.parse(inputUri)
            mediaPlayer = MediaPlayer.create(AndroidAppContextHolder.context, uri)?.apply {
                setOnCompletionListener {
                    stopPlayback()
                    onPlaybackCompleted()
                }
                start()
            }
            mediaPlayer != null
        } catch (_: Throwable) {
            stopPlayback()
            false
        }
    }

    actual fun stopPlayback() {
        try {
            mediaPlayer?.stop()
        } catch (_: Throwable) {
        } finally {
            mediaPlayer?.reset()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    actual fun dispose() {
        stopRecording()
        stopPlayback()
    }
}

actual fun inspectAudioTraitFile(uri: String): AudioTraitMetadata? {
    return try {
        val ctx = AndroidAppContextHolder.context
        val parsed = Uri.parse(uri)
        val document = androidx.documentfile.provider.DocumentFile.fromSingleUri(ctx, parsed)
        val lastModified = document?.lastModified() ?: 0L
        val fileSize = document?.length() ?: 0L
        val duration = MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(ctx, parsed)
            val millis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            formatDurationMillis(millis)
        }

        AudioTraitMetadata(
            timestamp = SimpleDateFormat("MMM d, yyyy | h:mm a", Locale.getDefault()).format(Date(lastModified)),
            duration = duration,
            fileSize = formatFileSize(fileSize)
        )
    } catch (_: Throwable) {
        null
    }
}

private inline fun <T : AutoCloseable?, R> T.use(block: (T) -> R): R {
    return try {
        block(this)
    } finally {
        this?.close()
    }
}

private fun formatDurationMillis(durationMillis: Long): String {
    val totalSeconds = ceil(durationMillis / 1000.0).toLong()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val exponent = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
    val prefix = "KMGTPE"[exponent - 1].toString()
    return "%.2f %sB".format(bytes / 1024.0.pow(exponent.toDouble()), prefix)
}
