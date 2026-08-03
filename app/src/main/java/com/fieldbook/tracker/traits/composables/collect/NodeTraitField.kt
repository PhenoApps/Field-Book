package com.fieldbook.tracker.traits.composables.collect

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.ImageAdapter
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.BaseTraitLayout
import com.fieldbook.tracker.traits.NodeTraitValueSession
import com.fieldbook.tracker.traits.TraitLayoutFactory
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.tree.Issue
import com.fieldbook.tracker.traits.formats.tree.TraitRef
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.BitmapLoader
import com.fieldbook.tracker.utilities.DocumentTreeUtil
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.TreePathPortability
import com.fieldbook.tracker.utilities.TreeSidecarWriter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

/** Amber for non-blocking field warnings (out of range, bad category, etc.). */
internal val TreeFieldWarningColor = Color(0xFFF9A825)

/**
 * Hosts a study [TraitObject] referenced by a [TraitRef] by inflating the real
 * Field Book trait layout XML and a fresh [BaseTraitLayout] controller with a
 * [NodeTraitValueSession] (sidecar values — never the tree trait CollectInputView).
 *
 * Photo formats keep CameraActivity capture and inflate [R.layout.trait_tree_photo] chrome
 * (not Hilt CameraX [com.fieldbook.tracker.traits.PhotoTraitLayout]).
 */
