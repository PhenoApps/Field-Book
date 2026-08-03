package com.fieldbook.tracker.traits.composables.constructor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import com.fieldbook.tracker.traits.composables.TreeActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import android.widget.Toast
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.composables.collect.NodeTraitField
import com.fieldbook.tracker.traits.TraitLayoutFactory
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.tree.ChildRule
import com.fieldbook.tracker.traits.formats.tree.EdgeType
import com.fieldbook.tracker.traits.formats.tree.NodeTypeDef
import com.fieldbook.tracker.traits.formats.tree.TraitRef
import com.fieldbook.tracker.traits.formats.tree.TreeSchema
import com.fieldbook.tracker.traits.formats.tree.TreeSchemaValidator
import com.fieldbook.tracker.ui.components.widgets.AppIcon
import com.fieldbook.tracker.ui.dialogs.builder.AppAlertDialog
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.FileUtil
import sh.calvin.reorderable.ReorderableColumn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalContext

/** Formats Constructor will not Attach — must stay ⊆ [TraitLayoutFactory] unhostable. */
private val UnsupportedFormats = setOf(
    // Video / CameraX external cams need Collect-coupled hosts; photo uses PhotoChromeHost.
    Formats.VIDEO.getDatabaseName(),
    Formats.USB_CAMERA.getDatabaseName(),
    Formats.GO_PRO.getDatabaseName(),
    Formats.CANON.getDatabaseName(),
    Formats.TREE_ARCHITECTURE.getDatabaseName(),
    Formats.TREE_SUMMARY.getDatabaseName(),
).map { it.lowercase() }.toSet()

/** Formats that Constructor will not Attach (no reinvented trait editor). */
fun isUnsupportedTreePaletteFormat(format: String): Boolean =
    format.lowercase() in UnsupportedFormats

/**
 * Photo is factory-unhostable on purpose ([TraitLayoutFactory]) but Attachable via
 * PhotoChromeHost in node Collect — keep it in the palette.
 */
fun isTreePalettePhotoFormat(format: String): Boolean {
    val key = format.lowercase()
    return key == Formats.CAMERA.getDatabaseName().lowercase() ||
        key == Formats.BASE_PHOTO.getDatabaseName().lowercase() ||
        key == "photo"
}

/**
 * Names that old soybean sample schemas invented as TraitRefs without study traits.
 * Never offer these in the Attach palette — even if a stale study still lists them.
 * (Legitimate user traits named "Height"/"Color" stay attachable; only the invented trio.)
 */
private val ForgottenSoybeanPhantomNames = setOf(
    "Node position",
    "Seed count",
    "Pod photo",
)

fun isForgottenSoybeanPhantomName(name: String?): Boolean =
    !name.isNullOrBlank() && ForgottenSoybeanPhantomNames.any { it.equals(name, ignoreCase = true) }

/**
 * Study traits the Constructor palette may offer for Attach.
 * Topology shells (tree architecture / summary) and Collect-coupled cams stay out;
 * photo is allowed (chrome host). No invented tree-* formats.
 */
fun isAttachableTreePaletteTrait(trait: TraitObject): Boolean {
    if (isForgottenSoybeanPhantomName(trait.name)) return false
    val format = trait.format.orEmpty()
    if (format.isBlank() || isUnsupportedTreePaletteFormat(format)) return false
    if (isTreePalettePhotoFormat(format)) return true
    return TraitLayoutFactory.isNodeHostable(format)
}

/** Filter [DataHelper.allTraitObjects] (or harness fixtures) down to Attach-eligible study traits. */
fun filterAttachableStudyTraits(traits: List<TraitObject>): List<TraitObject> =
    traits.filter(::isAttachableTreePaletteTrait)

/**
 * Attach an existing study trait by name only ([TraitRef.traitName]).
 * Palette supplies [TraitObject]s from the study; Constructor never defines new trait formats here.
 */
