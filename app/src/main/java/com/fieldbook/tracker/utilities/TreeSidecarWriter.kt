package com.fieldbook.tracker.utilities

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.formats.tree.TreeCodec
import com.fieldbook.tracker.traits.formats.tree.TreeObservation
import com.fieldbook.tracker.traits.formats.tree.TreePending
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TreeSidecarWriter {

    private const val TAG = "TreeSidecarWriter"

    fun fileStamp(): String =
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(Date())

    private fun TreePending.portableCopy(): TreePending =
        copy(root = TreePathPortability.portableizeTree(root, traitName))

    fun write(
        context: Context,
        pending: TreePending,
        schemaId: String,
        dirOverride: DocumentFile? = null,
    ): Uri {
        val trait = FileUtil.sanitizeFileName(pending.traitName)
        val plot = FileUtil.sanitizeFileName(pending.unitId).replace("/", "_")
        val stamp = fileStamp()
        val base = "${plot}_${trait}_$stamp"

        val dir = resolveMediaDir(context, trait, dirOverride) ?: return Uri.EMPTY
        val existing = pending.existingUri?.toUri()?.let { existingDoc(context, it) }
        val target = existing?.takeIf { it.exists() }
            ?: dir.createFile("application/json", "$base.json")
            ?: return Uri.EMPTY

        val payload = TreeCodec.encodeSidecar(schemaId, pending.portableCopy())
        openWrite(context, target)?.use {
            it.write(payload.toByteArray())
        } ?: return Uri.EMPTY

        // MTG string lives inside the JSON sidecar (`mtg` field). Do not write companion
        // `.mtg` / `.mtg.txt` files — SAF createFile("text/plain") collides into
        // `name.mtg (N).txt` junk that pollutes media export bundles.
        deleteOrphanMtgCompanions(dir, target.name?.removeSuffix(".json") ?: base)

        pending.existingUri = target.uri.toString()
        return target.uri
    }

    fun read(context: Context, uri: Uri): TreeObservation? = try {
        openRead(context, uri)?.use { input ->
            TreeCodec.decodeObservation(input.bufferedReader().readText())
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed reading tree sidecar $uri", e)
        null
    }

    fun delete(context: Context, pending: TreePending) {
        pending.existingUri?.toUri()?.let { uri ->
            existingDoc(context, uri)?.delete()
            val leaf = TreePathPortability.toRelative(uri.toString())
                .substringAfterLast('/')
                .ifBlank { uri.lastPathSegment.orEmpty() }
            val stem = leaf.removeSuffix(".json")
            if (stem.isNotBlank()) {
                resolveMediaDir(context, FileUtil.sanitizeFileName(pending.traitName), null)
                    ?.let { deleteOrphanMtgCompanions(it, stem) }
            }
        }
    }

    /** True for legacy companion MTG leaves written beside a JSON sidecar. */
    fun isOrphanMtgCompanionName(fileName: String): Boolean {
        val n = fileName.lowercase()
        return n.endsWith(".mtg") ||
            n.endsWith(".mtg.txt") ||
            Regex("""\.mtg \(\d+\)\.txt$""").containsMatchIn(n)
    }

    private fun deleteOrphanMtgCompanions(dir: DocumentFile, stem: String) {
        if (stem.isBlank()) return
        dir.listFiles().forEach { file ->
            val name = file.name ?: return@forEach
            if (!file.isFile) return@forEach
            if (name == "$stem.mtg" ||
                name == "$stem.mtg.txt" ||
                (name.startsWith("$stem.mtg") && isOrphanMtgCompanionName(name))
            ) {
                file.delete()
            }
        }
    }

    /** Best-effort delete of media files referenced under a deleted subtree. */
    fun deleteReferencedMedia(context: Context, traitName: String, node: com.fieldbook.tracker.traits.formats.tree.TreeNode) {
        val dir = nodeMediaDirectory(context, traitName) ?: return
        fun walk(n: com.fieldbook.tracker.traits.formats.tree.TreeNode) {
            n.traits.values.forEach { v ->
                deleteReferencedMediaValue(context, traitName, v, dir)
            }
            n.children.forEach { walk(it) }
        }
        walk(node)
    }

    /** Delete media leaf/leaves for a trait value (single path or newline-packed multi-shot). */
    fun deleteReferencedMediaValue(
        context: Context,
        traitName: String,
        value: String,
        dirOverride: DocumentFile? = null,
    ) {
        if (!TreePathPortability.looksLikeMediaRef(value)) return
        TreePathPortability.splitMediaRefs(value).forEach { part ->
            deleteOneMediaLeaf(context, traitName, part, dirOverride)
        }
    }

    private fun deleteOneMediaLeaf(
        context: Context,
        traitName: String,
        value: String,
        dirOverride: DocumentFile?,
    ) {
        if (value.isBlank()) return
        val relative = TreePathPortability.toRelative(value).ifBlank { value }
        val leaf = relative.substringAfterLast('/').ifBlank { relative }
        if (leaf.isBlank()) return
        val folderFromPath = relative
            .takeIf { it.contains('/') }
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() && it != leaf }
        val dir = dirOverride
            ?: folderFromPath?.let { nodeMediaDirectory(context, it) }
            ?: nodeMediaDirectory(context, traitName)
            ?: return
        findMediaLeaf(dir, leaf)?.delete()
    }

    fun nodeMediaDirectory(
        context: Context,
        traitName: String,
        dirOverride: DocumentFile? = null,
    ): DocumentFile? =
        resolveMediaDir(context, FileUtil.sanitizeFileName(traitName), dirOverride)

    /**
     * Locate a media file by stored leaf name. Tolerates SAF
     * `createFile("image/jpeg", "foo.jpg")` → display name `foo.jpg.jpg`, and mild
     * prefix collisions (`foo.jpg` vs `foo (1).jpg`).
     */
    fun findMediaLeaf(dir: DocumentFile, leaf: String): DocumentFile? {
        if (leaf.isBlank()) return null
        dir.findFile(leaf)?.takeIf { it.exists() && it.isFile }?.let { return it }
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return null
        files.firstOrNull { it.isFile && it.name == leaf }?.let { return it }
        // Double extension from MIME+displayName (image/jpeg + "x.jpg" → "x.jpg.jpg")
        files.firstOrNull { it.isFile && (it.name == "$leaf.jpg" || it.name == "$leaf.jpeg") }
            ?.let { return it }
        val stem = leaf.substringBeforeLast('.').takeIf { it.isNotBlank() && it != leaf } ?: return null
        return files.firstOrNull { file ->
            val name = file.name ?: return@firstOrNull false
            file.isFile && (name.startsWith("$leaf") || name.startsWith("$stem."))
        }
    }

    /** True when [candidate] is an acceptable on-disk name for stored [leaf]. */
    fun mediaLeafMatches(leaf: String, candidate: String): Boolean {
        if (leaf.isBlank() || candidate.isBlank()) return false
        if (candidate == leaf) return true
        if (candidate == "$leaf.jpg" || candidate == "$leaf.jpeg") return true
        val stem = leaf.substringBeforeLast('.').takeIf { it.isNotBlank() && it != leaf } ?: return false
        return candidate.startsWith(leaf) || candidate.startsWith("$stem.")
    }

    fun saveNodePhoto(
        context: Context,
        traitName: String,
        plotId: String,
        sourcePath: String,
        dirOverride: DocumentFile? = null,
    ): String? {
        val dir = nodeMediaDirectory(context, traitName, dirOverride) ?: return null
        val plot = FileUtil.sanitizeFileName(plotId).replace("/", "_")
        val name = "${plot}_node_${fileStamp()}.jpg"
        // Match AbstractCameraTrait: "*/*" avoids SAF appending a second ".jpg" for image/jpeg.
        val target = dir.createFile("*/*", name) ?: return null
        val source = DocumentFile.fromFile(File(sourcePath))
        // Prefer DocumentTreeUtil.copy; fall back to raw bytes for file:// targets.
        val copied = runCatching {
            org.phenoapps.utils.BaseDocumentTreeUtil.copy(context, source, target)
            true
        }.getOrElse {
            openWrite(context, target)?.use { out ->
                FileInputStream(sourcePath).use { input -> input.copyTo(out) }
            } != null
        }
        if (!copied) {
            target.delete()
            return null
        }
        // Persist the real DocumentFile display name (may differ from [name] on some providers).
        val actualName = target.name?.takeIf { it.isNotBlank() } ?: name
        val relative = TreePathPortability.mediaRelative(traitName, actualName)
        return relative.ifBlank { actualName }
    }

    /**
     * Prefer SAF plot_data/<field>/<trait>. When the intro storage definer was skipped
     * (common in headless instrumented runs), fall back to app-specific external files.
     */
    internal fun resolveMediaDir(
        context: Context,
        sanitizedTrait: String,
        dirOverride: DocumentFile?,
    ): DocumentFile? {
        dirOverride?.let { return it }
        DocumentTreeUtil.getFieldMediaDirectory(context, sanitizedTrait)?.let { return it }
        return appFilesMediaDir(context, sanitizedTrait)
    }

    private fun appFilesMediaDir(context: Context, sanitizedTrait: String): DocumentFile? {
        val field = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(GeneralKeys.FIELD_FILE, "")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val dir = File(
            context.getExternalFilesDir(null),
            "plot_data/${FileUtil.sanitizeFileName(field)}/$sanitizedTrait",
        )
        if (!dir.exists() && !dir.mkdirs()) return null
        return DocumentFile.fromFile(dir)
    }

    private fun existingDoc(context: Context, uri: Uri): DocumentFile? =
        when (uri.scheme) {
            "file" -> uri.path?.let { DocumentFile.fromFile(File(it)) }
            else -> DocumentFile.fromSingleUri(context, uri)
        }

    private fun openWrite(context: Context, file: DocumentFile): OutputStream? {
        runCatching { context.contentResolver.openOutputStream(file.uri, "wt") }.getOrNull()?.let { return it }
        val path = file.uri.path ?: return null
        return runCatching { FileOutputStream(File(path)) }.getOrNull()
    }

    private fun openRead(context: Context, uri: Uri): InputStream? {
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()?.let { return it }
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            return runCatching { FileInputStream(File(path)) }.getOrNull()
        }
        return null
    }
}