@Composable
fun NodeTraitField(
    traitRef: TraitRef,
    trait: TraitObject?,
    value: String,
    locked: Boolean,
    /** Include in Compose key so hosts remount/rebind when navigating nodes. */
    nodeId: String,
    onValueChange: (String) -> Unit,
    onRequestPhoto: () -> Unit,
    onRequestPhotoCropSettings: () -> Unit = {},
    /** Field-level validation for this TraitRef (red blocking / amber warning). */
    fieldIssues: List<Issue> = emptyList(),
) {
    val label = trait?.name ?: traitRef.traitName
    val format = trait?.format.orEmpty()
    val required = traitRef.requiredOverride == true
    val blocking = fieldIssues.any { it.isFieldBlocking() }
    val warning = !blocking && fieldIssues.any { it.isFieldWarning() }
    val titleColor = when {
        blocking -> AppTheme.colors.status.error
        warning -> TreeFieldWarningColor
        else -> AppTheme.colors.text.primary
    }
    val requiredColor = when {
        blocking -> AppTheme.colors.status.error
        else -> AppTheme.colors.text.secondary
    }
    val title = buildString {
        append(label)
        if (required) append(" *")
        if (format.isNotEmpty()) {
            append(" · ")
            append(format)
        }
    }

    // Session-local expand state keyed by trait name (reversible hide under the name).
    var expanded by remember(label) { mutableStateOf(true) }
    val expandLabel = if (expanded) {
        stringResource(R.string.brapi_server_collapse_content_description)
    } else {
        stringResource(R.string.brapi_server_expand_content_description)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .semantics { contentDescription = "$title, $expandLabel" }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = titleColor,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = expandLabel,
                tint = AppTheme.colors.text.secondary,
            )
        }
        if (trait == null) {
            Text(
                text = stringResource(R.string.tree_trait_missing, traitRef.traitName),
                color = if (blocking) AppTheme.colors.status.error else AppTheme.colors.text.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
        } else {
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    when {
                        isPhotoFormat(format) ->
                            key(nodeId, trait.id, "photo", locked) {
                                PhotoChromeHost(
                                    value = value,
                                    traitName = trait.name ?: traitRef.traitName,
                                    locked = locked,
                                    onRequestPhoto = onRequestPhoto,
                                    onRequestPhotoSettings = onRequestPhotoCropSettings,
                                    onValueChange = onValueChange,
                                )
                            }
                        else ->
                            key(nodeId, trait.id, format) {
                                CollectLayoutHost(
                                    trait = trait,
                                    value = value,
                                    locked = locked,
                                    onValueChange = onValueChange,
                                )
                            }
                    }
                }
            }
        }
        if (required) {
            Text(
                text = stringResource(R.string.tree_required_field),
                color = requiredColor,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        fieldIssues.forEach { issue ->
            val hintColor = when {
                issue.isFieldBlocking() -> AppTheme.colors.status.error
                else -> TreeFieldWarningColor
            }
            Text(
                text = issue.message,
                color = hintColor,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun isPhotoFormat(format: String): Boolean {
    val key = format.lowercase()
    return key == Formats.CAMERA.getDatabaseName().lowercase() ||
        key == Formats.BASE_PHOTO.getDatabaseName().lowercase() ||
        key == "photo"
}

@Composable
private fun CollectLayoutHost(
    trait: TraitObject,
    value: String,
    locked: Boolean,
    onValueChange: (String) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            val activity = context.findActivity()
            // Prefer CollectActivity so layouts that cast getContext() (scale, GNSS, …) work.
            val hostContext: Context = activity ?: context
            val format = trait.format.orEmpty()
            val controller = TraitLayoutFactory.create(format, hostContext)
            // Fail closed: unknown / unhostable formats must not soft-mis-host as Text.
            if (controller == null) {
                return@AndroidView TextView(context).apply {
                    text = context.getString(R.string.tree_format_unsupported)
                    setTextColor(android.graphics.Color.parseColor("#B00020"))
                }
            }
            val chrome = LayoutInflater.from(context).inflate(controller.layoutId(), null, false)
            chrome.visibility = View.VISIBLE
            chrome.layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            val session = NodeTraitValueSession(context, trait, value, locked, onValueChange)
            // Mount the session value buffer so DisplayValue formats (location, numeric,
            // counter, stop_watch) show captured text — Collect's carousel EditText is not used.
            val buffer = session.inputView()
            buffer.layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            val wrapper = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                addView(buffer)
                addView(chrome)
            }
            controller.attachSession(session)
            if (activity != null) {
                controller.init(activity, chrome)
                // loadLayout wires format-specific chrome (StopWatch Compose timer, Percent
                // seekbar bounds, Scale BLE UI, …). Node session path inside loadLayout
                // calls afterLoadExists from the detached buffer — do not skip it.
                runCatching { controller.loadLayout() }
                    .onFailure {
                        // Some hardware formats may throw without Collect services; still show chrome.
                        controller.loadNodeValue(value)
                    }
            }
            wrapper.tag = HostState(controller, session, loadedValue = value, loadedLocked = locked)
            wrapper
        },
        update = { root ->
            val state = root.tag as? HostState ?: return@AndroidView
            state.session.rebind(value, locked, onValueChange)
            state.controller.setLockedState(locked)
            // TextTraitLayout.loadNodeValue → selectEditText → requestFocus/showKeyboard.
            // Calling that on every Compose update invalidates layout and loops forever
            // (breaks ActivityScenario idle sync / crashes headless Collect proofs).
            if (state.loadedValue != value || state.loadedLocked != locked) {
                state.controller.loadNodeValue(value)
                state.loadedValue = value
                state.loadedLocked = locked
            }
        },
    )
}

private data class HostState(
    val controller: BaseTraitLayout,
    val session: NodeTraitValueSession,
    var loadedValue: String? = null,
    var loadedLocked: Boolean? = null,
)

@Composable
private fun PhotoChromeHost(
    value: String,
    traitName: String,
    locked: Boolean,
    onRequestPhoto: () -> Unit,
    onRequestPhotoSettings: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    val latest = rememberUpdatedState(Triple(value, traitName, locked))
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            LayoutInflater.from(context).inflate(R.layout.trait_tree_photo, null, false).apply {
                visibility = View.VISIBLE
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                val rv = findViewById<RecyclerView>(R.id.trait_photo_rv)
                rv.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, true)
                val noop = object : ImageAdapter.ImageItemHandler {
                    override fun onItemClicked(model: ImageAdapter.Model) = Unit
                    override fun onItemDeleted(model: ImageAdapter.Model) {
                        val (currentValue, name, isLocked) = latest.value
                        if (isLocked) return
                        val paths = TreePathPortability.splitMediaRefs(currentValue)
                        val remaining = paths.filterNot { pathMatchesModel(context, it, name, model) }
                        val removed = paths.filter { pathMatchesModel(context, it, name, model) }
                        removed.forEach {
                            TreeSidecarWriter.deleteReferencedMediaValue(context, name, it)
                        }
                        onValueChange(TreePathPortability.joinMediaRefs(remaining))
                    }
                    override fun onItemLongClicked(model: ImageAdapter.Model) = Unit
                }
                rv.adapter = ImageAdapter(context, noop)
                findViewById<FloatingActionButton>(R.id.capture).setOnClickListener {
                    if (isEnabled) onRequestPhoto()
                }
                findViewById<FloatingActionButton>(R.id.tree_node_photo_settings).setOnClickListener {
                    if (isEnabled) onRequestPhotoSettings()
                }
                tag = traitName
            }
        },
        update = { view ->
            view.isEnabled = !locked
            view.findViewById<FloatingActionButton>(R.id.capture).isEnabled = !locked
            val settings = view.findViewById<FloatingActionButton>(R.id.tree_node_photo_settings)
            // Always show System Photo Settings (Default/Android, preview, crop) for photo traits.
            settings.visibility = View.VISIBLE
            settings.isEnabled = !locked
            view.tag = traitName
            bindPhotoPreview(view, value, traitName)
        },
    )
    if (value.isNotBlank()) {
        val labels = TreePathPortability.splitMediaRefs(value).map { it.substringAfterLast('/') }
        Text(
            text = labels.joinToString(", "),
            color = AppTheme.colors.text.secondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun pathMatchesModel(
    context: Context,
    path: String,
    traitName: String,
    model: ImageAdapter.Model,
): Boolean {
    val modelUri = model.uri ?: return false
    val uri = resolveNodePhotoUri(context, path, traitName) ?: return false
    return uri == modelUri ||
        path.substringAfterLast('/') == modelUri.substringAfterLast('/')
}

internal fun bindPhotoPreview(host: View, value: String, traitName: String = "") {
    val rv = host.findViewById<RecyclerView>(R.id.trait_photo_rv)
    val fallback = host.findViewById<ImageView>(R.id.tree_node_photo_preview)
    val adapter = rv.adapter as? ImageAdapter
    if (value.isBlank()) {
        adapter?.submitList(emptyList())
        fallback.visibility = View.GONE
        fallback.setImageDrawable(null)
        return
    }
    val paths = TreePathPortability.splitMediaRefs(value)
    val models = paths.mapNotNull { path ->
        val uri = resolveNodePhotoUri(host.context, path, traitName) ?: return@mapNotNull null
        ImageAdapter.Model(
            id = path.hashCode(),
            type = ImageAdapter.Type.IMAGE,
            uri = uri,
        )
    }
    if (models.isNotEmpty()) {
        adapter?.submitList(models)
        // Multi-shot: always use the horizontal ImageAdapter (Collect photo RV).
        // Single-shot keeps the compact ImageView fallback when decode succeeds.
        if (models.size > 1) {
            fallback.visibility = View.GONE
            fallback.setImageDrawable(null)
            rv.visibility = View.VISIBLE
        } else {
            val firstUri = models.first().uri
            val preview = firstUri?.let { decodeNodePhotoPreview(host.context, it) }
            if (preview != null) {
                fallback.visibility = View.VISIBLE
                fallback.setImageBitmap(preview)
                rv.visibility = View.GONE
            } else {
                fallback.visibility = View.GONE
                rv.visibility = View.VISIBLE
            }
        }
    } else {
        adapter?.submitList(emptyList())
        rv.visibility = View.GONE
        fallback.visibility = View.VISIBLE
        fallback.setImageBitmap(
            placeholderThumb(host.context, paths.firstOrNull()?.substringAfterLast('/') ?: value),
        )
    }
}

/**
 * Resolve a portable `traitFolder/filename`, basename, file://, or content:// value to a
 * loadable URI. [traitName] is the study trait (or tree trait folder) used when the value
 * has no folder prefix.
 *
 * Folder from a `folder/file.jpg` value always wins over [traitName] (preview may pass the
 * study trait while media lives under the tree architecture folder).
 *
 * Important: [TreePathPortability.toRelative] keeps `folder/file.jpg` intact — always take
 * the basename for [DocumentFile.findFile] / filesystem name matches. Lookup uses
 * [TreeSidecarWriter.findMediaLeaf] so SAF double-extension leaves still resolve.
 */
internal fun resolveNodePhotoUri(context: Context, value: String, traitName: String = ""): String? {
    if (value.startsWith("content:") || value.startsWith("file:")) return value
    val asFile = File(value)
    if (asFile.isAbsolute && asFile.exists()) return asFile.toUri().toString()

    val relative = TreePathPortability.toRelative(value).ifBlank { value }
    val leaf = relative.substringAfterLast('/').ifBlank { relative }
    if (leaf.isBlank()) return null

    val folderFromPath = relative
        .takeIf { it.contains('/') }
        ?.substringBefore('/')
        ?.takeIf { it.isNotBlank() && it != leaf }
    val folder = folderFromPath
        ?: traitName.takeIf { it.isNotBlank() }?.let { TreePathPortability.sanitizeTraitFolder(it) }
        ?: TreePathPortability.sanitizeTraitFolder("tree")
    val sanitizedFolder = FileUtil.sanitizeFileName(folder)

    DocumentTreeUtil.getFieldMediaDirectory(context, sanitizedFolder)
        ?.let { TreeSidecarWriter.findMediaLeaf(it, leaf) }
        ?.uri
        ?.toString()
        ?.let { return it }

    // App-files / SAF via writer — findMediaLeaf tolerates empty SAF dirs that shadow files.
    TreeSidecarWriter.nodeMediaDirectory(context, sanitizedFolder)
        ?.let { TreeSidecarWriter.findMediaLeaf(it, leaf) }
        ?.uri
        ?.toString()
        ?.let { return it }

    // Walk plot_data so basename-only values still resolve when study traitName ≠ tree folder,
    // and so empty SAF trait dirs do not hide app-files media (including foo.jpg.jpg).
    val appDir = File(context.getExternalFilesDir(null), "plot_data")
    if (appDir.exists()) {
        appDir.walkTopDown().firstOrNull { file ->
            file.isFile &&
                TreeSidecarWriter.mediaLeafMatches(leaf, file.name) &&
                file.parentFile?.name == sanitizedFolder
        }?.let { return it.toUri().toString() }
        appDir.walkTopDown().firstOrNull { file ->
            file.isFile && TreeSidecarWriter.mediaLeafMatches(leaf, file.name)
        }?.let { return it.toUri().toString() }
    }
    File(context.cacheDir, leaf).takeIf { it.exists() }?.let { return it.toUri().toString() }
    File(context.cacheDir, "$leaf.jpg").takeIf { it.exists() }?.let { return it.toUri().toString() }
    return null
}

/** Portrait [BitmapLoader.getPreview] uses DocumentsContract thumbnails only — fall back. */
private fun decodeNodePhotoPreview(context: Context, uriString: String): Bitmap? {
    runCatching { BitmapLoader.getPreview(context, uriString) }.getOrNull()?.let { return it }
    runCatching {
        context.contentResolver.openInputStream(uriString.toUri())?.use { input ->
            BitmapFactory.decodeStream(input)
        }
    }.getOrNull()?.let { return it }
    return decodeFilePreview(uriString)
}

private fun decodeFilePreview(uriString: String): Bitmap? = runCatching {
    val path = uriString.toUri().path ?: return null
    val file = File(path)
    if (!file.exists()) return null
    BitmapFactory.decodeFile(file.absolutePath)
}.getOrNull()

private fun placeholderThumb(context: Context, @Suppress("UNUSED_PARAMETER") label: String): Bitmap {
    // Solid empty preview only — never paint the filename onto the bitmap (filename stays
    // in the Compose caption under the chrome).
    val w = context.resources.getDimensionPixelSize(R.dimen.camera_preview_portrait_width)
    val h = context.resources.getDimensionPixelSize(R.dimen.camera_preview_portrait_height)
    val bmp = Bitmap.createBitmap(w.coerceAtLeast(120), h.coerceAtLeast(160), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.parseColor("#CFD8DC"))
    return bmp
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
