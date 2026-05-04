package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.fieldbook.shared.generated.resources.arrow_expand
import com.fieldbook.shared.generated.resources.arrow_left
import com.fieldbook.shared.generated.resources.camera_24px
import com.fieldbook.shared.generated.resources.close
import com.fieldbook.shared.database.utils.internalTimeFormatter
import com.fieldbook.shared.screens.collect.CollectScreenController
import com.fieldbook.shared.theme.MainFloatingActionButtonShape
import com.fieldbook.shared.utilities.DocumentFile
import com.fieldbook.shared.utilities.DocumentTreeUtil
import com.fieldbook.shared.utilities.deleteFile
import com.fieldbook.shared.utilities.listFiles
import com.fieldbook.shared.utilities.sanitizeFileName
import com.kashif.cameraK.controller.CameraController
import com.kashif.cameraK.enums.CameraLens
import com.kashif.cameraK.enums.Directory
import com.kashif.cameraK.enums.FlashMode
import com.kashif.cameraK.enums.ImageFormat
import com.kashif.cameraK.result.ImageCaptureResult
import com.kashif.cameraK.ui.CameraPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.format
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

private const val PHOTO_VALUE_SEPARATOR = "\n"
private const val PHOTO_DIRECTORY_NAME = "picture"

enum class PhotoTraitDisplayMode {
    INLINE,
    FULLSCREEN
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun PhotoTrait(
    values: List<String>,
    onPhotoCaptured: (String) -> Unit,
    onPhotoDeleted: (String) -> Unit,
    modifier: Modifier = Modifier,
    controller: CollectScreenController,
    displayMode: PhotoTraitDisplayMode = PhotoTraitDisplayMode.INLINE,
    onExpandRequest: () -> Unit = {},
    onCollapseRequest: () -> Unit = {},
) {
    fun currentTraitName(): String {
        return controller.traits
            .getOrNull(controller.currentTraitIndex)
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: PHOTO_DIRECTORY_NAME
    }

    fun currentTraitDirectory(): DocumentFile? {
        return DocumentTreeUtil.getFieldMediaDirectory(currentTraitName())
    }

    fun legacyPictureDirectory(): DocumentFile? {
        return DocumentTreeUtil.getFieldMediaDirectory(PHOTO_DIRECTORY_NAME)
    }

    fun buildPhotoFileName(): String {
        val plotId = controller.units.getOrNull(controller.currentUnitIndex)?.observation_unit_db_id
            ?.takeIf { it.isNotBlank() }
            ?: "photo"
        val traitName = sanitizeFileName(currentTraitName())
        val timestamp = sanitizeFileName(Clock.System.now().format(internalTimeFormatter))
        return "${sanitizeFileName(plotId)}_${traitName}_$timestamp.jpg"
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
    val listState = rememberLazyListState()

    fun extractPhotoFileName(photoRef: String): String {
        return normalizeStoredPhotoRef(photoRef)
            .substringBefore("?")
            .substringAfterLast("/")
            .replace("%20", " ")
    }

    fun findStoredPhotoFile(photoRef: String): DocumentFile? {
        val normalizedPhotoRef = normalizeStoredPhotoRef(photoRef)
        val fileName = extractPhotoFileName(normalizedPhotoRef)
        if (fileName.isBlank()) return null

        val candidateDirectories = listOfNotNull(
            currentTraitDirectory(),
            legacyPictureDirectory(),
            DocumentTreeUtil.getPlotDataDirectory()
        ).distinctBy { it.uri() }

        candidateDirectories.forEach { directory ->
            directory.findFile(fileName)
                ?.takeIf { it.exists() }
                ?.let { return it }

            listFiles(directory)
                .firstOrNull { storedFile ->
                    storedFile.exists() && (
                        storedFile.uri() == normalizedPhotoRef ||
                            storedFile.name() == fileName
                        )
                }
                ?.let { return it }
        }

        return null
    }

    fun resolvePhotoDisplayUri(photoRef: String): String {
        val normalizedPhotoRef = normalizeStoredPhotoRef(photoRef)
        return findStoredPhotoFile(normalizedPhotoRef)?.uri() ?: normalizedPhotoRef
    }

    fun deleteStoredPhoto(photoRef: String) {
        try {
            findStoredPhotoFile(photoRef)?.let(::deleteFile)
        } catch (error: Throwable) {
            println("PhotoTrait: unable to delete photo file: ${error.message}")
        }
    }

    var cameraController by remember(displayMode) { mutableStateOf<CameraController?>(null) }
    var previewSessionKey by remember(displayMode) { mutableStateOf(0) }
    var captureInProgress by remember(displayMode) { mutableStateOf(false) }
    var lastCaptureFinishedAt by remember(displayMode) { mutableStateOf<Instant?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(displayMode, photoUris.value.size) {
        if (displayMode == PhotoTraitDisplayMode.INLINE) {
            listState.scrollToItem(photoUris.value.size)
        }
    }

    fun saveCapturedPhoto(byteArray: ByteArray): String? {
        val dir = currentTraitDirectory()
        if (dir == null) {
            println("PhotoTrait: unable to resolve field media directory for trait ${currentTraitName()}")
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
            createdFile.uri()
        } catch (error: Throwable) {
            println("PhotoTrait: unable to save photo: ${error.message}")
            null
        }
    }

    fun capturePhoto() {
        if (captureInProgress) {
            return
        }

        val now = Clock.System.now()
        val lastFinished = lastCaptureFinishedAt
        if (lastFinished != null && now.minus(lastFinished).inWholeMilliseconds < 750) {
            return
        }

        captureInProgress = true
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
                        if (displayMode == PhotoTraitDisplayMode.INLINE) {
                            cameraController = null
                            previewSessionKey += 1
                            scope.launch {
                                listState.animateScrollToItem(photoUris.value.size)
                            }
                        } else {
                            cameraController = null
                            previewSessionKey += 1
                        }
                    }
                }

                is ImageCaptureResult.Error -> {
                    println("CameraK Error: ${captureResult.exception.message}")
                }

                null -> {
                    println("CameraK Error: camera controller is not ready")
                }
            }

            delay(750)
            lastCaptureFinishedAt = Clock.System.now()
            captureInProgress = false
        }
    }

    if (displayMode == PhotoTraitDisplayMode.FULLSCREEN) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.scrim
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                key(previewSessionKey) {
                    CameraPreviewTile(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .align(Alignment.Center),
                        onControllerReady = { controller ->
                            cameraController = controller
                            captureInProgress = false
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 28.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    FloatingActionButton(
                        onClick = { if (!captureInProgress) capturePhoto() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(92.dp),
                        shape = CircleShape,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.camera_24px),
                            contentDescription = "Capture Photo",
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    IconButton(
                        onClick = onCollapseRequest,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(64.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_left),
                            contentDescription = "Back to collector",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Carousel for captured images and the live collapsed camera preview.
        LazyRow(
            state = listState,
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
                    key(previewSessionKey) {
                        CameraPreviewTile(
                            modifier = Modifier.fillMaxSize(),
                            onControllerReady = { controller ->
                                cameraController = controller
                                captureInProgress = false
                            }
                        )
                    }

                    IconButton(
                        onClick = onExpandRequest,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(64.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_expand),
                            contentDescription = "Expand camera preview",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { if (!captureInProgress) capturePhoto() },
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

@Composable
private fun CameraPreviewTile(
    modifier: Modifier = Modifier,
    onControllerReady: (CameraController) -> Unit,
) {
    CameraPreview(
        modifier = modifier,
        cameraConfiguration = {
            setCameraLens(CameraLens.BACK)
            setFlashMode(FlashMode.OFF)
            setImageFormat(ImageFormat.JPEG)
            setDirectory(Directory.PICTURES)
        },
        onCameraControllerReady = onControllerReady
    )
}
