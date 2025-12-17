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
import androidx.compose.material3.Text
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
import com.kashif.cameraK.controller.CameraController
import com.kashif.cameraK.enums.CameraLens
import com.kashif.cameraK.enums.Directory
import com.kashif.cameraK.enums.FlashMode
import com.kashif.cameraK.enums.ImageFormat
import com.kashif.cameraK.result.ImageCaptureResult
import com.kashif.cameraK.ui.CameraPreview
import com.kashif.imagesaverplugin.ImageSaverConfig
import com.kashif.imagesaverplugin.rememberImageSaverPlugin
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalResourceApi::class)
@Composable
fun PhotoTrait(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Maintain a local list of photo URIs
    val photoUris = remember {
        mutableStateOf<List<String>>(
            if (value.isNotBlank()) value.split(",").filter { it.isNotBlank() } else emptyList()
        )
    }

    // Create a state to hold the CameraController
    var cameraController by remember { mutableStateOf<CameraController?>(null) }

    // Create a CoroutineScope to call the suspend function
    val scope = rememberCoroutineScope()

    val imageSaverPlugin = rememberImageSaverPlugin(
        config = ImageSaverConfig(
            isAutoSave = false,
            prefix = "MyApp",
            directory = Directory.PICTURES,
            customFolderName = "MyAppPhotos"  // Android only
        )
    )

    // Keep onValueChange referenced
    LaunchedEffect(photoUris.value) {
        onValueChange(photoUris.value.joinToString(","))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Camera preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            CameraPreview(
                modifier = Modifier.fillMaxWidth().height(200.dp),
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

        // Carousel for images
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Color.LightGray)
        ) {
            if (photoUris.value.isNotEmpty()) {
                items(photoUris.value.size) { index ->
                    val rawUri = photoUris.value[index]

                    // Ensure URI works with Kamel (needs file:// for local paths)
                    val displayUri = if (rawUri.startsWith("http") || rawUri.startsWith("file")) {
                        rawUri
                    } else {
                        "file://$rawUri"
                    }

                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(80.dp, 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        KamelImage(
                            resource = asyncPainterResource(displayUri),
                            contentDescription = "Photo $index",
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            contentScale = ContentScale.Crop,
                            onLoading = { Text("...") },
                            onFailure = { Text("X") }
                        )
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No Photos Yet", color = Color.DarkGray)
                    }
                }
            }
        }

        // Capture button
        FloatingActionButton(
            onClick = {
                // Launch coroutine to handle the suspend function
                scope.launch {
                    cameraController?.let { controller ->
                        // Call takePicture and switch on the result
                        when (val result = controller.takePicture()) {
                            is ImageCaptureResult.Success -> {
                                // Add the new path to the list
                                // Handle the captured image
//                                val bitmap = result.byteArray.decodeToImageBitmap()

                                // Manually save the image if auto-save is disabled
                                if (!imageSaverPlugin.config.isAutoSave) {
                                    imageSaverPlugin.saveImage(
                                        byteArray = result.byteArray,
                                        // FIXME
//                                        imageName = "Photo_${System.currentTimeMillis()}"
                                    )
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
        ) {
            Icon(
                painter = painterResource(Res.drawable.camera_24px),
                contentDescription = "Capture Photo"
            )
        }
    }
}

