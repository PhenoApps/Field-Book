@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.fieldbook.shared.screens.collect.traits

import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.prepareToPlay
import platform.AVFAudio.setActive
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURL.Companion.fileURLWithPath
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_SEC
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow

actual class PlatformAudioController {
    private var recorder: AVAudioRecorder? = null
    private var player: AVAudioPlayer? = null
    private var playbackToken: Long = 0L

    actual fun startRecording(outputUri: String): Boolean {
        return try {
            stopPlayback()
            stopRecording()

            val outputUrl = outputUri.toPlatformFileUrl() ?: return false
            configureAudioSession() ?: return false

            val settings = mapOf<Any?, Any?>(
                AVFormatIDKey to 1633772320u,
                AVSampleRateKey to 44100.0,
                AVNumberOfChannelsKey to 1,
                AVEncoderAudioQualityKey to 2
            )

            val audioRecorder = AVAudioRecorder(outputUrl, settings, null) ?: return false

            recorder = audioRecorder
            audioRecorder.prepareToRecord()
            audioRecorder.record()
        } catch (_: Throwable) {
            false
        }
    }

    actual fun stopRecording() {
        recorder?.stop()
        recorder = null
    }

    actual fun startPlayback(inputUri: String, onPlaybackCompleted: () -> Unit): Boolean {
        return try {
            stopPlayback()
            val inputUrl = inputUri.toPlatformFileUrl() ?: return false
            val audioPlayer = AVAudioPlayer(inputUrl, null) ?: return false

            player = audioPlayer
            playbackToken += 1
            val token = playbackToken
            audioPlayer.prepareToPlay()
            if (!audioPlayer.play()) {
                stopPlayback()
                return false
            }

            val delayNanos = (audioPlayer.duration * NSEC_PER_SEC.toDouble()).toLong()
            dispatch_after(
                dispatch_time(DISPATCH_TIME_NOW, delayNanos),
                dispatch_get_main_queue()
            ) {
                if (playbackToken == token) {
                    stopPlayback()
                    onPlaybackCompleted()
                }
            }
            true
        } catch (_: Throwable) {
            stopPlayback()
            false
        }
    }

    actual fun stopPlayback() {
        playbackToken += 1
        player?.stop()
        player = null
    }

    actual fun dispose() {
        stopRecording()
        stopPlayback()
    }

    private fun configureAudioSession(): AVAudioSession? {
        return try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)
            session.setActive(true, error = null)
            session
        } catch (_: Throwable) {
            null
        }
    }
}

actual fun inspectAudioTraitFile(uri: String): AudioTraitMetadata? {
    return try {
        val fileUrl = uri.toPlatformFileUrl() ?: return null
        val path = fileUrl.path?.toString() ?: return null
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
            ?: return null
        val modifiedDate = attrs[NSFileModificationDate] as? NSDate
        val sizeBytes = (attrs["NSFileSize"] as? NSNumber)?.longLongValue ?: 0L
        val player = AVAudioPlayer(fileUrl, null)

        AudioTraitMetadata(
            timestamp = modifiedDate?.let(::formatDate) ?: "",
            duration = formatDurationSeconds(player?.duration ?: 0.0),
            fileSize = formatFileSize(sizeBytes)
        )
    } catch (_: Throwable) {
        null
    }
}

private fun String.toPlatformFileUrl(): NSURL? {
    return when {
        startsWith("file://") -> NSURL.URLWithString(this)
        startsWith("/") -> fileURLWithPath(this)
        else -> NSURL.URLWithString(this)
    }
}

private fun formatDate(date: NSDate): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "MMM d, yyyy | h:mm a"
    return formatter.stringFromDate(date)
}

private fun formatDurationSeconds(durationSeconds: Double): String {
    val totalSeconds = ceil(durationSeconds).toLong()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val minutePrefix = if (minutes < 10) "0" else ""
    val secondPrefix = if (seconds < 10) "0" else ""
    return "$minutePrefix$minutes:$secondPrefix$seconds"
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val exponent = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
    val prefix = "KMGTPE"[exponent - 1].toString()
    val value = bytes / 1024.0.pow(exponent.toDouble())
    return "${value.toFixed(2)} ${prefix}B"
}

private fun Double.toFixed(decimals: Int): String {
    val multiplier = 10.0.pow(decimals.toDouble())
    val rounded = kotlin.math.round(this * multiplier) / multiplier
    val text = rounded.toString()
    val dotIndex = text.indexOf('.')
    if (dotIndex == -1) return "$text.${"0".repeat(decimals)}"

    val fractionalLength = text.length - dotIndex - 1
    return if (fractionalLength >= decimals) {
        text
    } else {
        text + "0".repeat(decimals - fractionalLength)
    }
}
