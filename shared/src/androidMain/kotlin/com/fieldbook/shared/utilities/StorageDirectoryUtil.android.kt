package com.fieldbook.shared.utilities

import android.content.Intent
import com.fieldbook.shared.AndroidAppContextHolder
import io.github.vinceglb.filekit.core.PlatformDirectory
import org.phenoapps.utils.BaseDocumentTreeUtil

actual fun configurePickedStorageDirectory(directory: PlatformDirectory): String? {
    val context = AndroidAppContextHolder.context
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    runCatching {
        context.contentResolver.takePersistableUriPermission(directory.uri, flags)
    }

    val root = BaseDocumentTreeUtil.defineRootStructure(
        context,
        directory.uri,
        defaultStorageDirectoryNames().toTypedArray()
    ) ?: return null

    val rootDocument = AndroidDocumentFile(root)
    if (!ensureStorageDirectoryStructure(rootDocument)) return null

    return directory.uri.toString()
}

actual fun isStorageDirectoryConfigured(): Boolean {
    val root = BaseDocumentTreeUtil.getRoot(AndroidAppContextHolder.context) ?: return false
    return root.exists()
}