fun NodeTypeDef.attachTraitByName(traitName: String): NodeTypeDef {
    if (traitRefs.any { it.traitName == traitName }) return this
    return copy(
        traitRefs = traitRefs + TraitRef(
            traitName = traitName,
            order = traitRefs.size,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreeConstructorScreen(
    initialSchema: TreeSchema,
    availableTraits: List<TraitObject>,
    traitNameHint: String = "",
    onSave: (schema: TreeSchema, leafFileName: String) -> Unit,
    onCancel: () -> Unit,
    initialSelectedTypeName: String? = null,
    initialShowAddType: Boolean = false,
    initialShowPalette: Boolean = false,
    /** Existing resource leaf to overwrite; null/blank → new timestamped file on save. */
    existingResourceLeaf: String? = null,
    /** Fired when Blank/Soy clears overwrite so DialogFragment forces a new leaf. */
    onTemplateReset: (() -> Unit)? = null,
    /** When true, block type delete (schema already has observations). */
    isSchemaUsedByObservations: (schemaId: String) -> Boolean = { false },
    /** When true, lay out full scrollable height (for Roborazzi full-length screenshots). */
    expandVertically: Boolean = false,
) {
    val context = LocalContext.current
    var schema by remember { mutableStateOf(initialSchema) }
    var overwriteLeaf by remember { mutableStateOf(existingResourceLeaf) }
    var selectedTypeName by remember {
        mutableStateOf(
            initialSelectedTypeName
                ?: schema.nodeTypes.firstOrNull()?.name
                ?: schema.rootType,
        )
    }
    var showPalette by remember { mutableStateOf(initialShowPalette) }
    var showAddType by remember { mutableStateOf(initialShowAddType) }
    var pendingDeleteType by remember { mutableStateOf<String?>(null) }
    var bumpVersionOnDelete by remember { mutableStateOf(true) }
    var showSaveSummary by remember { mutableStateOf(false) }
    var pendingSaveLeaf by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }

    val validation = TreeSchemaValidator.validate(schema)
    val selected = schema.nodeTypes.firstOrNull { it.name == selectedTypeName }
        ?: schema.nodeTypes.firstOrNull()
    val fileStem = FileUtil.sanitizeFileName(schema.id).ifBlank { "tree_schema" }
    fun resolveSaveLeaf(): String =
        overwriteLeaf?.takeIf { it.isNotBlank() } ?: run {
            // Stable leaf for Roborazzi full-length captures (no wall-clock in the hint).
            if (expandVertically) {
                "${fileStem}_screenshot.json"
            } else {
                val stamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
                "${fileStem}_$stamp.json"
            }
        }
    val saveLeafLabel = pendingSaveLeaf ?: resolveSaveLeaf()

    Box(
        modifier = if (expandVertically) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = if (expandVertically) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tree_edit_schema),
                    style = AppTheme.typography.titleStyle,
                    color = AppTheme.colors.text.primary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.semantics {
                        contentDescription = "Close schema editor"
                    },
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.tree_close),
                        tint = AppTheme.colors.text.primary,
                    )
                }
            }
            HorizontalDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (expandVertically) {
                            Modifier
                        } else {
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        },
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                // Match by id only — size==1 && rootType=="root" falsely selected any customized one-type schema.
                selected = schema.id == blankSchema().id,
                onClick = {
                    schema = blankSchema()
                    selectedTypeName = schema.rootType
                    // Starters must not silently overwrite the previously opened leaf.
                    overwriteLeaf = null
                    onTemplateReset?.invoke()
                },
                label = { Text(stringResource(R.string.tree_schema_blank)) },
            )
            FilterChip(
                selected = schema.id == defaultSoybeanSchema().id,
                onClick = {
                    schema = defaultSoybeanSchema()
                    selectedTypeName = schema.rootType
                    overwriteLeaf = null
                    onTemplateReset?.invoke()
                },
                label = { Text(stringResource(R.string.tree_schema_soybean)) },
            )
        }

        OutlinedTextField(
            value = schema.name,
            onValueChange = { schema = schema.copy(name = it) },
            label = { Text(stringResource(R.string.tree_schema_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = schema.id,
            onValueChange = { schema = schema.copy(id = it.trim().replace(' ', '_')) },
            label = { Text(stringResource(R.string.tree_schema_id)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = schema.version.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.let { schema = schema.copy(version = it.coerceAtLeast(1)) }
            },
            label = { Text(stringResource(R.string.tree_schema_version)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        TreeActionButton(
            text = stringResource(R.string.tree_bump_version),
            onClick = { schema = schema.copy(version = schema.version + 1) },
        )

        Text(
            stringResource(R.string.tree_node_types),
            style = AppTheme.typography.titleStyle,
            color = AppTheme.colors.text.primary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            schema.nodeTypes.forEach { type ->
                val mark = if (type.name == schema.rootType) " ★" else ""
                val chipLabel = "${type.displayName} (${type.cls})$mark"
                FilterChip(
                    selected = type.name == selectedTypeName,
                    onClick = { selectedTypeName = type.name },
                    label = { Text(chipLabel) },
                    modifier = Modifier.semantics {
                        contentDescription = "Node type: ${type.displayName} (${type.cls})"
                    },
                )
            }
            IconButton(onClick = { showAddType = true }) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.tree_add_node_type),
                    tint = AppTheme.colors.text.primary,
                )
            }
        }

        selected?.let { type ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.tree_traits_attached),
                    style = AppTheme.typography.subheadingStyle,
                    color = AppTheme.colors.text.primary,
                )
                val attachLabel = stringResource(R.string.tree_attach_trait)
                TreeActionButton(
                    text = attachLabel,
                    onClick = { showPalette = true },
                    modifier = Modifier
                        .testTag("tree_attach_trait")
                        .semantics { contentDescription = attachLabel },
                )
            }

            val refs = type.traitRefs.sortedBy { it.order }
            TraitReorderList(
                refs = refs,
                onReorder = { from, to -> schema = schema.swapTraitOrder(type.name, from, to) },
                onToggleRequired = { ref ->
                    schema = schema.updateType(type.name) { t ->
                        t.copy(
                            traitRefs = t.traitRefs.map {
                                if (it.traitName == ref.traitName) {
                                    it.copy(requiredOverride = !(ref.requiredOverride ?: false))
                                } else it
                            },
                        )
                    }
                },
                onDetach = { ref ->
                    schema = schema.updateType(type.name) { t ->
                        t.copy(traitRefs = t.traitRefs.filterNot { it.traitName == ref.traitName })
                    }
                },
            )

            if (expandVertically && showPalette) {
                TraitPalettePanel(
                    availableTraits = availableTraits,
                    selected = selected,
                    search = search,
                    onSearchChange = { search = it },
                    onAttach = { trait ->
                        val t = type
                        schema = schema.updateType(t.name) { node ->
                            node.attachTraitByName(trait.name)
                        }
                    },
                    onDone = { showPalette = false; search = "" },
                )
            }

            Text(
                stringResource(R.string.tree_connects_to),
                style = AppTheme.typography.subheadingStyle,
                color = AppTheme.colors.text.primary,
            )
            val precedesTitle = stringResource(R.string.tree_precedes)
            val bearsTitle = stringResource(R.string.tree_bears)
            var showPrecedesPicker by remember { mutableStateOf(false) }
            var showBearsPicker by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TreeActionButton(
                    text = "${stringResource(R.string.tree_add_connection)} · ${stringResource(R.string.tree_precedes_short)}",
                    onClick = { showPrecedesPicker = true },
                    modifier = Modifier.semantics {
                        contentDescription = "Add connection ($precedesTitle)"
                    },
                )
                TreeActionButton(
                    text = "${stringResource(R.string.tree_add_connection)} · ${stringResource(R.string.tree_bears_short)}",
                    onClick = { showBearsPicker = true },
                    modifier = Modifier.semantics {
                        contentDescription = "Add connection ($bearsTitle)"
                    },
                )
            }
            if (showPrecedesPicker) {
                ConnectionTargetPickerDialog(
                    title = precedesTitle,
                    allTypes = schema.nodeTypes,
                    attached = type.allowedChildren.filter { it.edge == EdgeType.PRECEDES }
                        .map { it.nodeType }.toSet(),
                    onPick = { target, label ->
                        schema = schema.updateType(type.name) { t ->
                            t.copy(
                                allowedChildren = t.allowedChildren +
                                    ChildRule(target, EdgeType.PRECEDES, label),
                            )
                        }
                        showPrecedesPicker = false
                    },
                    onDismiss = { showPrecedesPicker = false },
                )
            }
            if (showBearsPicker) {
                ConnectionTargetPickerDialog(
                    title = bearsTitle,
                    allTypes = schema.nodeTypes,
                    attached = type.allowedChildren.filter { it.edge == EdgeType.BEARS }
                        .map { it.nodeType }.toSet(),
                    onPick = { target, label ->
                        schema = schema.updateType(type.name) { t ->
                            t.copy(
                                allowedChildren = t.allowedChildren +
                                    ChildRule(target, EdgeType.BEARS, label),
                            )
                        }
                        showBearsPicker = false
                    },
                    onDismiss = { showBearsPicker = false },
                )
            }
            ConnectionZone(
                title = precedesTitle,
                color = AppTheme.colors.primary,
                edge = EdgeType.PRECEDES,
                rules = type.allowedChildren.filter { it.edge == EdgeType.PRECEDES },
                onUpdateLabel = { rule, label ->
                    schema = schema.updateType(type.name) { t ->
                        t.copy(
                            allowedChildren = t.allowedChildren.map {
                                if (it == rule) it.copy(label = label) else it
                            },
                        )
                    }
                },
                onRemove = { rule ->
                    schema = schema.updateType(type.name) { t ->
                        t.copy(allowedChildren = t.allowedChildren - rule)
                    }
                },
                onReorder = { from, to ->
                    schema = schema.reorderChildRules(type.name, EdgeType.PRECEDES, from, to)
                },
            )
            ConnectionZone(
                title = bearsTitle,
                color = AppTheme.colors.accent,
                edge = EdgeType.BEARS,
                rules = type.allowedChildren.filter { it.edge == EdgeType.BEARS },
                onUpdateLabel = { rule, label ->
                    schema = schema.updateType(type.name) { t ->
                        t.copy(
                            allowedChildren = t.allowedChildren.map {
                                if (it == rule) it.copy(label = label) else it
                            },
                        )
                    }
                },
                onRemove = { rule ->
                    schema = schema.updateType(type.name) { t ->
                        t.copy(allowedChildren = t.allowedChildren - rule)
                    }
                },
                onReorder = { from, to ->
                    schema = schema.reorderChildRules(type.name, EdgeType.BEARS, from, to)
                },
            )

            OutlinedTextField(
                value = type.displayName,
                onValueChange = { display ->
                    schema = schema.updateType(type.name) { it.copy(displayName = display) }
                },
                label = { Text(stringResource(R.string.tree_display_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = type.name,
                onValueChange = { newName ->
                    val cleaned = newName.lowercase().replace(' ', '_')
                    if (cleaned.isBlank() || schema.nodeTypes.any { it.name == cleaned && it.name != type.name }) {
                        return@OutlinedTextField
                    }
                    schema = schema.renameType(type.name, cleaned)
                    selectedTypeName = cleaned
                },
                label = { Text(stringResource(R.string.tree_internal_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = type.cls,
                onValueChange = { cls ->
                    schema = schema.updateType(type.name) { it.copy(cls = cls.take(1).uppercase()) }
                },
                label = { Text(stringResource(R.string.tree_class_letter)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = type.maxChildren?.toString().orEmpty(),
                onValueChange = { raw ->
                    val trimmed = raw.trim()
                    schema = schema.updateType(type.name) {
                        it.copy(
                            // blank = unlimited; 0 = none (not unlimited)
                            maxChildren = if (trimmed.isEmpty()) {
                                null
                            } else {
                                trimmed.toIntOrNull()?.coerceAtLeast(0)
                            },
                        )
                    }
                },
                label = { Text(stringResource(R.string.tree_max_children)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.tree_max_children_unlimited)) },
                supportingText = { Text(stringResource(R.string.tree_max_children_none)) },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (type.name != schema.rootType) {
                    TreeActionButton(
                        text = stringResource(R.string.tree_set_as_root),
                        onClick = { schema = schema.copy(rootType = type.name) },
                    )
                    TreeActionButton(
                        text = stringResource(R.string.tree_remove_node_type),
                        onClick = { pendingDeleteType = type.name },
                    )
                } else {
                    Text(
                        stringResource(R.string.tree_is_root),
                        style = AppTheme.typography.subheadingStyle,
                        color = AppTheme.colors.text.secondary,
                    )
                }
            }

            HorizontalDivider()
            Text(
                stringResource(R.string.tree_preview),
                style = AppTheme.typography.subheadingStyle,
                color = AppTheme.colors.text.primary,
            )
            type.traitRefs.sortedBy { it.order }.forEach { ref ->
                val trait = availableTraits.firstOrNull {
                    it.name == ref.traitName || it.alias == ref.traitName
                }
                NodeTraitField(
                    traitRef = ref,
                    trait = trait,
                    value = "",
                    locked = true,
                    nodeId = "preview",
                    onValueChange = {},
                    onRequestPhoto = {},
                )
            }
        }

        validation.forEach {
            Text(
                stringResource(R.string.tree_schema_error, it),
                color = AppTheme.colors.status.error,
                style = AppTheme.typography.bodyStyle,
            )
        }

        Text(
            stringResource(
                R.string.tree_save_summary_hint,
                traitNameHint.ifBlank { "—" },
                schema.name,
                schema.id,
                saveLeafLabel,
            ),
            style = AppTheme.typography.subheadingStyle,
            color = AppTheme.colors.text.secondary,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TreeActionButton(
                text = stringResource(R.string.tree_save_schema),
                onClick = {
                    pendingSaveLeaf = resolveSaveLeaf()
                    showSaveSummary = true
                },
                enabled = validation.isEmpty() && schema.id.isNotBlank(),
                emphasized = true,
            )
            TreeActionButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
            )
        }
            }
        }

        if (showPalette && !expandVertically) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = AppTheme.colors.background,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.tree_attach_trait),
                            style = AppTheme.typography.titleStyle,
                            color = AppTheme.colors.text.primary,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        )
                        IconButton(onClick = { showPalette = false; search = "" }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.tree_close),
                                tint = AppTheme.colors.text.primary,
                            )
                        }
                    }
                    HorizontalDivider()
                    TraitPalettePanel(
                        availableTraits = availableTraits,
                        selected = selected,
                        search = search,
                        onSearchChange = { search = it },
                        onAttach = { trait ->
                            val t = selected!!
                            schema = schema.updateType(t.name) { node ->
                                node.attachTraitByName(trait.name)
                            }
                        },
                        onDone = { showPalette = false; search = "" },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showAddType) {
        AddTypeDialog(
            usedLetters = schema.nodeTypes.map { it.cls }.toSet(),
            existingNames = schema.nodeTypes.map { it.name }.toSet(),
            onDismiss = { showAddType = false },
            onAdd = { name, display, cls ->
                if (schema.nodeTypes.any { it.name.equals(name, ignoreCase = true) }) {
                    Toast.makeText(context, R.string.tree_duplicate_node_type, Toast.LENGTH_SHORT).show()
                    return@AddTypeDialog
                }
                schema = schema.copy(
                    nodeTypes = schema.nodeTypes + NodeTypeDef(
                        name = name,
                        displayName = display,
                        cls = cls,
                    ),
                )
                selectedTypeName = name
                showAddType = false
            },
        )
    }

    if (showSaveSummary) {
        AppAlertDialog(
            title = stringResource(R.string.tree_save_schema),
            content = {
                Text(
                    stringResource(
                        R.string.tree_save_summary_hint,
                        traitNameHint.ifBlank { "—" },
                        schema.name,
                        schema.id,
                        saveLeafLabel,
                    ),
                    style = AppTheme.typography.bodyStyle,
                    color = AppTheme.colors.text.primary,
                )
            },
            positiveButtonText = stringResource(R.string.tree_save_schema),
            onPositive = {
                val leaf = pendingSaveLeaf ?: resolveSaveLeaf()
                showSaveSummary = false
                pendingSaveLeaf = null
                onSave(schema, leaf)
            },
            negativeButtonText = stringResource(R.string.cancel),
            onNegative = {
                showSaveSummary = false
                pendingSaveLeaf = null
            },
        )
    }

    pendingDeleteType?.let { name ->
        AppAlertDialog(
            title = stringResource(R.string.tree_remove_node_type),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.tree_delete_type_confirm),
                        style = AppTheme.typography.bodyStyle,
                        color = AppTheme.colors.text.primary,
                    )
                    TreeActionButton(
                        text = if (bumpVersionOnDelete) {
                            stringResource(R.string.tree_bump_version_on_delete_yes)
                        } else {
                            stringResource(R.string.tree_bump_version_on_delete_no)
                        },
                        onClick = { bumpVersionOnDelete = !bumpVersionOnDelete },
                        selected = bumpVersionOnDelete,
                    )
                }
            },
            positiveButtonText = stringResource(R.string.tree_remove_node_type),
            positiveTextColor = AppTheme.colors.status.error,
            onPositive = {
                if (isSchemaUsedByObservations(schema.id)) {
                    Toast.makeText(
                        context,
                        R.string.tree_delete_type_blocked,
                        Toast.LENGTH_LONG,
                    ).show()
                    pendingDeleteType = null
                } else {
                    var next = schema.copy(
                        nodeTypes = schema.nodeTypes
                            .filterNot { it.name == name }
                            .map { type ->
                                type.copy(
                                    allowedChildren = type.allowedChildren.filterNot { it.nodeType == name },
                                )
                            },
                        summaryPodNodeType = schema.summaryPodNodeType?.takeUnless { it == name },
                    )
                    if (bumpVersionOnDelete) next = next.copy(version = next.version + 1)
                    schema = next
                    if (selectedTypeName == name) selectedTypeName = schema.rootType
                    pendingDeleteType = null
                }
            },
            negativeButtonText = stringResource(R.string.cancel),
            onNegative = { pendingDeleteType = null },
        )
    }
}

