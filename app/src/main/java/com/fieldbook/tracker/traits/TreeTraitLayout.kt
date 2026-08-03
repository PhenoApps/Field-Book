package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.AttributeSet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CameraActivity
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.provider.GenericFileProvider
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.traits.composables.collect.OverviewMode
import com.fieldbook.tracker.traits.composables.collect.TreeCollectScreen
import com.fieldbook.tracker.traits.composables.collect.TreeCollectStrings
import com.fieldbook.tracker.traits.composables.collect.TreeOverviewSheet
import com.fieldbook.tracker.traits.formats.tree.ChildRule
import com.fieldbook.tracker.traits.formats.tree.DatabaseTraitRefResolver
import com.fieldbook.tracker.traits.formats.tree.Issue
import com.fieldbook.tracker.traits.formats.tree.TreeCodec
import com.fieldbook.tracker.traits.formats.tree.TreeMutations
import com.fieldbook.tracker.traits.formats.tree.TreeNode
import com.fieldbook.tracker.traits.formats.tree.TreePending
import com.fieldbook.tracker.traits.formats.tree.TreeSchema
import com.fieldbook.tracker.traits.formats.tree.TraitRefResolverUtil
import com.fieldbook.tracker.traits.formats.tree.TreeSummary
import com.fieldbook.tracker.traits.formats.tree.TreeValidator
import com.fieldbook.tracker.traits.formats.tree.find
import com.fieldbook.tracker.traits.formats.tree.pathTo
import com.fieldbook.tracker.utilities.TreeDerivedTraitHelper
import com.fieldbook.tracker.utilities.TreeNodePhotoCrop
import com.fieldbook.tracker.utilities.TreePathPortability
import com.fieldbook.tracker.utilities.TreeSchemaLoader
import com.fieldbook.tracker.utilities.TreeSidecarWriter
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.views.CameraTraitSettingsView
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class TreeTraitLayout : BaseTraitLayout {

    companion object {
        const val type = "tree architecture"
        const val REQUEST_TREE_NODE_PHOTO = 206
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var composeView: ComposeView? = null
    private var schema by mutableStateOf<TreeSchema?>(null)
    private var root by mutableStateOf<TreeNode?>(null)
    private var currentNodeId by mutableStateOf<String?>(null)
    private var issues by mutableStateOf<List<Issue>>(emptyList())
    private var showOverview by mutableStateOf(false)
    private var schemaMissing by mutableStateOf(false)
    private var schemaMissingMessage by mutableStateOf<String?>(null)
    private var dirty = false
    private var pending: TreePending? = null
    /** Kept when a flush write fails so loadTree does not silently discard it. */
    private var failedFlushPending: TreePending? = null
    private var pendingPhotoTrait: String? = null
    /** Study photo trait id (not the tree architecture trait) for crop prefs / Camera extras. */
    private var pendingPhotoTraitId: String? = null
    private var pendingPhotoNodeId: String? = null
    private var pendingPhotoUnitId: String? = null
    /** Absolute/cache path waiting on define-crop before sidecar save. */
    private var pendingPhotoMediaPath: String? = null
    private var awaitingNodePhotoCrop: Boolean = false
    private var schemaMismatch by mutableStateOf(false)
    /** Compose-observable lock: edits disabled, navigation/overview still work (04 §8). */
    private var collectLocked by mutableStateOf(false)
    /** Schema TraitRefs that fail resolve at load time (R-20); per-field still shows missing. */
    private var unresolvedTraitRefs by mutableStateOf<List<String>>(emptyList())
    private val debouncer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var debounceTask: java.util.concurrent.ScheduledFuture<*>? = null
    private var composeBound = false
    private var lastMissingSchemaMessage: String? = null

    private val resolver by lazy {
        val db = getDatabase()
        DatabaseTraitRefResolver(db::getTraitByName, db::getTraitByAlias)
    }

    override fun type(): String = type
    override fun layoutId(): Int = R.layout.trait_tree

    override fun init(act: Activity) {
        composeView = findTraitView(R.id.compose_view)
        composeBound = false
        bindCompose()
    }

    override fun onRefresh() {
        flushPending()
        retryFailedFlush()
        super.onRefresh()
    }

    override fun onExit() {
        flushPending()
        retryFailedFlush()
    }

    // R-14: only MissingRequired blocks Collect plot/trait nav.
    // schemaMismatch / schemaMissing stay edit-locked via collectLocked, not block().
    override fun block(): Boolean =
        issues.any { it is Issue.MissingRequired }

    override fun validate(data: String): Boolean {
        // Never fail Collect's validateData→removeTrait path for incomplete required
        // node fields — MissingRequired is enforced via block() / Overview only.
        // Scalar "NA" / blank must not wipe the sidecar URI observation.
        if (data.isBlank() || data.equals("NA", ignoreCase = true)) return true
        if (!data.contains("://") && !data.endsWith(".json", ignoreCase = true)) return true
        return runCatching {
            TreeSidecarWriter.read(context, data.toUri()) != null
        }.getOrDefault(true)
    }

    override fun afterLoadExists(act: CollectActivity, value: String?) {
        super.afterLoadExists(act, value)
        loadTree(act, value)
    }

    override fun afterLoadNotExists(act: CollectActivity) {
        super.afterLoadNotExists(act)
        loadTree(act, null)
    }

    override fun deleteTraitListener() {
        if (isLocked) return
        val p = pending
        val r = root
        if (r != null) {
            TreeSidecarWriter.deleteReferencedMedia(context, currentTrait.name, r)
        }
        p?.let { pendingToDelete ->
            TreeSidecarWriter.delete(context, pendingToDelete)
            TreeDerivedTraitHelper.resolveDerivedTrait(getDatabase(), pendingToDelete.traitId)?.let { derived ->
                val rep = getDatabase().getDefaultRep(pendingToDelete.studyId, pendingToDelete.unitId, derived.id)
                getDatabase().deleteTrait(pendingToDelete.studyId, pendingToDelete.unitId, derived.id, rep)
            }
        }
        removeTrait(currentTrait)
        softDelete()
        // Fresh empty tree so block()/UI are not stuck on null schema after delete.
        loadTree(getCollectActivity(), null)
    }

    override fun afterLoadDefault(act: CollectActivity) {
        super.afterLoadDefault(act)
        loadTree(act, null)
    }

    override fun setNaTraitsText() {
        bindCompose()
    }

    fun handleNodePhotoResult(mediaPath: String?) {
        val traitName = pendingPhotoTrait
        val nodeId = pendingPhotoNodeId ?: currentNodeId
        val expectedUnit = pendingPhotoUnitId
        // Cancel / missing path / missing target: drop pending so a later result
        // cannot attach to a stale node after the user navigated away.
        if (traitName == null || nodeId == null || mediaPath.isNullOrBlank()) {
            clearPendingNodePhoto()
            return
        }
        val liveUnit = getCollectActivity().observationUnit
        if (expectedUnit != null && expectedUnit != liveUnit) {
            clearPendingNodePhoto()
            Utils.makeToast(
                context,
                context.getString(R.string.tree_photo_unit_mismatch),
            )
            return
        }

        val photoTrait = resolvePhotoTrait(traitName)
        when (TreeNodePhotoCrop.decision(photoTrait, getPrefs())) {
            TreeNodePhotoCrop.Decision.NeedsDefinition -> {
                // Keep pending* and ask for ROI; CropImageActivity crops the cache file in place.
                pendingPhotoMediaPath = mediaPath
                awaitingNodePhotoCrop = true
                val traitId = photoTrait?.id ?: pendingPhotoTraitId
                if (traitId.isNullOrBlank()) {
                    finishNodePhotoSave(mediaPath)
                    return
                }
                getCollectActivity().showCropDialog(traitId, Uri.fromFile(File(mediaPath)))
                return
            }
            TreeNodePhotoCrop.Decision.ApplyExistingRoi -> {
                val traitId = photoTrait!!.id.toInt()
                val roi = TreeNodePhotoCrop.readRoi(getPrefs(), traitId)
                TreeNodePhotoCrop.applyCropToPath(context, mediaPath, roi)
                finishNodePhotoSave(mediaPath)
            }
            TreeNodePhotoCrop.Decision.NotRequired -> finishNodePhotoSave(mediaPath)
        }
    }

    /**
     * Called after define-crop dialog / [CropImageActivity] for a pending node capture.
     * When [cropped] is true the cache file was already cropped in place; otherwise save as-is
     * (user declined dialog or cancelled crop — same as Collect camera when ROI is skipped).
     */
    fun handleNodePhotoCropFinished(@Suppress("UNUSED_PARAMETER") cropped: Boolean) {
        if (!awaitingNodePhotoCrop) return
        val path = pendingPhotoMediaPath
        awaitingNodePhotoCrop = false
        pendingPhotoMediaPath = null
        if (path.isNullOrBlank()) {
            clearPendingNodePhoto()
            return
        }
        // Declined/cancelled define-crop: leave ROI unset (next capture asks again).
        // When cropped == true, CropImageActivity already wrote the JPEG in place.
        finishNodePhotoSave(path)
    }

    fun hasPendingNodePhotoCrop(): Boolean = awaitingNodePhotoCrop && !pendingPhotoMediaPath.isNullOrBlank()

    /**
     * Settings cog: same Collect [CameraTraitSettingsView] sheet (Default vs Android camera,
     * Preview, resolutions, Set Crop Region) for the hosted study photo trait.
     */
    fun showNodePhotoSettings(traitName: String) {
        if (isLocked || schemaMismatch) return
        val photoTrait = resolvePhotoTrait(traitName) ?: return
        val settingsView = CameraTraitSettingsView(context, emptyList())
        settingsView.bindHostedPhotoTrait(photoTrait)
        AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.trait_system_photo_settings_title)
            .setPositiveButton(R.string.dialog_ok) { dialog, _ ->
                settingsView.commitChanges()
                dialog.dismiss()
            }
            .setView(settingsView)
            .show()
    }

    /**
     * Define / redefine crop ROI for the hosted study photo trait
     * (same CameraActivity MODE_CROP → CropImageActivity path as Collect photo settings).
     */
    fun requestNodePhotoCropDefinition(traitName: String) {
        if (isLocked || schemaMismatch) return
        val photoTrait = resolvePhotoTrait(traitName) ?: return
        if (!photoTrait.cropImage) return
        val traitId = photoTrait.id.toIntOrNull() ?: return
        getCollectActivity().requestAndCropImage(traitId, true, false)
    }

    fun requestNodePhoto(traitName: String) {
        if (isLocked || schemaMismatch) return
        val act = getCollectActivity()
        val photoTrait = resolvePhotoTrait(traitName)
        if (!checkNodePictureLimit(traitName, photoTrait)) {
            Utils.makeToast(context, context.getString(R.string.traits_create_photo_maximum))
            return
        }
        pendingPhotoTrait = traitName
        pendingPhotoTraitId = photoTrait?.id
        pendingPhotoNodeId = currentNodeId
        pendingPhotoUnitId = act.observationUnit
        pendingPhotoMediaPath = null
        awaitingNodePhotoCrop = false
        val systemCamera = getPrefs().getInt(
            GeneralKeys.CAMERA_SYSTEM,
            R.id.view_trait_photo_settings_camera_custom_rb,
        ) == R.id.view_trait_photo_settings_camera_system_rb
        if (systemCamera) {
            launchSystemCameraForNodePhoto(act)
        } else {
            launchFieldBookCameraForNodePhoto(act, traitName, photoTrait)
        }
    }

    /**
     * Same prefs key as [PhotoTraitLayout]: Default (Field Book CameraActivity) vs Android
     * [MediaStore.ACTION_IMAGE_CAPTURE] writing [AbstractCameraTrait.TEMPORARY_IMAGE_NAME].
     */
    private fun launchSystemCameraForNodePhoto(act: CollectActivity) {
        val file = File(context.cacheDir, AbstractCameraTrait.TEMPORARY_IMAGE_NAME)
        runCatching {
            if (file.exists()) file.delete()
            file.createNewFile()
        }
        val uri = GenericFileProvider.getUriForFile(context, GenericFileProvider.AUTHORITY, file)
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        if (takePictureIntent.resolveActivity(context.packageManager) != null) {
            act.startActivityForResult(takePictureIntent, REQUEST_TREE_NODE_PHOTO)
        } else {
            launchFieldBookCameraForNodePhoto(act, pendingPhotoTrait.orEmpty(), resolvePhotoTrait(pendingPhotoTrait.orEmpty()))
        }
    }

    private fun launchFieldBookCameraForNodePhoto(
        act: CollectActivity,
        traitName: String,
        photoTrait: TraitObject?,
    ) {
        // CameraActivity finishes immediately unless study/obs/trait extras are set
        // (see CameraActivity.onCreate early-exit). Mirror PhotoTraitLayout / VideoTrait.
        // EXTRA_LAUNCHED_FOR_PHOTO_TRAIT disables barcode auto-return and the
        // "no observation" snackbar — without it a live shutter can finish with
        // EXTRA_BARCODE and a null media_path (Collect then clears pending photo).
        // EXTRA_TRAIT_ID must be the *study photo* trait so crop overlay / prefs resolve.
        val traitIdExtra = photoTrait?.id?.toIntOrNull()
            ?: currentTrait.id.toIntOrNull()
            ?: -1
        val intent = Intent(context, CameraActivity::class.java).apply {
            putExtra(CameraActivity.EXTRA_MODE, CameraActivity.MODE_PHOTO)
            putExtra(CameraActivity.EXTRA_SKIP_SAVE, true)
            putExtra(CameraActivity.EXTRA_LAUNCHED_FOR_PHOTO_TRAIT, true)
            putExtra(CameraActivity.EXTRA_TRAIT_NAME, traitName)
            putExtra(CameraActivity.EXTRA_STUDY_ID, act.studyId)
            putExtra(CameraActivity.EXTRA_OBS_UNIT, act.observationUnit)
            putExtra(CameraActivity.EXTRA_TRAIT_ID, traitIdExtra)
        }
        act.startActivityForResult(intent, REQUEST_TREE_NODE_PHOTO)
    }

    /** Same details→limit contract as [AbstractCameraTrait.checkPictureLimit]. */
    private fun checkNodePictureLimit(traitName: String, photoTrait: TraitObject?): Boolean {
        val limit = try {
            photoTrait?.details?.toInt() ?: 0
        } catch (_: Exception) {
            0
        }
        if (limit == 0) return true
        val nodeId = currentNodeId ?: return true
        val existing = root?.let { find(it, nodeId) }?.traits?.get(traitName).orEmpty()
        return TreePathPortability.splitMediaRefs(existing).size < limit
    }

    private fun finishNodePhotoSave(mediaPath: String) {
        val traitName = pendingPhotoTrait
        val nodeId = pendingPhotoNodeId ?: currentNodeId
        val expectedUnit = pendingPhotoUnitId
        if (traitName == null || nodeId == null) {
            clearPendingNodePhoto()
            return
        }
        val liveUnit = getCollectActivity().observationUnit
        val relative = TreeSidecarWriter.saveNodePhoto(
            context,
            currentTrait.name,
            expectedUnit ?: liveUnit,
            mediaPath,
        )
        if (relative == null) {
            clearPendingNodePhoto()
            Utils.makeToast(context, context.getString(R.string.tree_photo_save_failed))
            return
        }
        // Gate mutate: if locked/mismatch, drop the file we just saved.
        if (isLocked || schemaMismatch || root == null) {
            TreeSidecarWriter.deleteReferencedMediaValue(context, currentTrait.name, relative)
            clearPendingNodePhoto()
            return
        }
        // Collect multi-shot = multiple observation rows; sidecar packs paths newline-separated.
        val node = root?.let { find(it, nodeId) }
        val prior = node?.traits?.get(traitName).orEmpty()
        val existing = TreePathPortability.splitMediaRefs(prior)
        val photoTrait = resolvePhotoTrait(traitName)
        val limit = try {
            photoTrait?.details?.toInt() ?: 0
        } catch (_: Exception) {
            0
        }
        if (limit > 0 && existing.size >= limit) {
            TreeSidecarWriter.deleteReferencedMediaValue(context, currentTrait.name, relative)
            clearPendingNodePhoto()
            Utils.makeToast(context, context.getString(R.string.traits_create_photo_maximum))
            return
        }
        val combined = TreePathPortability.joinMediaRefs(existing + relative)
        mutate { TreeMutations.setTrait(it, nodeId, traitName, combined, now()) }
        clearPendingNodePhoto()
    }

    private fun resolvePhotoTrait(traitName: String): TraitObject? = resolver.resolve(traitName)

    private fun clearPendingNodePhoto() {
        pendingPhotoTrait = null
        pendingPhotoTraitId = null
        pendingPhotoNodeId = null
        pendingPhotoUnitId = null
        pendingPhotoMediaPath = null
        awaitingNodePhotoCrop = false
    }

    private fun loadTree(act: CollectActivity, value: String?) {
        debounceTask?.cancel(false)
        debounceTask = null
        // Do not discard a prior plot's failed flush — retry later via onExit/onRefresh.
        root = null
        currentNodeId = null
        issues = emptyList()
        dirty = false
        pending = null
        clearPendingNodePhoto()
        showOverview = false
        schemaMissing = false
        schemaMissingMessage = null
        schemaMismatch = false
        unresolvedTraitRefs = emptyList()

        schema = TreeSchemaLoader.load(context, currentTrait.resourceFile)
        if (schema == null) {
            schemaMissing = true
            schemaMissingMessage =
                if (currentTrait.resourceFile.isNullOrBlank()) {
                    context.getString(R.string.tree_missing_schema)
                } else {
                    context.getString(R.string.tree_missing_schema_imported)
                }
            syncCollectLocked()
            bindCompose()
            return
        }

        unresolvedTraitRefs = TraitRefResolverUtil.unresolvedTraitNames(schema!!, resolver)

        val existing = value?.takeIf { it.isNotBlank() }?.let { TreeSidecarWriter.read(context, it.toUri()) }
        if (existing != null && existing.schemaId != schema!!.id) {
            schemaMismatch = true
        }
        root = existing?.root ?: TreeCodec.newRoot(schema!!, now())
        currentNodeId = root?.id
        pending = if (existing != null) {
            TreePending(
                unitId = act.observationUnit,
                studyId = act.studyId,
                traitId = currentTrait.id,
                traitName = currentTrait.name,
                rep = getCollectInputView().getRep(),
                root = existing.root,
                capturedAt = existing.captured,
                sourceApp = existing.sourceApp,
                existingUri = value,
            )
        } else {
            capturePending(act)
        }
        // R-10: never revalidate a mismatched observation under the current schema.
        if (schemaMismatch) {
            issues = emptyList()
        } else {
            revalidate()
        }
        syncCollectLocked()
        bindCompose()
    }

    private fun syncCollectLocked() {
        collectLocked = isLocked || schemaMismatch
    }

    private fun capturePending(act: CollectActivity): TreePending? {
        val r = root ?: return null
        val p = TreePending(
            unitId = act.observationUnit,
            studyId = act.studyId,
            traitId = currentTrait.id,
            traitName = currentTrait.name,
            rep = getCollectInputView().getRep(),
            root = r,
            capturedAt = now(),
            sourceApp = "Field Book",
            existingUri = pending?.existingUri,
        )
        pending = p
        return p
    }

    /** @return false when a dirty pending could not be written (C1). */
    private fun flushPending(): Boolean {
        if (!dirty) return true
        if (schemaMismatch) return true // read-only: do not rewrite under a mismatched schema
        val p = pending ?: return true
        val sch = schema ?: return false
        debounceTask?.cancel(false)
        debounceTask = null
        val writtenFor = p
        val uri = TreeSidecarWriter.write(context, writtenFor, sch.id)
        if (uri == Uri.EMPTY) {
            failedFlushPending = writtenFor
            com.fieldbook.tracker.utilities.Utils.makeToast(
                context,
                context.getString(R.string.tree_flush_failed),
            )
            return false
        }
        persistObservation(
            studyId = writtenFor.studyId,
            unitId = writtenFor.unitId,
            traitId = writtenFor.traitId,
            value = uri.toString(),
            rep = writtenFor.rep,
        )
        val summary = TreeSummary.compute(writtenFor.root, sch)
        persistOrDropDerivedSummary(writtenFor, summary)
        dirty = false
        // Only drop a failed pending that this flush successfully replaced.
        val priorFailed = failedFlushPending
        if (priorFailed == null || pendingMatches(priorFailed, writtenFor)) {
            failedFlushPending = null
        }
        if (pending === writtenFor) {
            pending?.existingUri = uri.toString()
        }
        return true
    }

    private fun pendingMatches(a: TreePending, b: TreePending): Boolean =
        a.unitId == b.unitId &&
            a.studyId == b.studyId &&
            a.traitId == b.traitId &&
            a.rep == b.rep

    private fun retryFailedFlush() {
        val failed = failedFlushPending ?: return
        val failedTrait = getDatabase().getTraitById(failed.traitId)
            ?: getDatabase().allTraitObjects.firstOrNull { it.id == failed.traitId }
        val sch = failedTrait?.resourceFile?.let { TreeSchemaLoader.load(context, it) }
            ?: schema
            ?: TreeSchemaLoader.load(context, currentTrait.resourceFile)
            ?: return
        val uri = TreeSidecarWriter.write(context, failed, sch.id)
        if (uri == Uri.EMPTY) {
            com.fieldbook.tracker.utilities.Utils.makeToast(
                context,
                context.getString(R.string.tree_flush_failed),
            )
            return
        }
        persistObservation(
            studyId = failed.studyId,
            unitId = failed.unitId,
            traitId = failed.traitId,
            value = uri.toString(),
            rep = failed.rep,
        )
        val summary = TreeSummary.compute(failed.root, sch)
        persistOrDropDerivedSummary(failed, summary)
        failedFlushPending = null
    }

    /**
     * Summary companion is created only when [TreeSummary.hasContent]; empty/"0" summaries
     * are not persisted and existing empty companions are dropped.
     */
    private fun persistOrDropDerivedSummary(pending: TreePending, summary: TreeSummary) {
        val db = getDatabase()
        if (!summary.hasContent()) {
            TreeDerivedTraitHelper.dropEmptySummaryCompanion(db, pending.traitId)
            return
        }
        val derived = TreeDerivedTraitHelper.ensureSummaryCompanion(db, pending.traitId) ?: return
        val derivedRep = db.getDefaultRep(pending.studyId, pending.unitId, derived.id)
        persistObservation(
            studyId = pending.studyId,
            unitId = pending.unitId,
            traitId = derived.id,
            value = summary.podTotal.toString(),
            rep = derivedRep,
        )
    }

    /** Persist without using Collect's live plot (R-02). */
    private fun persistObservation(
        studyId: String,
        unitId: String,
        traitId: String,
        value: String,
        rep: String?,
    ) {
        val db = getDatabase()
        val effectiveRep = rep?.takeIf { it.isNotBlank() } ?: db.getDefaultRep(studyId, unitId, traitId)
        val existing = db.getRepeatedValues(studyId, unitId, traitId)
            .firstOrNull { it.rep == effectiveRep }
        if (existing != null) {
            db.updateObservationValue(existing.internal_id_observation, value)
        } else {
            val person = runCatching { getCollectActivity().person }.getOrDefault("")
            val location = runCatching { getCollectActivity().locationByPreferences }.getOrNull()
            db.insertObservation(
                unitId,
                traitId,
                value,
                person,
                location,
                "",
                studyId,
                null,
                null,
                null,
                effectiveRep,
            )
        }
    }

    private fun onTreeMutated(newRoot: TreeNode) {
        if (schemaMismatch) return
        root = newRoot
        pending?.root = newRoot
        dirty = true
        revalidate()
        debounceWrite()
        // TTS is navigation-only (ascend/jump/add), not every trait keystroke.
    }

    private fun mutate(transform: (TreeNode) -> TreeNode) {
        if (schemaMismatch || isLocked) return
        val current = root ?: return
        onTreeMutated(transform(current))
    }

    private fun debounceWrite() {
        val snapshot = pending ?: return
        val sch = schema ?: return
        debounceTask?.cancel(false)
        debounceTask = debouncer.schedule({
            val uri = TreeSidecarWriter.write(context, snapshot, sch.id)
            if (uri != Uri.EMPTY && pending === snapshot) {
                snapshot.existingUri = uri.toString()
            }
        }, 500, TimeUnit.MILLISECONDS)
    }

    private fun revalidate() {
        val r = root ?: return
        val sch = schema ?: return
        issues = TreeValidator.validate(r, sch, resolver)
    }

    private fun resetState() {
        debounceTask?.cancel(false)
        debounceTask = null
        root = null
        currentNodeId = null
        issues = emptyList()
        dirty = false
        pending = null
        clearPendingNodePhoto()
        showOverview = false
        schemaMissing = false
        schemaMissingMessage = null
        schemaMismatch = false
        unresolvedTraitRefs = emptyList()
        schema = null
        syncCollectLocked()
    }

    private fun now(): String = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun maybeTts() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean(PreferenceKeys.TTS_LANGUAGE_ENABLED, false)) return
        val node = root?.let { find(it, currentNodeId ?: "") } ?: return
        val path = pathTo(root!!, node.id)
        val spoken = path.joinToString(", ") { n ->
            val type = schema?.typeOf(n.nodeType)?.displayName ?: n.nodeType
            "$type ${spokenIndex(n.idx)}"
        }
        getCollectActivity().triggerTts(spoken)
    }

    private fun spokenIndex(n: Int): String = when (n) {
        1 -> "one"
        2 -> "two"
        3 -> "three"
        4 -> "four"
        5 -> "five"
        6 -> "six"
        7 -> "seven"
        8 -> "eight"
        9 -> "nine"
        10 -> "ten"
        else -> n.toString()
    }

    fun openOverviewForBlockedNav() {
        val firstMissing = issues.filterIsInstance<Issue.MissingRequired>().firstOrNull()
        if (firstMissing != null) {
            currentNodeId = firstMissing.nodeId
        }
        showOverview = true
        bindCompose()
    }

    private fun softAdvance() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean(PreferenceKeys.PRIMARY_SOUND, false)) {
            getCollectActivity().getSoundHelper().playAdvance()
        }
    }

    private fun softDelete() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean(PreferenceKeys.DELETE_OBSERVATION_SOUND, false)) {
            getCollectActivity().getSoundHelper().playDelete()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun bindCompose() {
        val view = composeView ?: return
        val missingMsg = schemaMissingMessage ?: context.getString(R.string.tree_missing_schema)
        if (composeBound && lastMissingSchemaMessage == missingMsg) return
        composeBound = true
        lastMissingSchemaMessage = missingMsg

        val strings = TreeCollectStrings(
            noIssues = context.getString(R.string.tree_no_issues),
            childrenTitle = { count -> context.getString(R.string.tree_children, count) },
            overview = context.getString(R.string.tree_overview),
            missingSchema = missingMsg,
            deleteChild = context.getString(R.string.tree_delete_child),
            confirmDelete = context.getString(R.string.tree_confirm_delete_subtree),
            confirmDeleteSubtreeTitle = context.getString(R.string.tree_delete_subtree_title),
            confirmDeleteSubtreeMessage = context.getString(R.string.tree_delete_subtree_message),
            ascend = context.getString(R.string.tree_ascend),
            ascendDescription = context.getString(R.string.tree_ascend_description),
            lockedBanner = context.getString(R.string.tree_locked_navigate_only),
            breadcrumbEllipsisDescription = context.getString(R.string.tree_breadcrumb_ellipsis),
        )

        view.setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = AppTheme.colors.background,
                    contentColor = AppTheme.colors.text.primary,
                ) {
                    val sch = schema
                    val r = root
                    val nodeId = currentNodeId
                    val missing = schemaMissing || sch == null || r == null || nodeId == null
                    val mismatch = schemaMismatch
                    val locked = collectLocked

                    if (missing) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = strings.missingSchema,
                                style = AppTheme.typography.bodyStyle,
                                color = AppTheme.colors.text.primary,
                            )
                        }
                        return@Surface
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (mismatch) {
                            Text(
                                text = context.getString(R.string.tree_schema_mismatch),
                                style = AppTheme.typography.bodyStyle,
                                color = AppTheme.colors.status.error,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }

                        val unresolved = unresolvedTraitRefs
                        if (unresolved.isNotEmpty()) {
                            val banner = if (unresolved.size == 1) {
                                context.getString(R.string.tree_unresolved_trait_ref, unresolved.first())
                            } else {
                                context.getString(
                                    R.string.tree_unresolved_trait_refs,
                                    unresolved.joinToString(", "),
                                )
                            }
                            Text(
                                text = banner,
                                style = AppTheme.typography.bodyStyle,
                                color = AppTheme.colors.status.error,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }

                        TreeCollectScreen(
                            schema = sch,
                            root = r,
                            currentNodeId = nodeId,
                            issues = issues,
                            locked = locked,
                            onNavigate = {
                                currentNodeId = it
                                maybeTts()
                            },
                            onAddChild = { rule -> addChild(rule) },
                            onDeleteChild = { childId ->
                                softDelete()
                                val victim = root?.let { find(it, childId) }
                                if (victim != null) {
                                    TreeSidecarWriter.deleteReferencedMedia(context, currentTrait.name, victim)
                                }
                                mutate { TreeMutations.deleteNode(it, childId) }
                            },
                            onTraitChange = { trait, value ->
                                val id = currentNodeId ?: return@TreeCollectScreen
                                val prior = root?.let { find(it, id) }?.traits?.get(trait)
                                if (!prior.isNullOrBlank() && prior != value &&
                                    com.fieldbook.tracker.utilities.TreePathPortability.looksLikeMediaRef(prior)
                                ) {
                                    TreeSidecarWriter.deleteReferencedMediaValue(
                                        context,
                                        currentTrait.name,
                                        prior,
                                    )
                                }
                                mutate { TreeMutations.setTrait(it, id, trait, value, now()) }
                            },
                            onRequestPhoto = { requestNodePhoto(it) },
                            onRequestPhotoCropSettings = { showNodePhotoSettings(it) },
                            onShowOverview = { showOverview = true },
                            resolveTrait = { resolver.resolve(it) },
                            strings = strings,
                            expandVertically = true,
                        )
                    }

                    if (showOverview) {
                        // Nearly full-screen sheet (skip partial peek) with padding inside the sheet.
                        var overviewMode by remember { mutableStateOf(OverviewMode.Graph) }
                        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        ModalBottomSheet(
                            onDismissRequest = { showOverview = false },
                            sheetState = sheetState,
                            sheetGesturesEnabled = overviewMode != OverviewMode.Graph,
                            containerColor = AppTheme.colors.background,
                        ) {
                            TreeOverviewSheet(
                                root = r,
                                schema = sch,
                                currentNodeId = nodeId,
                                issues = issues,
                                summary = TreeSummary.compute(r, sch),
                                resolveTrait = { resolver.resolve(it) },
                                onJumpTo = {
                                    currentNodeId = it
                                    showOverview = false
                                    maybeTts()
                                },
                                onModeChange = { overviewMode = it },
                                modifier = Modifier.fillMaxHeight(0.95f),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun addChild(rule: ChildRule) {
        if (schemaMismatch || isLocked) return
        val parentId = currentNodeId ?: return
        val sch = schema ?: return
        val current = root ?: return
        val (newRoot, newId) = TreeMutations.addChild(current, parentId, rule, sch, now())
        softAdvance()
        onTreeMutated(newRoot)
        currentNodeId = newId
        maybeTts()
    }

    override fun refreshLayout(onNew: Boolean?) {
        flushPending()
        retryFailedFlush()
        val act = getCollectActivity()
        val rep = getCollectInputView().getRep()
        val value = getDatabase().getRepeatedValues(act.studyId, act.observationUnit, currentTrait.id)
            .firstOrNull { it.rep == rep }
            ?.value
        loadTree(act, value)
        syncCollectLocked()
    }
}
