package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.camera_24px
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.theme.MainFloatingActionButtonShape
import com.kashif.cameraK.controller.CameraController
import com.kashif.cameraK.enums.CameraLens
import com.kashif.cameraK.enums.Directory
import com.kashif.cameraK.enums.FlashMode
import com.kashif.cameraK.enums.ImageFormat
import com.kashif.cameraK.result.ImageCaptureResult
import com.kashif.cameraK.ui.CameraPreview
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.core.FileKit
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalResourceApi::class)
@Composable
fun PhotoTrait(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Maintain a local list of photo URIs // TODO onValueChange
    val photoUris = remember {
        // TODO initialize from value
        mutableStateOf(emptyList<String>())
    }

    var cameraController by remember { mutableStateOf<CameraController?>(null) }
    val scope = rememberCoroutineScope()

    // Retrieve default storage directory from preferences
    val preferences = remember { Settings() }
    // TODO
    val defaultDirectory =
        preferences.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY.key, "")

    LaunchedEffect(photoUris.value) {
        onValueChange(photoUris.value.joinToString(","))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Carousel for images and camera preview
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Color.LightGray)
        ) {
            val height = 80.dp
            val width = 80.dp

            // Camera preview as first item
            item {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(width, height),
                    contentAlignment = Alignment.Center
                ) {
                    CameraPreview(
                        modifier = Modifier.fillMaxWidth().height(height),
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
            // Images
            if (photoUris.value.isNotEmpty()) {
                items(photoUris.value.size) { index ->
                    val rawUri = photoUris.value[index]
                    val displayUri = if (rawUri.startsWith("http") || rawUri.startsWith("file")) {
                        rawUri
                    } else {
                        "file://$rawUri"
                    }
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(width, height),
                        contentAlignment = Alignment.Center
                    ) {
                        KamelImage(
                            resource = asyncPainterResource(displayUri),
                            contentDescription = "Photo $index",
                            modifier = Modifier.fillMaxWidth().height(height),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                scope.launch {
                    cameraController?.let { controller ->
                        when (val result = controller.takePicture()) {
                            is ImageCaptureResult.Success -> {
                                val fileName = "Photo_${Clock.System.now().toEpochMilliseconds()}"

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
                                        val currentList = photoUris.value.toMutableList()
                                        currentList.add(path)
                                        photoUris.value = currentList
                                    }
                                }
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
