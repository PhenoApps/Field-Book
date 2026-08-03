package com.fieldbook.tracker.utilities

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.fieldbook.tracker.traits.formats.tree.TreeNode

/**
 * Relative portable paths for tree sidecars and node media.
 * Prefer `sanitizedTrait/filename` so zip layout matches trait media folders.
 * Runtime may still use SAF content:// URIs for DocumentFile I/O.
 */
object TreePathPortability {

    /** Must match ExportUtil / DocumentTreeUtil trait media folder names. */
    fun sanitizeTraitFolder(traitName: String): String =
        FileUtil.sanitizeFileName(traitName).ifBlank { "tree" }

    /** Basename only from URI or path. */
    fun toRelative(uri: Uri?, fallbackName: String? = null): String {
        if (uri == null) return fallbackName.orEmpty()
        val s = uri.toString()
        if (!s.startsWith("content:") && !s.startsWith("file:")) {
            return basename(s).ifBlank { s }
        }
        val candidates = listOfNotNull(
            uri.lastPathSegment,
            s.substringAfterLast('/').takeIf { it.isNotBlank() },
            fallbackName,
            s,
        )
        for (raw in candidates) {
            val name = basename(unquote(raw))
            if (isCleanBasename(name)) return name
        }
        return basename(unquote(fallbackName ?: s)).ifBlank { fallbackName.orEmpty() }
    }

    fun toRelative(uriString: String?): String {
        if (uriString.isNullOrBlank()) return ""
        return if (uriString.startsWith("content:") || uriString.startsWith("file:")) {
            toRelative(uriString.toUri(), uriString.substringAfterLast('/'))
        } else if (uriString.startsWith("/")) {
            // Absolute filesystem path → basename only
            basename(uriString)
        } else if (uriString.contains('/')) {
            // Already trait/file or path — keep as-is for zip-relative media refs
            uriString
        } else {
            uriString
        }
    }

    /** Zip-relative form: `sanitizedTrait/filename`. Rewrites stale folder prefixes after rename. */
    fun mediaRelative(traitName: String, fileNameOrUri: String): String {
        val folder = sanitizeTraitFolder(traitName)
        val raw = toRelative(fileNameOrUri)
        if (raw.isBlank()) return ""
        if (raw.contains('/')) {
            val leaf = raw.substringAfterLast('/')
            if (leaf.isBlank()) return "$folder/$raw"
            return "$folder/$leaf"
        }
        return "$folder/$raw"
    }

    fun portableizeTree(root: TreeNode, traitName: String): TreeNode {
        val folder = sanitizeTraitFolder(traitName)
        fun walk(node: TreeNode): TreeNode {
            val traits = node.traits.mapValues { (_, v) ->
                when {
                    v.isBlank() -> v
                    v.contains('\n') && looksLikeMediaRef(v) ->
                        joinMediaRefs(splitMediaRefs(v).map { mediaRelative(traitName, it) })
                    v.startsWith("content:") || v.startsWith("file:") -> mediaRelative(traitName, v)
                    v.startsWith("/") -> mediaRelative(traitName, v) // absolute FS path
                    looksLikeMediaRef(v) -> mediaRelative(traitName, v)
                    else -> v
                }
            }.toMutableMap()
            return node.copy(
                traits = traits,
                children = node.children.map { walk(it) }.toMutableList(),
            )
        }
        return walk(root)
    }

    fun resolve(context: Context, mediaDir: DocumentFile?, relativeOrUri: String): Uri? {
        if (relativeOrUri.isBlank()) return null
        if (relativeOrUri.startsWith("content:") || relativeOrUri.startsWith("file:")) {
            return relativeOrUri.toUri()
        }
        val name = relativeOrUri.substringAfterLast('/')
        return mediaDir?.findFile(name)?.uri
    }

    fun isRelative(path: String): Boolean =
        path.isNotBlank() &&
            !path.startsWith("content:") &&
            !path.startsWith("file:") &&
            !path.startsWith("/")

    fun looksLikeMediaRef(name: String): Boolean {
        // Multi-shot node photos pack several relative paths in one sidecar string (newline-separated).
        if (name.contains('\n')) {
            return splitMediaRefs(name).any { looksLikeMediaRef(it) }
        }
        if (name.startsWith("content:") || name.startsWith("file:")) return true
        if (name.startsWith("/")) return true // absolute filesystem path
        val lower = name.lowercase()
        // Require a media-like extension or _node_ marker — not any slash (dates like 2026/07/27).
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.contains("_node_")
        ) {
            return true
        }
        if (name.contains('/')) {
            val leaf = name.substringAfterLast('/').lowercase()
            return leaf.endsWith(".jpg") || leaf.endsWith(".jpeg") || leaf.endsWith(".png") ||
                leaf.endsWith(".mp4") || leaf.endsWith(".mov") || leaf.contains("_node_")
        }
        return false
    }

    /**
     * Sidecar trait map is one string per key; Collect photo multi-shot is multiple observation
     * rows. Closest pack: newline-separated relative paths (filenames never contain `\n`).
     */
    fun splitMediaRefs(value: String): List<String> =
        value.split('\n').map { it.trim() }.filter { it.isNotBlank() }

    fun joinMediaRefs(paths: Iterable<String>): String =
        paths.map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n")

    private fun basename(raw: String): String =
        raw.substringAfterLast('/').substringAfterLast('\\').trim()

    private fun isCleanBasename(name: String): Boolean =
        name.isNotBlank() && !name.contains(':') && '%' !in name

    private fun unquote(raw: String): String {
        var s = raw
        repeat(2) {
            s = s.replace("%2F", "/", ignoreCase = true)
                .replace("%3A", ":", ignoreCase = true)
                .replace("%20", " ")
        }
        return s
    }
}
