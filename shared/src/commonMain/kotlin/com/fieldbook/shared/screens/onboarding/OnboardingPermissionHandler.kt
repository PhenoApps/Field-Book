package com.fieldbook.shared.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.camera.CAMERA
import dev.icerock.moko.permissions.compose.BindEffect
import dev.icerock.moko.permissions.compose.rememberPermissionsControllerFactory
import dev.icerock.moko.permissions.location.COARSE_LOCATION
import dev.icerock.moko.permissions.location.LOCATION
import dev.icerock.moko.permissions.microphone.RECORD_AUDIO

interface OnboardingPermissionHandler {
    suspend fun checkPermissions(): Boolean
    suspend fun requestPermissions(): Boolean
}

private class MokoOnboardingPermissionHandler(
    private val controller: PermissionsController
) : OnboardingPermissionHandler {
    override suspend fun checkPermissions(): Boolean {
        val hasCamera = controller.isPermissionGranted(Permission.CAMERA)
        val hasAudio = controller.isPermissionGranted(Permission.RECORD_AUDIO)
        val hasFineLocation = controller.isPermissionGranted(Permission.LOCATION)
        val hasCoarseLocation = controller.isPermissionGranted(Permission.COARSE_LOCATION)

        return hasCamera && hasAudio && (hasFineLocation || hasCoarseLocation)
    }

    override suspend fun requestPermissions(): Boolean {
        controller.requestIfNeeded(Permission.CAMERA)
        controller.requestIfNeeded(Permission.RECORD_AUDIO)

        controller.requestIfNeeded(Permission.LOCATION)
        if (!controller.isPermissionGranted(Permission.LOCATION)) {
            controller.requestIfNeeded(Permission.COARSE_LOCATION)
        }

        return checkPermissions()
    }
}

@Composable
fun rememberOnboardingPermissionHandler(): OnboardingPermissionHandler {
    val factory = rememberPermissionsControllerFactory()
    val controller = remember(factory) { factory.createPermissionsController() }

    BindEffect(controller)

    return remember(controller) { MokoOnboardingPermissionHandler(controller) }
}

private suspend fun PermissionsController.requestIfNeeded(permission: Permission) {
    if (!isPermissionGranted(permission)) {
        runCatching { providePermission(permission) }
    }
}
