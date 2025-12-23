package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.camera_24px
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.storage.PlatformPhotos
import com.fieldbook.shared.theme.MainFloatingActionButtonShape
import com.kashif.cameraK.controller.CameraController
import com.kashif.cameraK.enums.CameraLens
import com.kashif.cameraK.enums.Directory
import com.kashif.cameraK.enums.FlashMode
import com.kashif.cameraK.enums.ImageFormat
import com.kashif.cameraK.result.ImageCaptureResult
import com.kashif.cameraK.ui.CameraPreview
import com.russhwolf.settings.Settings
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource


@OptIn(ExperimentalResourceApi::class)
@Composable
fun PhotoTrait(
    values: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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

    // Initialize from db values (list)
    val photoUris = remember {
        val initial = values.map { normalizeStoredPhotoRef(it) }.filter { it.isNotBlank() }
        mutableStateOf(initial)
    }

    var cameraController by remember { mutableStateOf<CameraController?>(null) }
    val scope = rememberCoroutineScope()

    // Retrieve default storage directory from preferences
    val preferences = remember { Settings() }
    // TODO
    val defaultDirectory =
        preferences.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY.key, "")

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Carousel for images and camera preview
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                // FIXME looks good on emulator, find the right setting
                .fillMaxHeight(0.7f)
        ) {
            val width = 230.dp

            // Images
            if (photoUris.value.isNotEmpty()) {
                items(photoUris.value.size) { index ->
                    val displayUri = normalizeStoredPhotoRef(photoUris.value[index])

                    // Log for debugging so you can confirm what is being loaded
                    println("PhotoTrait: loading image: $displayUri")

                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = displayUri,
                            contentDescription = "Photo $index",
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(width),
                            contentScale = ContentScale.Crop,
                            onError = { error ->
                                // Log loading errors for debugging
                                println(
                                    "PhotoTrait: error loading image $displayUri : ${error.result.throwable.message}"
                                )
                            }
                        )

                        // TODO remove (debug)
                        // Small debug URI text so you can see the exact URI being handed to AsyncImage
                        /*Text(
                            text = displayUri,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(4.dp)
                        )*/
                    }
                }
            }

            // Camera preview
            item {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    CameraPreview(
                        modifier = Modifier.fillMaxHeight()
                            .width(width),
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
            onClick = {
                scope.launch {
                    cameraController?.let { controller ->
                        when (val result = controller.takePicture()) {
                            is ImageCaptureResult.Success -> {
                                val fileName =
                                    "Photo_${Clock.System.now().toEpochMilliseconds()}.jpg"

                                // Save in an app-controlled directory so the file definitely exists.
                                val absPath = PlatformPhotos.newPhotoFilePath(fileName)
                                val p = absPath.toPath()
                                FileSystem.SYSTEM.createDirectories(p.parent!!)
                                FileSystem.SYSTEM.write(p) {
                                    write(result.byteArray)
                                }

                                val normalized = "file://$absPath"
                                photoUris.value = photoUris.value + normalized

                                // TODO cannot get fileKit 0.8.8 to work, files are saved but path is wrong
                                //  using PlatformPhotos workaround for now

                                /*val fileName = "Photo_${Clock.System.now().toEpochMilliseconds()}"

                                // TODO autosave (PlatformFile(directory, fileName) not available in filekit 0.8.8)
                                val savedFile =
                                    FileKit.saveFile(
                                        bytes = result.byteArray,
                                        baseName = fileName,
                                        extension = "jpg",
                                    )

                                savedFile?.let { file ->
                                    val path = file.path
                                    if (path != null) {
                                        // Normalize the stored path so consumers see a consistent URI
                                        val normalized = if (path.startsWith("file") || path.startsWith("content:")) path else "file://$path"
                                        val currentList = photoUris.value.toMutableList()
                                        currentList.add(normalized)
                                        photoUris.value = currentList
                                    }
                                }*/

                                onValueChange(normalized)
                            }

                            is ImageCaptureResult.Error -> {
                                println("CameraK Error: ${result.exception.message}")
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .padding(top = 24.dp)
                .size(64.dp),
            shape = MainFloatingActionButtonShape,
        ) {
            Icon(
                painter = painterResource(Res.drawable.camera_24px),
                contentDescription = "Capture Photo"
            )
        }
    }
}
