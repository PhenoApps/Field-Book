package com.fieldbook.shared.storage

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSArray
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.firstObject

actual object PlatformPhotos : PlatformPhotoPaths {
    @OptIn(ExperimentalForeignApi::class)
    override fun newPhotoFilePath(fileName: String): String {
        val fm = NSFileManager.defaultManager
        val urls = fm.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask) as NSArray
        val docsDir: NSURL = (urls.firstObject as? NSURL)
            ?: error("NSFileManager.URLsForDirectory returned empty result for Documents")

        val photosDir = docsDir.URLByAppendingPathComponent("photos", isDirectory = true)!!

        val dirPath = photosDir.path ?: ((docsDir.path ?: "") + "/photos")
        if (!fm.fileExistsAtPath(dirPath)) {
            fm.createDirectoryAtPath(
                path = dirPath,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }

        val photoUrl = photosDir.URLByAppendingPathComponent(fileName, isDirectory = false)!!
        return photoUrl.path!!
    }
}
