package com.fieldbook.tracker.utilities

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.core.content.ContextCompat
import com.fieldbook.tracker.activities.ScannerActivity.Companion.startScanner

private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
fun Context.isPermissionGranted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

inline fun Context.cameraPermissionRequest(){
    AlertDialog.Builder(this)
            .setTitle("Camera Permission Required")
            .setMessage("Camera permission is necessary for scanning barcodes")
            .setPositiveButton("Allow") { _, _ ->
                openPermissionSettings()
            }.setNegativeButton("Cancel") { _, _ ->
            }
            .show()
}

fun Context.openPermissionSettings() {
    Intent(ACTION_APPLICATION_DETAILS_SETTINGS).also{
        val uri: Uri = Uri.fromParts("package", packageName, null)
        it.data = uri
        startActivity(it)
    }
}

fun Context.requestCameraAndStartScanner(requestCode: Int) {
    if (isPermissionGranted(CAMERA_PERMISSION)) {
        startScanner(this, requestCode)
    } else {
        cameraPermissionRequest()
    }
}