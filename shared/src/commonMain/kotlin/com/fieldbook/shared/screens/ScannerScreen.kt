package com.fieldbook.shared.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.kashif.cameraK.enums.CameraLens
import com.kashif.cameraK.enums.Directory
import com.kashif.cameraK.enums.FlashMode
import com.kashif.cameraK.enums.ImageFormat
import com.kashif.cameraK.ui.CameraPreview
import com.kashif.qrscannerplugin.rememberQRScannerPlugin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onResult: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    // CameraK QR plugin
    val qrScannerPlugin = rememberQRScannerPlugin(coroutineScope = scope)

    // Collect scanned QR codes
    LaunchedEffect(Unit) {
        qrScannerPlugin
            .getQrCodeFlow()
            .distinctUntilChanged()
            .collectLatest { code ->
                // Stop further detections before navigating away
                qrScannerPlugin.pauseScanning()
                onResult(code)
                onBack()
            }
    }

    CameraPreview(
        modifier = Modifier.fillMaxSize(),
        cameraConfiguration = {
            setCameraLens(CameraLens.BACK)
            setFlashMode(FlashMode.OFF)
            setImageFormat(ImageFormat.JPEG)
            setDirectory(Directory.PICTURES)
            addPlugin(qrScannerPlugin)
        },
        onCameraControllerReady = {
            qrScannerPlugin.startScanning()
        }
    )
}
