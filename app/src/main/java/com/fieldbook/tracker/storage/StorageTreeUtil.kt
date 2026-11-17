package com.fieldbook.tracker.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import org.phenoapps.utils.BaseDocumentTreeUtil.Companion.isVolumeRoot
import org.phenoapps.utils.KeyUtil

/**
 * Handles the folder picked with ActivityResultContracts.OpenDocumentTree as the storage root.
 *
 * The picker needs no runtime permission. PhenoLib's SecureDefineDocumentTree is not used because on
 * API 33+ it requests READ_MEDIA_IMAGES/READ_MEDIA_VIDEO, which Field Book does not declare; the system
 * denies them without a prompt and the definer closes before the picker opens.
 */
object StorageTreeUtil {

    // matches StorageDefinerFragment.defaultAppName
    const val DEFAULT_APP_FOLDER = "fieldBook"

    /**
     * Persists the picked uri and returns the folder to use as the storage root:
     * the uri itself, or the default app folder inside it when a volume root was picked.
     */
    fun resolvePickedTree(ctx: Context, uri: Uri): Uri? {

        persistSinglePermission(ctx, uri)

        //older devices can choose the volume root, getRoot expects a default folder to be saved in that case
        return if (uri.isVolumeRoot()) defineDefaultFolder(ctx, uri) else uri
    }

    /**
     * Persists the chosen uri and releases any previously chosen ones,
     * getRoot only reads the first persisted permission.
     */
    private fun persistSinglePermission(ctx: Context, uri: Uri) {

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        with(ctx.contentResolver) {

            takePersistableUriPermission(uri, flags)

            persistedUriPermissions.forEach {
                if (it.uri != uri) {
                    releasePersistableUriPermission(it.uri, flags)
                }
            }
        }
    }

    /**
     * Finds or creates the default app folder within a volume root and saves it as the default tree uri.
     */
    private fun defineDefaultFolder(ctx: Context, volumeRoot: Uri): Uri? {

        val root = DocumentFile.fromTreeUri(ctx, volumeRoot) ?: return null

        val folder = root.findFile(DEFAULT_APP_FOLDER)?.takeIf { it.isDirectory }
            ?: root.createDirectory(DEFAULT_APP_FOLDER)
            ?: return null

        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putString(KeyUtil(ctx).defaultTreeUri, folder.uri.toString())
            .apply()

        return folder.uri
    }
}
