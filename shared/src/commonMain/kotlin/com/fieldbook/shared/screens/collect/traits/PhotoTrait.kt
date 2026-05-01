package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.camera_24px
import com.fieldbook.shared.generated.resources.close
import com.fieldbook.shared.screens.collect.CollectScreenController
import com.fieldbook.shared.theme.MainFloatingActionButtonShape
import com.fieldbook.shared.utilities.DocumentTreeUtil
import com.fieldbook.shared.utilities.deleteFile
import com.fieldbook.shared.utilities.sanitizeFileName
import com.kashif.cameraK.controller.CameraController
import com.kashif.cameraK.enums.CameraLens
import com.kashif.cameraK.enums.Directory
import com.kashif.cameraK.enums.FlashMode
import com.kashif.cameraK.enums.ImageFormat
import com.kashif.cameraK.result.ImageCaptureResult
import com.kashif.cameraK.ui.CameraPreview
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import kotlin.math.absoluteValue

private const val PHOTO_VALUE_SEPARATOR = "\n"
private const val PHOTO_DIRECTORY_NAME = "picture"

@OptIn(ExperimentalResourceApi::class)
@Composable
fun PhotoTrait(
    values: List<String>,
    onPhotoCaptured: (String) -> Unit,
    onPhotoDeleted: (String) -> Unit,
    modifier: Modifier = Modifier,
    controller: CollectScreenController,
) {
    fun Int.twoDigits(): String = toString().padStart(2, '0')

    fun buildPhotoFileName(): String {
        val now = Clock.System.now()
        val plotId = controller.units.getOrNull(controller.currentUnitIndex)?.observation_unit_db_id
            ?.takeIf { it.isNotBlank() }
            ?: "photo"
        val timeZone = TimeZone.currentSystemDefault()
        val local = now.toLocalDateTime(timeZone)

        val offset = timeZone.offsetAt(now)

        val offsetHours = (offset.totalSeconds / 3600)
        val offsetMinutes = ((offset.totalSeconds % 3600) / 60).absoluteValue
        val timestamp = buildString {
            append(local.year)
            append("-")
            append(local.monthNumber.twoDigits())
            append("-")
            append(local.dayOfMonth.twoDigits())
            append("T")
            append(local.hour.twoDigits())
            append("_")
            append(local.minute.twoDigits())
            append("_")
            append(local.second.twoDigits())
            append(".")
            append(local.nanosecond / 1_000_000)
            append(
                if (offsetHours >= 0) "+" else "-"
            )
            append(offsetHours.absoluteValue.twoDigits())
            append("_")
            append(offsetMinutes.twoDigits())
        }
        return "${sanitizeFileName(plotId)}_picture_$timestamp.jpg"
    }

    fun normalizeStoredPhotoRef(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("content://") -> trimmed
            trimmed.startsWith("file://") -> trimmed
            // Legacy values sometimes stored as absolute paths
            trimmed.startsWith("/") -> "file://$trimmed"
            else -> trimmed
        }
    }

    fun decodeStoredPhotoRefs(storedValues: List<String>): List<String> {
        return storedValues
            .flatMap { it.split(PHOTO_VALUE_SEPARATOR) }
            .map(::normalizeStoredPhotoRef)
            .filter { it.isNotBlank() }
    }

    val valuesKey = values.joinToString(PHOTO_VALUE_SEPARATOR)
    val photoUris = remember(valuesKey) {
        val initial = decodeStoredPhotoRefs(values)
        mutableStateOf(initial)
    }

    fun extractPhotoFileName(photoRef: String): String {
        return normalizeStoredPhotoRef(photoRef)
            .substringBefore("?")
            .substringAfterLast("/")
            .replace("%20", " ")
    }

    fun resolvePhotoDisplayUri(photoRef: String): String {
        val normalizedPhotoRef = normalizeStoredPhotoRef(photoRef)
        val fileName = extractPhotoFileName(normalizedPhotoRef)
        return fileName
            .takeIf { it.isNotBlank() }
            ?.let {
                DocumentTreeUtil.getFieldMediaDirectory(PHOTO_DIRECTORY_NAME)?.findFile(it)
                    ?: DocumentTreeUtil.getPlotDataDirectory()?.findFile(it)
            }
            ?.takeIf { it.exists() }
            ?.uri()
            ?: normalizedPhotoRef
    }

    fun deleteStoredPhoto(photoRef: String) {
        try {
            val fileName = extractPhotoFileName(photoRef)
            if (fileName.isBlank()) return

            (
                DocumentTreeUtil.getFieldMediaDirectory(PHOTO_DIRECTORY_NAME)?.findFile(fileName)
                    ?: DocumentTreeUtil.getPlotDataDirectory()?.findFile(fileName)
                )
                ?.let(::deleteFile)
        } catch (error: Throwable) {
            println("PhotoTrait: unable to delete photo file: ${error.message}")
        }
    }

    var cameraController by remember { mutableStateOf<CameraController?>(null) }
    val scope = rememberCoroutineScope()

    fun saveCapturedPhoto(byteArray: ByteArray): String? {
        val dir = DocumentTreeUtil.getFieldMediaDirectory(PHOTO_DIRECTORY_NAME)
        if (dir == null) {
            println("PhotoTrait: unable to resolve field picture directory")
            return null
        }

        val fileName = buildPhotoFileName()
        val createdFile = dir.createFile(
            mimeType = "image/jpeg",
            name = fileName
        )
        if (createdFile == null) {
            println("PhotoTrait: unable to create photo file")
            return null
        }

        return try {
            createdFile.writeBytes(byteArray)
            fileName
        } catch (error: Throwable) {
            println("PhotoTrait: unable to save photo: ${error.message}")
            null
        }
    }

    fun capturePhoto() {
        scope.launch {
            val captureResult = try {
                cameraController?.takePicture()
            } catch (error: Throwable) {
                println("CameraK Error: ${error.message}")
                null
            }

            when (captureResult) {
                is ImageCaptureResult.Success -> {
                    val photoUri = saveCapturedPhoto(captureResult.byteArray)
                    if (photoUri != null) {
                        photoUris.value = photoUris.value + photoUri
                        onPhotoCaptured(photoUri)
                    }
                }

                is ImageCaptureResult.Error -> {
                    println("CameraK Error: ${captureResult.exception.message}")
                }

                null -> {
                    println("CameraK Error: camera controller is not ready")
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Carousel for captured images and the live collapsed camera preview.
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                // FIXME looks good on emulator, find the right setting
                .fillMaxHeight(0.7f),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            val itemWidth = 230.dp
            val itemShape = RoundedCornerShape(8.dp)

            // Images
            itemsIndexed(photoUris.value) { index, photoUri ->
                val displayUri = resolvePhotoDisplayUri(photoUri)

                Box(
                    modifier = Modifier
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = displayUri,
                        contentDescription = "Photo ${index + 1}",
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(itemWidth)
                            .clip(itemShape)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = itemShape
                            ),
                        contentScale = ContentScale.Crop,
                        onError = { error ->
                            // Log loading errors for debugging
                            println(
                                "PhotoTrait: error loading image $displayUri : ${error.result.throwable.message}"
                            )
                        }
                    )

                    IconButton(
                        onClick = {
                            val removedPhotoUri = photoUris.value.getOrNull(index) ?: return@IconButton
                            val normalizedPhotoUri = normalizeStoredPhotoRef(removedPhotoUri)
                            val updated = photoUris.value.toMutableList().also {
                                it.removeAt(index)
                            }
                            deleteStoredPhoto(normalizedPhotoUri)
                            photoUris.value = updated
                            onPhotoDeleted(normalizedPhotoUri)
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.close),
                            contentDescription = "Delete photo",
                            modifier = Modifier
                                .size(22.dp)
                        )
                    }
                }
            }

            // Live camera preview.
            item {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(itemWidth)
                        .clip(itemShape)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = itemShape
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        cameraConfiguration = {
                            setCameraLens(CameraLens.BACK)
                            setFlashMode(FlashMode.OFF)
                            setImageFormat(ImageFormat.JPEG)
                            setDirectory(Directory.PICTURES)
                        },
                        onCameraControllerReady = { controller ->
                            cameraController = controller
                        }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { capturePhoto() },
            modifier = Modifier
                .padding(top = 16.dp)
                .size(72.dp),
            shape = MainFloatingActionButtonShape,
        ) {
            Icon(
                painter = painterResource(Res.drawable.camera_24px),
                contentDescription = "Capture Photo"
            )
        }
    }
}
