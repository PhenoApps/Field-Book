package com.fieldbook.shared.utilities

import com.fieldbook.shared.preferences.GeneralKeys
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.core.PlatformDirectory

data class StorageSetupResult(
    val configuredDirectory: String,
    val providerTypeName: String,
    val providerLabel: String,
    val seededSampleCount: Int,
    val sampleSeedFailed: Boolean,
)

class StorageConfigurationException(message: String) : IllegalStateException(message)

suspend fun configureAndPersistStorageDirectory(
    directory: PlatformDirectory,
    settings: Settings = Settings(),
): Result<StorageSetupResult> {
    return runCatching {
        val configuredDirectory = configurePickedStorageDirectory(directory)
            ?: throw StorageConfigurationException("Failed to configure the selected folder.")

        val providerTypeName = detectStorageProviderType(configuredDirectory).name
        val providerLabel = detectStorageProviderLabel(configuredDirectory)

        settings.putString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY.key, configuredDirectory)
        settings.putString(GeneralKeys.DEFAULT_STORAGE_LOCATION_PROVIDER_TYPE.key, providerTypeName)
        settings.putString(GeneralKeys.DEFAULT_STORAGE_LOCATION_PROVIDER_LABEL.key, providerLabel)

        val sampleSeedResult = runCatching { seedBundledStorageSamples() }

        StorageSetupResult(
            configuredDirectory = configuredDirectory,
            providerTypeName = providerTypeName,
            providerLabel = providerLabel,
            seededSampleCount = sampleSeedResult.getOrDefault(0),
            sampleSeedFailed = sampleSeedResult.isFailure,
        )
    }
}
