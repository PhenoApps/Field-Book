package com.fieldbook.shared.utilities

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.dir_archive
import com.fieldbook.shared.generated.resources.dir_database
import com.fieldbook.shared.generated.resources.dir_field_export
import com.fieldbook.shared.generated.resources.dir_field_import
import com.fieldbook.shared.generated.resources.dir_geonav
import com.fieldbook.shared.generated.resources.dir_plot_data
import com.fieldbook.shared.generated.resources.dir_preferences
import com.fieldbook.shared.generated.resources.dir_resources
import com.fieldbook.shared.generated.resources.dir_trait
import com.fieldbook.shared.generated.resources.dir_updates
import io.github.vinceglb.filekit.core.PlatformDirectory
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

fun defaultStorageDirectoryNames(): List<String> = runBlocking {
    listOf(
        getString(Res.string.dir_archive),
        getString(Res.string.dir_database),
        getString(Res.string.dir_field_export),
        getString(Res.string.dir_field_import),
        getString(Res.string.dir_geonav),
        getString(Res.string.dir_plot_data),
        getString(Res.string.dir_resources),
        getString(Res.string.dir_trait),
        getString(Res.string.dir_updates),
        getString(Res.string.dir_preferences),
    )
}

fun ensureStorageDirectoryStructure(root: DocumentFile): Boolean {
    return defaultStorageDirectoryNames().all { directoryName ->
        val existing = root.findFile(directoryName)
        when {
            existing == null -> root.createDirectory(directoryName) != null
            !existing.exists() -> root.createDirectory(directoryName) != null
            else -> existing.isDirectory()
        }
    }
}

expect fun configurePickedStorageDirectory(directory: PlatformDirectory): String?

expect fun isStorageDirectoryConfigured(): Boolean
