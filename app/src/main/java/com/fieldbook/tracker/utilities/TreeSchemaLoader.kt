package com.fieldbook.tracker.utilities

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.tree.TreeCodec
import com.fieldbook.tracker.traits.formats.tree.TreeSchema
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object TreeSchemaLoader {

    private const val TAG = "TreeSchemaLoader"

    fun load(context: Context, resourceUri: String?): TreeSchema? {
        if (resourceUri.isNullOrBlank()) return null
        return try {
            readRaw(context, resourceUri)?.let { TreeCodec.decodeSchema(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun readText(context: Context, resourceUri: String?): String? {
        if (resourceUri.isNullOrBlank()) return null
        return readRaw(context, resourceUri)
    }

    /**
     * Overwrites an attached schema resource when writable.
     * Returns false for blank refs, asset paths, unloadable targets, or I/O failure.
     */
    fun writeSchema(context: Context, resourceRef: String?, schema: TreeSchema): Boolean {
        if (resourceRef.isNullOrBlank()) return false
        if (isReadOnlyAssetRef(resourceRef)) return false
        val bytes = TreeCodec.encodeSchema(schema).toByteArray()
        if (resourceRef.contains("://")) {
            return runCatching {
                context.contentResolver.openOutputStream(resourceRef.toUri(), "wt")?.use {
                    it.write(bytes)
                } != null
            }.getOrDefault(false)
        }
        val leaf = schemaLeaf(resourceRef)
        if (leaf.isBlank()) return false
        val resources = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_resources) ?: return false
        val file = resources.findFile(leaf) ?: findRecursive(resources, leaf) ?: return false
        return runCatching {
            context.contentResolver.openOutputStream(file.uri, "wt")?.use {
                it.write(bytes)
            } != null
        }.getOrDefault(false)
    }

    /**
     * After a study trait rename, rewrite matching [TraitRef]s in loadable, writable
     * tree-architecture resource schemas (R-18). Skips unloadable / asset-backed schemas.
     */
    fun repairTraitRefsAfterRename(
        context: Context,
        treeTraits: List<TraitObject>,
        oldName: String,
        newName: String,
    ): TraitRefRenameRepairResult {
        if (oldName.isBlank() || oldName == newName) {
            return TraitRefRenameRepairResult()
        }
        var updated = 0
        var unwritable = 0
        var unloadable = 0
        treeTraits
            .filter {
                it.format.equals(Formats.TREE_ARCHITECTURE.getDatabaseName(), ignoreCase = true) &&
                    !it.resourceFile.isNullOrBlank()
            }
            .forEach { treeTrait ->
                val ref = treeTrait.resourceFile
                val schema = load(context, ref)
                if (schema == null) {
                    unloadable++
                    return@forEach
                }
                if (!schema.referencesTraitName(oldName)) return@forEach
                val rewritten = schema.renameTraitRefs(oldName, newName)
                if (writeSchema(context, ref, rewritten)) {
                    updated++
                    Log.w(
                        TAG,
                        "Updated TraitRefs in schema for tree trait '${treeTrait.name}' " +
                            "after rename '$oldName' → '$newName'",
                    )
                } else {
                    unwritable++
                    Log.w(
                        TAG,
                        "Trait renamed '$oldName' → '$newName' but could not update " +
                            "TraitRefs in schema for tree trait '${treeTrait.name}' (unwritable/missing file)",
                    )
                }
            }
        return TraitRefRenameRepairResult(
            schemasUpdated = updated,
            schemasUnwritable = unwritable,
            schemasUnloadable = unloadable,
        )
    }

    fun saveEmbeddedSchema(
        context: Context,
        traitName: String,
        resourceRef: String?,
        schemaText: String,
    ): String? {
        val resources = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_resources) ?: return null
        val leaf = preferredImportFileName(resourceRef, traitName)
        val target = resources.findFile(leaf) ?: resources.createFile("*/*", leaf) ?: return null
        context.contentResolver.openOutputStream(target.uri, "wt")?.use {
            it.write(schemaText.toByteArray())
        } ?: return null
        return target.name ?: leaf
    }

    internal fun isReadOnlyAssetRef(resourceRef: String): Boolean {
        val normalized = resourceRef.removePrefix("/").lowercase()
        return normalized.startsWith("assets/") ||
            (normalized.startsWith("trait/") && !resourceRef.contains("://"))
    }

    private fun readRaw(context: Context, resourceUri: String): String? {
        // content:// or file://
        if (resourceUri.contains("://")) {
            runCatching {
                context.contentResolver.openInputStream(resourceUri.toUri())?.use {
                    it.bufferedReader().readText()
                }
            }.getOrNull()?.let { return it }
        }
        val leaf = schemaLeaf(resourceUri)
        if (leaf.isBlank()) {
            return null
        }
        // direct file name under resources document tree
        val resources = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_resources)
        val file = resources?.findFile(leaf)
            ?: findRecursive(resources, leaf)
        if (file != null && file.exists()) {
            return context.contentResolver.openInputStream(file.uri)?.use {
                it.bufferedReader().readText()
            }
        }
        // App-specific external files (SAF fallback used by Constructor save).
        val fallback = java.io.File(
            context.getExternalFilesDir(null),
            context.getString(R.string.dir_resources),
        )
        val local = java.io.File(fallback, leaf)
        if (local.isFile) {
            return runCatching { local.readText() }.getOrNull()
        }
        // asset path e.g. trait/tree_soy_arch_sample.trt or assets/...
        val assetPath = resourceUri.removePrefix("assets/").removePrefix("/")
        runCatching {
            context.assets.open(assetPath).bufferedReader().use { return it.readText() }
        }
        return null
    }

    internal fun schemaLeaf(resourceUri: String): String =
        if (resourceUri.contains("://")) {
            URLDecoder.decode(resourceUri, StandardCharsets.UTF_8.name()).substringAfterLast('/')
        } else {
            resourceUri.substringAfterLast('/')
        }

    internal fun preferredImportFileName(resourceRef: String?, traitName: String): String {
        val leaf = resourceRef?.takeIf { it.isNotBlank() }?.let(::schemaLeaf).orEmpty()
        if (leaf.isNotBlank()) return leaf
        return "${FileUtil.sanitizeFileName(traitName)}_schema.trt"
    }

    private fun findRecursive(dir: DocumentFile?, name: String): DocumentFile? {
        if (dir == null) return null
        dir.listFiles().forEach { child ->
            if (child.isFile && child.name == name) return child
            if (child.isDirectory) {
                findRecursive(child, name)?.let { return it }
            }
        }
        return null
    }
}

/** Outcome of rewriting TraitRefs after a study trait rename (R-18). */
data class TraitRefRenameRepairResult(
    val schemasUpdated: Int = 0,
    val schemasUnwritable: Int = 0,
    val schemasUnloadable: Int = 0,
) {
    val touchedAny: Boolean
        get() = schemasUpdated > 0 || schemasUnwritable > 0
}
