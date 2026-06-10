package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.trait_audio_button_content_description
import com.fieldbook.shared.generated.resources.trait_audio_duration
import com.fieldbook.shared.generated.resources.trait_audio_file_size
import com.fieldbook.shared.generated.resources.trait_audio_placeholder_filename
import com.fieldbook.shared.generated.resources.trait_audio
import com.fieldbook.shared.generated.resources.trait_audio_play
import com.fieldbook.shared.generated.resources.trait_audio_stop
import com.fieldbook.shared.generated.resources.trait_audio_timestamp
import com.fieldbook.shared.screens.collect.CollectScreenController
import com.fieldbook.shared.utilities.DocumentTreeUtil
import com.fieldbook.shared.utilities.currentLocalInternalTimestamp
import com.fieldbook.shared.utilities.deleteFile
import com.fieldbook.shared.utilities.sanitizeFileName
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private enum class AudioButtonState {
    WAITING_FOR_RECORDING,
    RECORDING,
    WAITING_FOR_PLAYBACK,
    PLAYING
}

@Composable
fun AudioTrait(
    value: String,
    onValueChange: (String) -> Unit,
    controller: CollectScreenController,
    modifier: Modifier = Modifier,
) {
    val audioController = remember { PlatformAudioController() }
    var buttonState by remember { mutableStateOf(AudioButtonState.WAITING_FOR_RECORDING) }
    var recordedUri by remember { mutableStateOf(value.takeUnless { it == "NA" }.orEmpty()) }
    var metadata by remember { mutableStateOf<AudioTraitMetadata?>(null) }

    fun currentTraitName(): String {
        return controller.traits
            .getOrNull(controller.currentTraitIndex)
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: "audio"
    }

    fun buildAudioFileName(): String {
        val plotId = controller.units.getOrNull(controller.currentUnitIndex)?.observation_unit_db_id
            ?.takeIf { it.isNotBlank() }
            ?: "audio"
        val traitName = sanitizeFileName(currentTraitName())
        val timestamp = sanitizeFileName(currentLocalInternalTimestamp())
        return "${sanitizeFileName(plotId)}_${traitName}_$timestamp.mp4"
    }

    fun createAudioTargetUri(): String? {
        val directory = DocumentTreeUtil.getFieldMediaDirectory(currentTraitName()) ?: return null
        val file = directory.createFile("audio/mp4", buildAudioFileName()) ?: return null
        return file.uri()
    }

    fun deleteStoredAudio(uri: String) {
        val fileName = uri.substringBefore("?").substringAfterLast("/").replace("%20", " ")
        val directory = DocumentTreeUtil.getFieldMediaDirectory(currentTraitName()) ?: return
        directory.findFile(fileName)?.let(::deleteFile)
    }

    LaunchedEffect(value) {
        if (buttonState == AudioButtonState.RECORDING || buttonState == AudioButtonState.PLAYING) {
            return@LaunchedEffect
        }

        if (value == "NA") {
            recordedUri = ""
            metadata = null
            buttonState = AudioButtonState.WAITING_FOR_RECORDING
        } else {
            recordedUri = value
            metadata = value.takeIf { it.isNotBlank() }?.let(::inspectAudioTraitFile)
            buttonState = if (value.isNotBlank()) {
                AudioButtonState.WAITING_FOR_PLAYBACK
            } else {
                AudioButtonState.WAITING_FOR_RECORDING
            }
        }
    }

    LaunchedEffect(buttonState) {
        controller.updateCollectInteractionLocked(
            buttonState == AudioButtonState.RECORDING || buttonState == AudioButtonState.PLAYING
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            audioController.dispose()
            controller.updateCollectInteractionLocked(false)
        }
    }

    fun stopPlayback() {
        audioController.stopPlayback()
        buttonState = if (recordedUri.isNotBlank()) {
            AudioButtonState.WAITING_FOR_PLAYBACK
        } else {
            AudioButtonState.WAITING_FOR_RECORDING
        }
    }

    fun stopRecording() {
        audioController.stopRecording()
        if (recordedUri.isNotBlank()) {
            metadata = inspectAudioTraitFile(recordedUri)
            buttonState = AudioButtonState.WAITING_FOR_PLAYBACK
        } else {
            buttonState = AudioButtonState.WAITING_FOR_RECORDING
        }
    }

    fun startPlayback() {
        val uri = recordedUri.takeIf { it.isNotBlank() } ?: return
        if (audioController.startPlayback(uri) {
                buttonState = AudioButtonState.WAITING_FOR_PLAYBACK
            }
        ) {
            buttonState = AudioButtonState.PLAYING
        }
    }

    fun startRecording() {
        if (recordedUri.isNotBlank()) {
            deleteStoredAudio(recordedUri)
            recordedUri = ""
            metadata = null
            onValueChange("")
        }
        val targetUri = createAudioTargetUri() ?: return
        if (audioController.startRecording(targetUri)) {
            recordedUri = targetUri
            buttonState = AudioButtonState.RECORDING
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (value == "NA") {
            AudioInfoCard(
                title = "NA",
                metadata = null
            )
        } else if (recordedUri.isNotBlank() && metadata != null) {
            AudioInfoCard(
                title = stringResource(Res.string.trait_audio_placeholder_filename),
                metadata = metadata
            )
        }

        if (recordedUri.isNotBlank() || value == "NA") {
            Spacer(modifier = Modifier.size(16.dp))
        }

        FloatingActionButton(
            onClick = {
                when (buttonState) {
                    AudioButtonState.WAITING_FOR_RECORDING -> startRecording()
                    AudioButtonState.RECORDING -> {
                        stopRecording()
                        if (recordedUri.isNotBlank()) {
                            onValueChange(recordedUri)
                        }
                    }
                    AudioButtonState.WAITING_FOR_PLAYBACK -> startPlayback()
                    AudioButtonState.PLAYING -> stopPlayback()
                }
            },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(96.dp)
        ) {
            Icon(
                painter = painterResource(
                    when (buttonState) {
                        AudioButtonState.WAITING_FOR_RECORDING -> Res.drawable.trait_audio
                        AudioButtonState.RECORDING -> Res.drawable.trait_audio_stop
                        AudioButtonState.WAITING_FOR_PLAYBACK -> Res.drawable.trait_audio_play
                        AudioButtonState.PLAYING -> Res.drawable.trait_audio_stop
                    }
                ),
                contentDescription = stringResource(Res.string.trait_audio_button_content_description),
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

@Composable
private fun AudioInfoCard(
    title: String,
    metadata: AudioTraitMetadata?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (metadata != null) {
                Text(
                    text = "${stringResource(Res.string.trait_audio_timestamp)}${metadata.timestamp}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${stringResource(Res.string.trait_audio_duration)}${metadata.duration}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${stringResource(Res.string.trait_audio_file_size)}${metadata.fileSize}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
