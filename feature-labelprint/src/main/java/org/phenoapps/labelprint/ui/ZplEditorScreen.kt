package org.phenoapps.labelprint.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.phenoapps.labelprint.R
import org.phenoapps.labelprint.model.LabelDesignElement
import org.phenoapps.labelprint.model.LabelDesignElementType
import org.phenoapps.labelprint.zpl.ParseResult
import org.phenoapps.labelprint.zpl.TokenizeResult
import org.phenoapps.labelprint.zpl.ZplGenerator
import org.phenoapps.labelprint.zpl.ZplParserImpl
import org.phenoapps.labelprint.zpl.ZplTokenizerImpl

/**
 * Data class holding label settings.
 */
data class LabelSettings(
    val labelWidth: Int,
    val labelLength: Int,
    val mediaType: String,
    val mediaGap: String
)

/**
 * Visual-first ZPL label editor with drag-and-drop element positioning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZplEditorScreen(
    initialZpl: String = "",
    initialTemplateName: String? = null,
    templates: Map<String, String>, // name -> zpl
    onDismiss: () -> Unit,
    onSave: (name: String, zpl: String, defaultValues: Map<String, String>) -> Unit,
    onDelete: (name: String) -> Unit,
    onExport: (suggestedName: String, zpl: String) -> Unit,
    onReadFromDevice: (suspend () -> LabelSettings?)? = null
) {
    val initialParsed = remember(initialZpl) {
        if (initialZpl.isBlank()) {
            ZplGenerator.ParsedTemplate(
                elements = emptyList(),
                labelWidth = ZplGenerator.WIDTH_3X2,
                labelHeight = ZplGenerator.HEIGHT_3X2,
                mediaType = ZplGenerator.MEDIA_THERMAL_DIRECT,
                mediaGap = ZplGenerator.TRACKING_GAP
            )
        } else {
            ZplGenerator.parseTemplate(initialZpl)
        }
    }

    val elements =
        remember { mutableStateListOf<LabelDesignElement>().apply { addAll(initialParsed.elements) } }
    var labelWidth by remember { mutableIntStateOf(initialParsed.labelWidth) }
    var labelHeight by remember { mutableIntStateOf(initialParsed.labelHeight) }

    var zplText by remember { mutableStateOf(initialZpl) }
    var templateName by remember { mutableStateOf(initialTemplateName) }
    var mediaType by remember { mutableStateOf(initialParsed.mediaType) }
    var mediaGap by remember { mutableStateOf(initialParsed.mediaGap) }

    val defaultDpi = remember(labelWidth, labelHeight) {
        if (labelWidth % 300 == 0 || labelHeight % 300 == 0) 300 else ZplGenerator.NORMALIZED_DPI
    }
    var localDpi by remember { mutableStateOf(defaultDpi.toString()) }

    var showZplCode by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddElementDialog by remember { mutableStateOf(false) }
    var showEditElementDialog by remember { mutableStateOf(false) }
    var selectedElementId by remember { mutableStateOf<String?>(null) }

    fun nextElementId(): String {
        val existingIds = elements.map { it.id }.toSet()
        var counter = 0
        while ("elem_$counter" in existingIds) {
            counter++
        }
        return "elem_$counter"
    }

    var syncingFromCode by remember { mutableStateOf(false) }

    fun regenerateZpl() {
        if (!syncingFromCode) {
            zplText = ZplGenerator.generate(
                elements.toList(),
                labelWidth,
                labelHeight,
                mediaType,
                mediaGap
            )
        }
    }

    fun syncFromCode(newZpl: String) {
        syncingFromCode = true
        zplText = newZpl
        val parsed = ZplGenerator.parseTemplate(newZpl)
        elements.clear()
        elements.addAll(parsed.elements)
        labelWidth = parsed.labelWidth
        labelHeight = parsed.labelHeight
        mediaType = parsed.mediaType
        mediaGap = parsed.mediaGap
        syncingFromCode = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = templateName ?: stringResource(R.string.zpl_editor_untitled),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val saved = templates[templateName]
                            if (saved != null) syncFromCode(saved)
                        },
                        enabled = templateName != null && templates.containsKey(templateName)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }
                    IconButton(onClick = { onExport(templateName ?: "template", zplText) }) {
                        Icon(Icons.Default.Save, contentDescription = "Export")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = templateName != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.zpl_editor_delete))
                }
                Button(
                    onClick = {
                        if (templateName != null) {
                            onSave(
                                templateName!!,
                                zplText,
                                elements.associate { it.placeholder to it.defaultValue })
                            onDismiss()
                        } else {
                            showSaveDialog = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Save, null, Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.zpl_editor_save))
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
            var dropdownExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = templateName ?: stringResource(R.string.zpl_editor_untitled),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.zpl_editor_template)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.zpl_editor_new_template)) },
                        onClick = {
                            templateName = null
                            elements.clear()
                            zplText = ""
                            dropdownExpanded = false
                        }
                    )
                    HorizontalDivider()
                    templates.keys.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                templateName = name
                                syncFromCode(templates[name] ?: "")
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = templateName ?: "",
                onValueChange = { templateName = it.ifBlank { null } },
                label = { Text(stringResource(R.string.zpl_editor_name)) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.zpl_editor_name_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(onClick = { showSettingsDialog = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Label Settings")
                }
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(onClick = { showZplCode = !showZplCode }) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = if (showZplCode) "Hide ZPL" else "Show ZPL"
                    )
                }
                if (selectedElementId != null) {
                    FilledTonalIconButton(onClick = { showEditElementDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit selected")
                    }
                    FilledTonalIconButton(
                        onClick = {
                            elements.removeAll { it.id == selectedElementId }
                            selectedElementId = null
                            regenerateZpl()
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete selected",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            val parsedLabel = remember(zplText) {
                if (zplText.isBlank()) null
                else {
                    val tokenizer = ZplTokenizerImpl()
                    val parser = ZplParserImpl()
                    val tokenResult = tokenizer.tokenize(zplText)
                    if (tokenResult is TokenizeResult.Success) {
                        val parseResult = parser.parse(tokenResult.tokens)
                        if (parseResult is ParseResult.Success) {
                            parseResult.document.labels.firstOrNull()
                        } else null
                    } else null
                }
            }

            Box(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)) {
                LabelDesignCanvas(
                    elements = elements.toList(),
                    labelWidthDots = labelWidth,
                    labelHeightDots = labelHeight,
                    selectedElementId = selectedElementId,
                    onElementSelected = { id -> selectedElementId = id },
                    onElementMoved = { id, newX, newY ->
                        val index = elements.indexOfFirst { it.id == id }
                        if (index >= 0) {
                            elements[index] = elements[index].copy(x = newX, y = newY)
                            regenerateZpl()
                        }
                    },
                    parsedLabel = parsedLabel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                IconButton(
                    onClick = { showAddElementDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add element",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            AnimatedVisibility(
                visible = showZplCode,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)) {
                    Text(
                        text = stringResource(R.string.zpl_editor_code_label),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    TextField(
                        value = zplText,
                        onValueChange = { syncFromCode(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.zpl_editor_code_placeholder),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        LabelSettingsDialog(
            labelWidth = labelWidth,
            labelLength = labelHeight,
            mediaType = mediaType,
            mediaGap = mediaGap,
            defaultDpi = localDpi.toIntOrNull() ?: 203,
            onConfirm = { newWidth, newHeight, newMedia, newGap, newDpi ->
                localDpi = newDpi.toString()
                labelWidth = newWidth
                labelHeight = newHeight
                mediaType = newMedia
                mediaGap = newGap
                regenerateZpl()
                showSettingsDialog = false
            },
            onDismiss = { showSettingsDialog = false },
            onReadFromDevice = onReadFromDevice
        )
    }

    if (showAddElementDialog) {
        val existingPlaceholders = elements.map { it.placeholder }.toSet()
        var nextFieldIdx = 1
        while ("{text$nextFieldIdx}" in existingPlaceholders) nextFieldIdx++
        var nextBarcodeIdx = 1
        while ("{barcode$nextBarcodeIdx}" in existingPlaceholders) nextBarcodeIdx++
        var nextQrIdx = 1
        while ("{qrcode$nextQrIdx}" in existingPlaceholders) nextQrIdx++
        AddElementDialog(
            nextTextIndex = nextFieldIdx,
            nextBarcodeIndex = nextBarcodeIdx,
            nextQrCodeIndex = nextQrIdx,
            onConfirm = { newElement ->
                val newId = nextElementId()
                val margin = 20
                val spawnX = margin.coerceIn(0, (labelWidth - margin).coerceAtLeast(0))
                val spawnY =
                    (margin + (elements.size * 40)) % (labelHeight - margin).coerceAtLeast(margin + 1)
                elements.add(newElement.copy(id = newId, x = spawnX, y = spawnY))
                selectedElementId = newId
                regenerateZpl()
                showAddElementDialog = false
            },
            onDismiss = { showAddElementDialog = false }
        )
    }

    if (showEditElementDialog && selectedElementId != null) {
        val selectedElement = elements.find { it.id == selectedElementId }
        if (selectedElement != null) {
            EditElementDialog(
                element = selectedElement,
                onConfirm = { updated ->
                    val index = elements.indexOfFirst { it.id == updated.id }
                    if (index >= 0) {
                        elements[index] = updated
                        regenerateZpl()
                    }
                    showEditElementDialog = false
                },
                onDismiss = { showEditElementDialog = false }
            )
        } else {
            showEditElementDialog = false
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.zpl_editor_delete_template)) },
            text = { Text(stringResource(R.string.zpl_editor_delete_confirm, templateName ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    if (templateName != null) {
                        onDelete(templateName!!)
                        templateName = null
                        elements.clear()
                        zplText = ""
                    }
                    showDeleteConfirm = false
                }) {
                    Text(
                        stringResource(R.string.zpl_editor_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(
                        stringResource(R.string.dialog_cancel)
                    )
                }
            }
        )
    }

    if (showSaveDialog) {
        var saveName by remember { mutableStateOf(templateName ?: "") }
        var showError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.zpl_editor_save_template)) },
            text = {
                Column {
                    TextField(
                        value = saveName,
                        onValueChange = { saveName = it; if (it.isNotBlank()) showError = false },
                        label = { Text(stringResource(R.string.zpl_editor_template_name)) },
                        singleLine = true,
                        isError = showError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showError) {
                        Text(
                            text = stringResource(R.string.zpl_editor_template_name_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (saveName.isBlank()) showError = true
                    else {
                        onSave(
                            saveName.trim(),
                            zplText,
                            elements.associate { it.placeholder to it.defaultValue })
                        templateName = saveName.trim()
                        showSaveDialog = false
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.zpl_editor_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabelSettingsDialog(
    labelWidth: Int,
    labelLength: Int,
    mediaType: String,
    mediaGap: String,
    defaultDpi: Int,
    onConfirm: (labelWidth: Int, labelLength: Int, mediaType: String, mediaGap: String, dpi: Int) -> Unit,
    onDismiss: () -> Unit,
    onReadFromDevice: (suspend () -> LabelSettings?)?
) {
    var localDpi by remember { mutableStateOf(defaultDpi.toString()) }
    var isManualDpi by remember { mutableStateOf(defaultDpi != 203 && defaultDpi != 300) }
    var localWidthInches by remember {
        mutableStateOf(
            String.format(
                "%.2f",
                labelWidth.toFloat() / defaultDpi
            )
        )
    }
    var localHeightInches by remember {
        mutableStateOf(
            String.format(
                "%.2f",
                labelLength.toFloat() / defaultDpi
            )
        )
    }
    var localMediaType by remember { mutableStateOf(mediaType) }
    var localMediaGap by remember { mutableStateOf(mediaGap) }
    var readStatus by remember { mutableStateOf<String?>(null) }
    var isReading by remember { mutableStateOf(false) }

    if (onReadFromDevice != null) {
        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(Unit) {
            isReading = true
            val settings = withContext(Dispatchers.IO) { onReadFromDevice() }
            isReading = false
            if (settings != null) {
                val dpi = defaultDpi
                readStatus = context.getString(
                    R.string.zpl_editor_dots_display_format,
                    settings.labelWidth,
                    settings.labelLength,
                    dpi
                )
            } else {
                readStatus = "Could not read from printer"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.zpl_editor_label_settings)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = localWidthInches,
                    onValueChange = { text ->
                        localWidthInches = text.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text(stringResource(R.string.zpl_editor_label_width)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = localHeightInches,
                    onValueChange = { text ->
                        localHeightInches = text.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text(stringResource(R.string.zpl_editor_label_length)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                var dpiExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = dpiExpanded,
                    onExpandedChange = { dpiExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (isManualDpi) stringResource(R.string.zpl_editor_dpi_manual) else localDpi,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.zpl_editor_printer_dpi)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dpiExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = dpiExpanded,
                        onDismissRequest = { dpiExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        val manualLabel = stringResource(R.string.zpl_editor_dpi_manual)
                        listOf("203", "300", manualLabel).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    if (option == manualLabel) isManualDpi = true else {
                                        isManualDpi = false; localDpi = option
                                    }; dpiExpanded = false
                                })
                        }
                    }
                }
                if (isManualDpi) {
                    OutlinedTextField(
                        value = localDpi,
                        onValueChange = { text -> localDpi = text.filter { it.isDigit() } },
                        label = { Text(stringResource(R.string.zpl_editor_manual_dpi)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val dpiValue = localDpi.toIntOrNull() ?: defaultDpi
                val widthDots = ((localWidthInches.toFloatOrNull() ?: 0f) * dpiValue).toInt()
                val heightDots = ((localHeightInches.toFloatOrNull() ?: 0f) * dpiValue).toInt()
                Text(
                    text = stringResource(
                        R.string.zpl_editor_dots_display_format,
                        widthDots,
                        heightDots,
                        dpiValue
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                var mediaTypeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = mediaTypeExpanded,
                    onExpandedChange = { mediaTypeExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = when (localMediaType) {
                            ZplGenerator.MEDIA_THERMAL_DIRECT -> stringResource(R.string.zpl_editor_media_thermal_direct)
                            ZplGenerator.MEDIA_THERMAL_TRANSFER -> stringResource(R.string.zpl_editor_media_thermal_transfer)
                            else -> localMediaType
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.zpl_editor_media_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mediaTypeExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = mediaTypeExpanded,
                        onDismissRequest = { mediaTypeExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        listOf(
                            ZplGenerator.MEDIA_THERMAL_DIRECT,
                            ZplGenerator.MEDIA_THERMAL_TRANSFER
                        ).forEach { value ->
                            DropdownMenuItem(
                                text = { Text(value) },
                                onClick = { localMediaType = value; mediaTypeExpanded = false })
                        }
                    }
                }
                var mediaGapExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = mediaGapExpanded,
                    onExpandedChange = { mediaGapExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = when (localMediaGap) {
                            ZplGenerator.TRACKING_GAP -> stringResource(R.string.zpl_editor_media_gap)
                            ZplGenerator.TRACKING_CONTINUOUS -> stringResource(R.string.zpl_editor_media_continuous)
                            ZplGenerator.TRACKING_MARK -> stringResource(R.string.zpl_editor_media_mark)
                            else -> localMediaGap
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.zpl_editor_media_tracking)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mediaGapExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = mediaGapExpanded,
                        onDismissRequest = { mediaGapExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        listOf(
                            ZplGenerator.TRACKING_GAP,
                            ZplGenerator.TRACKING_CONTINUOUS,
                            ZplGenerator.TRACKING_MARK
                        ).forEach { value ->
                            DropdownMenuItem(
                                text = { Text(value) },
                                onClick = { localMediaGap = value; mediaGapExpanded = false })
                        }
                    }
                }
                if (onReadFromDevice != null) {
                    if (isReading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.zpl_editor_reading_printer),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (readStatus != null) {
                        Text(
                            text = readStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (readStatus!!.contains("Could not")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val dpi = localDpi.toIntOrNull() ?: defaultDpi
                val w = ((localWidthInches.toFloatOrNull() ?: 0f) * dpi).toInt().coerceAtLeast(1)
                val h = ((localHeightInches.toFloatOrNull() ?: 0f) * dpi).toInt().coerceAtLeast(1)
                onConfirm(w, h, localMediaType, localMediaGap, dpi)
            }) { Text(stringResource(R.string.dialog_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddElementDialog(
    nextTextIndex: Int,
    nextBarcodeIndex: Int,
    nextQrCodeIndex: Int,
    onConfirm: (LabelDesignElement) -> Unit,
    onDismiss: () -> Unit
) {
    var elementType by remember { mutableStateOf(LabelDesignElementType.TEXT) }
    var defaultValue by remember { mutableStateOf("") }
    var fontSize by remember { mutableStateOf("28") }
    var blockWidth by remember { mutableStateOf("0") }
    var magnification by remember { mutableStateOf("5") }
    var barcodeHeight by remember { mutableStateOf("80") }
    var moduleWidth by remember { mutableStateOf("2") }
    var typeExpanded by remember { mutableStateOf(false) }

    val placeholderId = when (elementType) {
        LabelDesignElementType.TEXT -> "{text$nextTextIndex}"
        LabelDesignElementType.QR_CODE -> "{qrcode$nextQrCodeIndex}"
        LabelDesignElementType.BARCODE_128 -> "{barcode$nextBarcodeIndex}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.zpl_editor_add_element)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = when (elementType) {
                            LabelDesignElementType.TEXT -> stringResource(R.string.zpl_editor_element_text)
                            LabelDesignElementType.QR_CODE -> stringResource(R.string.zpl_editor_element_qr)
                            LabelDesignElementType.BARCODE_128 -> stringResource(R.string.zpl_editor_element_barcode)
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.zpl_editor_element_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.zpl_editor_element_text)) },
                            onClick = {
                                elementType = LabelDesignElementType.TEXT; typeExpanded = false
                            })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.zpl_editor_element_qr)) },
                            onClick = {
                                elementType = LabelDesignElementType.QR_CODE; typeExpanded = false
                            })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.zpl_editor_element_barcode)) },
                            onClick = {
                                elementType = LabelDesignElementType.BARCODE_128; typeExpanded =
                                false
                            })
                    }
                }
                OutlinedTextField(
                    value = defaultValue,
                    onValueChange = { defaultValue = it },
                    label = { Text(stringResource(R.string.zpl_editor_default_value)) },
                    placeholder = { Text(stringResource(R.string.zpl_editor_default_value_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                when (elementType) {
                    LabelDesignElementType.TEXT -> {
                        OutlinedTextField(
                            value = fontSize,
                            onValueChange = { fontSize = it.filter { it.isDigit() } },
                            label = { Text(stringResource(R.string.zpl_editor_font_size)) },
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.zpl_editor_font_size_helper)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = blockWidth,
                            onValueChange = { blockWidth = it.filter { it.isDigit() } },
                            label = { Text(stringResource(R.string.zpl_editor_block_width)) },
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.zpl_editor_block_width_helper)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    LabelDesignElementType.QR_CODE -> {
                        OutlinedTextField(
                            value = magnification,
                            onValueChange = { magnification = it.filter { it.isDigit() } },
                            label = { Text(stringResource(R.string.zpl_editor_magnification)) },
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.zpl_editor_magnification_helper)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    LabelDesignElementType.BARCODE_128 -> {
                        OutlinedTextField(
                            value = barcodeHeight,
                            onValueChange = { barcodeHeight = it.filter { it.isDigit() } },
                            label = { Text(stringResource(R.string.zpl_editor_barcode_height)) },
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.zpl_editor_barcode_height_helper)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = moduleWidth,
                            onValueChange = { moduleWidth = it.filter { it.isDigit() } },
                            label = { Text(stringResource(R.string.zpl_editor_module_width)) },
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.zpl_editor_module_width_helper)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val element = when (elementType) {
                    LabelDesignElementType.TEXT -> LabelDesignElement(
                        id = "",
                        type = LabelDesignElementType.TEXT,
                        x = 0,
                        y = 0,
                        placeholder = placeholderId,
                        defaultValue = defaultValue,
                        fontSize = fontSize.toIntOrNull()?.coerceIn(10, 300) ?: 28,
                        blockWidth = blockWidth.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    )

                    LabelDesignElementType.QR_CODE -> LabelDesignElement(
                        id = "",
                        type = LabelDesignElementType.QR_CODE,
                        x = 0,
                        y = 0,
                        placeholder = placeholderId,
                        defaultValue = defaultValue,
                        magnification = magnification.toIntOrNull()?.coerceIn(1, 10) ?: 5
                    )

                    LabelDesignElementType.BARCODE_128 -> LabelDesignElement(
                        id = "",
                        type = LabelDesignElementType.BARCODE_128,
                        x = 0,
                        y = 0,
                        placeholder = placeholderId,
                        defaultValue = defaultValue,
                        barcodeHeight = barcodeHeight.toIntOrNull()?.coerceIn(10, 300) ?: 80,
                        moduleWidth = moduleWidth.toIntOrNull()?.coerceIn(1, 4) ?: 2
                    )
                }
                onConfirm(element)
            }) { Text(stringResource(R.string.dialog_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditElementDialog(
    element: LabelDesignElement,
    onConfirm: (LabelDesignElement) -> Unit,
    onDismiss: () -> Unit
) {
    var defaultValue by remember { mutableStateOf(element.defaultValue) }
    var fontSize by remember { mutableStateOf(element.fontSize.toString()) }
    var blockWidth by remember { mutableStateOf(element.blockWidth.toString()) }
    var magnification by remember { mutableStateOf(element.magnification.toString()) }
    var barcodeHeight by remember { mutableStateOf(element.barcodeHeight.toString()) }
    var moduleWidth by remember { mutableStateOf(element.moduleWidth.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                when (element.type) {
                    LabelDesignElementType.TEXT -> stringResource(R.string.zpl_editor_edit_text); LabelDesignElementType.QR_CODE -> stringResource(
                    R.string.zpl_editor_edit_qr
                ); LabelDesignElementType.BARCODE_128 -> stringResource(R.string.zpl_editor_edit_barcode)
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = defaultValue,
                    onValueChange = { defaultValue = it },
                    label = { Text(stringResource(R.string.zpl_editor_default_value)) },
                    placeholder = { Text(stringResource(R.string.zpl_editor_default_value_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                when (element.type) {
                    LabelDesignElementType.TEXT -> {
                        OutlinedTextField(
                            value = fontSize,
                            onValueChange = { fontSize = it.filter { it.isDigit() } },
                            label = { Text(stringResource(R.string.zpl_editor_font_size)) },
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.zpl_editor_font_size_helper)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = blockWidth,
                            onValueChange = { blockWidth = it.filter { it.isDigit() } },
                            label = { Text(stringResource(R.string.zpl_editor_block_width)) },
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.zpl_editor_block_width_helper)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    LabelDesignElementType.QR_CODE -> {
                        OutlinedTextField(
                            value = magnification,
                            onValueChange = { magnification = it.filter { it.isDigit() } },
                            label = { Text(stringResource(R.string.zpl_editor_magnification)) },
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.zpl_editor_magnification_helper)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    LabelDesignElementType.BARCODE_128 -> {
                        OutlinedTextField(
                            value = barcodeHeight,
                            onValueChange = { barcodeHeight = it.filter { it.isDigit() } },
                            label = { Text(stringResource(R.string.zpl_editor_barcode_height)) },
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.zpl_editor_barcode_height_helper)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = moduleWidth,
                            onValueChange = { moduleWidth = it.filter { it.isDigit() } },
                            label = { Text(stringResource(R.string.zpl_editor_module_width)) },
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.zpl_editor_module_width_helper)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val updated = when (element.type) {
                    LabelDesignElementType.TEXT -> element.copy(
                        defaultValue = defaultValue,
                        fontSize = fontSize.toIntOrNull()?.coerceIn(10, 300) ?: element.fontSize,
                        blockWidth = blockWidth.toIntOrNull()?.coerceAtLeast(0)
                            ?: element.blockWidth
                    )

                    LabelDesignElementType.QR_CODE -> element.copy(
                        defaultValue = defaultValue,
                        magnification = magnification.toIntOrNull()?.coerceIn(1, 10)
                            ?: element.magnification
                    )

                    LabelDesignElementType.BARCODE_128 -> element.copy(
                        defaultValue = defaultValue,
                        barcodeHeight = barcodeHeight.toIntOrNull()?.coerceIn(10, 300)
                            ?: element.barcodeHeight,
                        moduleWidth = moduleWidth.toIntOrNull()?.coerceIn(1, 4)
                            ?: element.moduleWidth
                    )
                }
                onConfirm(updated)
            }) { Text(stringResource(R.string.dialog_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } }
    )
}