@Composable
private fun TraitPalettePanel(
    availableTraits: List<TraitObject>,
    selected: NodeTypeDef?,
    search: String,
    onSearchChange: (String) -> Unit,
    onAttach: (TraitObject) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppTheme.colors.background,
        tonalElevation = 0.dp,
    ) {
        TraitPaletteBody(
            availableTraits = availableTraits,
            selected = selected,
            search = search,
            onSearchChange = onSearchChange,
            onAttach = onAttach,
            onDone = onDone,
        )
    }
}

@Composable
private fun TraitPaletteBody(
    availableTraits: List<TraitObject>,
    selected: NodeTypeDef?,
    search: String,
    onSearchChange: (String) -> Unit,
    onAttach: (TraitObject) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.tree_palette_title),
            style = AppTheme.typography.titleStyle,
            color = AppTheme.colors.text.primary,
        )
        Text(
            stringResource(R.string.tree_palette_keep_open),
            style = AppTheme.typography.subheadingStyle,
            color = AppTheme.colors.text.secondary,
        )
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            label = { Text(stringResource(R.string.tree_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Only real attachable study traits — never topology shells / unhostable cams,
        // and never phantom names invented by a schema template.
        val filtered = filterAttachableStudyTraits(availableTraits).filter {
            search.isBlank() ||
                it.name.contains(search, true) ||
                it.alias.contains(search, true)
        }.groupBy { it.format.orEmpty().ifBlank { "?" } }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.tree_palette_empty),
                    style = AppTheme.typography.bodyStyle,
                    color = AppTheme.colors.text.secondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            filtered.forEach { (format, traits) ->
                Text(
                    text = format,
                    style = AppTheme.typography.subheadingStyle,
                    color = AppTheme.colors.text.secondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                traits.forEach { trait ->
                    val already = selected?.traitRefs?.any { it.traitName == trait.name } == true
                    TraitPaletteRow(
                        trait = trait,
                        alreadyAttached = already,
                        enabled = !already && selected != null,
                        onClick = { onAttach(trait) },
                    )
                }
            }
        }
        TreeActionButton(
            text = stringResource(R.string.tree_palette_done),
            onClick = onDone,
            emphasized = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Attach palette row — matches [com.fieldbook.tracker.ui.screens.traits.listItems.TraitListItem] chrome. */
@Composable
private fun TraitPaletteRow(
    trait: TraitObject,
    alreadyAttached: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val formatKey = trait.format.orEmpty()
    val formatEnum = Formats.entries.find { it.getDatabaseName().equals(formatKey, ignoreCase = true) }
        ?: Formats.entries.find { it.name.equals(formatKey, ignoreCase = true) }
    val label = trait.alias.takeIf { it.isNotBlank() } ?: trait.name.orEmpty()
    val shape = RoundedCornerShape(5.dp)
    val borderColor = if (alreadyAttached) {
        AppTheme.colors.accent.copy(alpha = 0.45f)
    } else {
        AppTheme.colors.surface.border
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.background, shape)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = buildString {
                    append(label)
                    append(", ")
                    append(formatKey)
                    if (alreadyAttached) append(", attached")
                }
                testTagsAsResourceId = true
            }
            .testTag("tree_palette_trait_${trait.name}"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            AppIcon(
                icon = painterResource(
                    formatEnum?.getIcon() ?: R.drawable.ic_trait_categorical,
                ),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = AppTheme.typography.bodyStyle,
                    color = if (enabled || alreadyAttached) {
                        AppTheme.colors.text.primary
                    } else {
                        AppTheme.colors.text.secondary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatKey,
                    style = AppTheme.typography.subheadingStyle,
                    color = AppTheme.colors.text.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (alreadyAttached) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = AppTheme.colors.accent,
                )
            }
        }
    }
}

@Composable
private fun TraitReorderList(
    refs: List<TraitRef>,
    onReorder: (from: Int, to: Int) -> Unit,
    onToggleRequired: (TraitRef) -> Unit,
    onDetach: (TraitRef) -> Unit,
) {
    // ReorderableColumn (not LazyColumn) — lives inside an outer verticalScroll; nested LazyColumn fights gestures.
    ReorderableColumn(
        list = refs,
        onSettle = { from, to -> onReorder(from, to) },
        modifier = Modifier.fillMaxWidth(),
    ) { _, ref, _ ->
        key(ref.traitName) {
            ReorderableItem {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = AppTheme.colors.text.secondary,
                        modifier = Modifier.draggableHandle(),
                    )
                    Text(
                        ref.traitName,
                        style = AppTheme.typography.bodyStyle,
                        color = AppTheme.colors.text.primary,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    IconButton(onClick = { onToggleRequired(ref) }) {
                        Icon(
                            if (ref.requiredOverride == true) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Required: ${ref.traitName}",
                            tint = AppTheme.colors.text.button,
                        )
                    }
                    IconButton(onClick = { onDetach(ref) }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Detach",
                            tint = AppTheme.colors.text.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionTargetPickerDialog(
    title: String,
    allTypes: List<NodeTypeDef>,
    attached: Set<String>,
    onPick: (targetType: String, label: String) -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        title = title,
        content = {
            Column(
                modifier = Modifier.semantics { testTagsAsResourceId = true },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                allTypes.forEach { type ->
                    val already = type.name in attached
                    Text(
                        text = if (already) {
                            "${type.displayName} ✓"
                        } else {
                            type.displayName
                        },
                        style = AppTheme.typography.bodyStyle,
                        color = if (already) {
                            AppTheme.colors.text.secondary
                        } else {
                            AppTheme.colors.text.primary
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tree_connect_target_${type.name}")
                            .semantics {
                                contentDescription = "Connect to ${type.displayName}"
                            }
                            .clickable(enabled = !already) {
                                onPick(type.name, "Add ${type.displayName}")
                            }
                            .padding(8.dp),
                    )
                }
            }
        },
        negativeButtonText = stringResource(R.string.cancel),
        onNegative = onDismiss,
    )
}

@Composable
private fun ConnectionZone(
    title: String,
    color: Color,
    edge: EdgeType,
    rules: List<ChildRule>,
    onUpdateLabel: (ChildRule, String) -> Unit,
    onRemove: (ChildRule) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, color = color, style = AppTheme.typography.subheadingStyle)
        ReorderableColumn(
            list = rules,
            onSettle = { from, to -> onReorder(from, to) },
            modifier = Modifier.fillMaxWidth(),
        ) { _, rule, _ ->
            key("${rule.nodeType}:${rule.edge}:${rule.label}") {
                ReorderableItem {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = AppTheme.colors.text.secondary,
                            modifier = Modifier.draggableHandle(),
                        )
                        Text(
                            edge.symbol,
                            style = AppTheme.typography.bodyStyle,
                            color = color,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        OutlinedTextField(
                            value = rule.label,
                            onValueChange = { onUpdateLabel(rule, it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("→ ${rule.nodeType}") },
                        )
                        IconButton(onClick = { onRemove(rule) }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Remove rule",
                                tint = AppTheme.colors.text.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTypeDialog(
    usedLetters: Set<String>,
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (name: String, display: String, cls: String) -> Unit,
) {
    var display by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val suggested = (('A'..'Z').map { it.toString() } - usedLetters).firstOrNull() ?: "X"
    var cls by remember { mutableStateOf(suggested) }
    val nameTaken = name.isNotBlank() && existingNames.any { it.equals(name, ignoreCase = true) }
    AppAlertDialog(
        title = stringResource(R.string.tree_add_node_type),
        content = {
            Column(
                modifier = Modifier.semantics { testTagsAsResourceId = true },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AddTypeDialogFields(
                    display = display,
                    onDisplayChange = {
                        display = it
                        if (name.isBlank()) name = it.lowercase().replace(' ', '_')
                    },
                    name = name,
                    onNameChange = { name = it.lowercase().replace(' ', '_') },
                    cls = cls,
                    onClsChange = { cls = it.take(1).uppercase() },
                )
                if (nameTaken) {
                    Text(
                        stringResource(R.string.tree_duplicate_node_type),
                        color = AppTheme.colors.status.error,
                        style = AppTheme.typography.bodyStyle,
                    )
                }
            }
        },
        positiveButtonText = stringResource(R.string.tree_add),
        onPositive = {
            val taken = name.isNotBlank() && existingNames.any { it.equals(name, ignoreCase = true) }
            if (display.isNotBlank() && name.isNotBlank() && cls.isNotBlank() && !taken) {
                onAdd(name, display, cls)
            }
        },
        negativeButtonText = stringResource(R.string.cancel),
        onNegative = onDismiss,
    )
}

/** Dialog body only, extracted for isolated screenshot tests; not full dialog chrome. */
@Composable
fun AddTypeDialogFields(
    display: String,
    onDisplayChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    cls: String,
    onClsChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = display,
            onValueChange = onDisplayChange,
            label = { Text(stringResource(R.string.tree_display_name)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tree_add_type_display"),
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.tree_internal_name)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tree_add_type_name"),
        )
        OutlinedTextField(
            value = cls,
            onValueChange = onClsChange,
            label = { Text(stringResource(R.string.tree_class_letter)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tree_add_type_class"),
        )
    }
}

internal fun TreeSchema.updateType(name: String, transform: (NodeTypeDef) -> NodeTypeDef): TreeSchema =
    copy(nodeTypes = nodeTypes.map { if (it.name == name) transform(it) else it })

internal fun TreeSchema.renameType(oldName: String, newName: String): TreeSchema {
    if (oldName == newName) return this
    val types = nodeTypes.map { type ->
        val renamed = if (type.name == oldName) type.copy(name = newName) else type
        renamed.copy(
            allowedChildren = renamed.allowedChildren.map { rule ->
                if (rule.nodeType == oldName) rule.copy(nodeType = newName) else rule
            },
        )
    }
    return copy(
        rootType = if (rootType == oldName) newName else rootType,
        nodeTypes = types,
        summaryPodNodeType = if (summaryPodNodeType == oldName) newName else summaryPodNodeType,
    )
}

private fun TreeSchema.swapTraitOrder(typeName: String, from: Int, to: Int): TreeSchema =
    updateType(typeName) { type ->
        val refs = type.traitRefs.sortedBy { it.order }.toMutableList()
        if (from !in refs.indices || to !in refs.indices) return@updateType type
        val item = refs.removeAt(from)
        refs.add(to, item)
        type.copy(traitRefs = refs.mapIndexed { i, r -> r.copy(order = i) })
    }

private fun TreeSchema.reorderChildRules(
    typeName: String,
    edge: EdgeType,
    from: Int,
    to: Int,
): TreeSchema = updateType(typeName) { type ->
    val edgeRules = type.allowedChildren.filter { it.edge == edge }.toMutableList()
    val other = type.allowedChildren.filterNot { it.edge == edge }
    if (from !in edgeRules.indices || to !in edgeRules.indices) return@updateType type
    val item = edgeRules.removeAt(from)
    edgeRules.add(to, item)
    // Keep PRECEDES before BEARS (or preserve original non-edge grouping).
    type.copy(
        allowedChildren = when (edge) {
            EdgeType.PRECEDES -> edgeRules + other
            EdgeType.BEARS -> other + edgeRules
        },
    )
}

fun blankSchema() = TreeSchema(
    id = "tree_blank_v1",
    name = "Blank tree",
    version = 1,
    rootType = "root",
    nodeTypes = listOf(
        NodeTypeDef(
            name = "root",
            displayName = "Root",
            cls = "R",
        ),
    ),
    summaryPodTraitName = null,
    summaryPodNodeType = null,
)

/**
 * Topology-only soybean starter. Does **not** pre-attach TraitRefs — those must
 * come from real study traits via Attach (same policy as Blank). Phantom names
 * like "Node position" / "Height" / "Color" previously appeared as attached without
 * existing in the study — [traitRefs] is explicitly empty on every node type.
 */
fun defaultSoybeanSchema() = TreeSchema(
    id = "soy_arch_v1",
    name = "Soybean architecture",
    version = 1,
    rootType = "plant",
    summaryPodTraitName = null,
    summaryPodNodeType = null,
    nodeTypes = listOf(
        NodeTypeDef(
            name = "plant",
            displayName = "Plant",
            cls = "P",
            allowedChildren = listOf(ChildRule("internode", EdgeType.PRECEDES, "Main stem")),
            maxChildren = 1,
            traitRefs = emptyList(),
        ),
        NodeTypeDef(
            name = "internode",
            displayName = "Internode",
            cls = "N",
            allowedChildren = listOf(
                ChildRule("internode", EdgeType.PRECEDES, "Next internode"),
                ChildRule("internode", EdgeType.BEARS, "Add offshoot"),
                ChildRule("pod", EdgeType.BEARS, "Add pod"),
            ),
            traitRefs = emptyList(),
        ),
        NodeTypeDef(
            name = "pod",
            displayName = "Pod",
            cls = "C",
            traitRefs = emptyList(),
        ),
    ),
)
