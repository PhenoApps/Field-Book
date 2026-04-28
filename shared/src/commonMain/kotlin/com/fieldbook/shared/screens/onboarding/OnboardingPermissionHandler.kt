package com.fieldbook.shared.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.icerock.moko.permissions.DeniedAlwaysException
import dev.icerock.moko.permissions.DeniedException
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.RequestCanceledException
import dev.icerock.moko.permissions.camera.CAMERA
import dev.icerock.moko.permissions.compose.BindEffect
import dev.icerock.moko.permissions.compose.rememberPermissionsControllerFactory
import dev.icerock.moko.permissions.location.COARSE_LOCATION
import dev.icerock.moko.permissions.location.LOCATION
import dev.icerock.moko.permissions.microphone.RECORD_AUDIO

interface OnboardingPermissionHandler {
    suspend fun checkPermissions(): Boolean
    suspend fun requestPermissions(): OnboardingPermissionRequestResult
}

data class OnboardingPermissionRequestResult(
    val permissions: Map<Permission, Boolean>
) {
    val granted: Boolean
        get() {
            val hasCamera = permissions[Permission.CAMERA] == true
            val hasAudio = permissions[Permission.RECORD_AUDIO] == true
            val hasFineLocation = permissions[Permission.LOCATION] == true
            val hasCoarseLocation = permissions[Permission.COARSE_LOCATION] == true

            return hasCamera && hasAudio && (hasFineLocation || hasCoarseLocation)
        }
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

    override suspend fun requestPermissions(): OnboardingPermissionRequestResult {
        controller.requestIfNeeded(Permission.CAMERA)
        controller.requestIfNeeded(Permission.RECORD_AUDIO)

        controller.requestIfNeeded(Permission.LOCATION)
        if (!controller.isPermissionGranted(Permission.LOCATION)) {
            controller.requestIfNeeded(Permission.COARSE_LOCATION)
        }

        return OnboardingPermissionRequestResult(
            permissions = mapOf(
                Permission.CAMERA to controller.isPermissionGranted(Permission.CAMERA),
                Permission.RECORD_AUDIO to controller.isPermissionGranted(Permission.RECORD_AUDIO),
                Permission.LOCATION to controller.isPermissionGranted(Permission.LOCATION),
                Permission.COARSE_LOCATION to controller.isPermissionGranted(Permission.COARSE_LOCATION)
            )
        )
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
        try {
            providePermission(permission)
        } catch (deniedAlways: DeniedAlwaysException) {
            // The user needs to enable this permission from system settings.
        } catch (denied: DeniedException) {
            // The user can be prompted again from the onboarding button.
        } catch (canceled: RequestCanceledException) {
            // The user can restart the request from the onboarding button.
        }
    }
}
