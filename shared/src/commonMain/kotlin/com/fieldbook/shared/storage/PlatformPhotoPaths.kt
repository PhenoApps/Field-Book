package com.fieldbook.shared.storage

/**
 * Platform-specific paths for storing photos captured in the app.
 *
 * Contract:
 * - returns an absolute path inside an app-controlled directory
 * - directory exists (or can be created)
 */
interface PlatformPhotoPaths {
    /**
     * Returns an absolute path for a new photo file (including filename).
     * The caller is responsible for writing the bytes.
     */
    fun newPhotoFilePath(fileName: String): String
}

expect object PlatformPhotos : PlatformPhotoPaths
