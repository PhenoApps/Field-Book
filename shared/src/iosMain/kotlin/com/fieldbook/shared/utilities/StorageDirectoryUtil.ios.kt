package com.fieldbook.shared.utilities

import io.github.vinceglb.filekit.core.PlatformDirectory

actual fun configurePickedStorageDirectory(directory: PlatformDirectory): String? = directory.path

actual fun isStorageDirectoryConfigured(): Boolean = false
