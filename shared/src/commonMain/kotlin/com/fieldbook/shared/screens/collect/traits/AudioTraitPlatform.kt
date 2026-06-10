package com.fieldbook.shared.screens.collect.traits

data class AudioTraitMetadata(
    val timestamp: String,
    val duration: String,
    val fileSize: String,
)

expect class PlatformAudioController() {
    fun startRecording(outputUri: String): Boolean
    fun stopRecording()
    fun startPlayback(inputUri: String, onPlaybackCompleted: () -> Unit): Boolean
    fun stopPlayback()
    fun dispose()
}

expect fun inspectAudioTraitFile(uri: String): AudioTraitMetadata?
