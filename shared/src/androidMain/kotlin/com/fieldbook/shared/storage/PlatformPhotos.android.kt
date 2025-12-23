package com.fieldbook.shared.storage

import com.fieldbook.shared.AndroidAppContextHolder
import java.io.File

actual object PlatformPhotos : PlatformPhotoPaths {
    override fun newPhotoFilePath(fileName: String): String {
        val dir = File(AndroidAppContextHolder.context.filesDir, "photos")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName).absolutePath
    }
}

