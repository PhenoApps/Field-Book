package com.fieldbook.tracker.activities.brapi.io.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.dialogs.builder.AppAlertDialog

// File-level enum for global toggle state used in PendingConflictsList
private enum class GlobalChoice { NONE, SERVER, LOCAL }

/**
 * A Composable screen for managing BrAPI (Breeding API) data synchronization.
 * It provides UI for downloading data from a BrAPI server, uploading local changes,
 * and viewing synchronization statistics.
 *
 * The screen is organized into three main sections (cards):
 * 1.  Download from BrAPI: Initiates a download, shows progress, displays results
 *     (inserts, updates, errors), and allows selection of a merge conflict strategy.
 * 2.  Upload to BrAPI: Initiates an upload of local data (observations and images),
 *     shows progress, and displays the results of the upload.
 * 3.  Sync Statistics: Shows counts of synced data
 *
 * @param uiState The state object containing all data to be displayed on the screen,
 *   such as progress, counts, error messages, and current view mode.
 * @param onDownloadClick Callback invoked when the user clicks the "Download" button.
 * @param onCancelDownloadClick Callback invoked to cancel an in-progress download.
 * @param onExportClick Callback invoked when the user clicks the "Upload" button.
 * @param onCancelExportClick Callback invoked to cancel an in-progress upload.
 * @param onImageUploadToggle Callback invoked when the user toggles the "Include Images" switch.
 * @param onNavigateUp Callback for navigating back from this screen.
 * @param onAuthenticate Callback to re-authenticate with the BrAPI server.
 * @param onMergeStrategyChange Callback invoked when the user selects a different
 *   merge conflict resolution strategy for downloads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrapiSyncScreen(
    uiState: BrapiExportUiState,
    onDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onExportClick: () -> Unit,
    onCancelExportClick: () -> Unit,
    onImageUploadToggle: (Boolean) -> Unit,
    onNavigateUp: () -> Unit,
    onAuthenticate: () -> Unit,
    onMergeStrategyChange: (MergeStrategy) -> Unit,
    onPersistLastCheckedDownload: (String) -> Unit = {},
    onApplyManualChoices: (Map<String, Boolean>) -> Unit = {},
) {
    // Local UI state to allow the user to temporarily dismiss the conflict-resolution prompt
    var suppressConflictDialog by remember { mutableStateOf(false) }
    // Prompt shown when user attempts to download but there are unuploaded items
    var showUploadPrompt by remember { mutableStateOf(false) }

    // When the UI state reports a new last-checked text, persist it via the provided callbacks
    LaunchedEffect(uiState.lastCheckedDownloadText) {
        uiState.lastCheckedDownloadText?.let { onPersistLastCheckedDownload(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.brapi_sync)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.Black
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_left),
                            contentDescription = stringResource(R.string.dialog_back),
                            tint = Color.Black
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onAuthenticate) {
                        Icon(
                            painter = painterResource(R.drawable.lock_reset),
                            contentDescription = stringResource(R.string.authenticate),
                            tint = Color.Black
                        )
                    }
                }
            )
        },
        bottomBar = {}
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // top card: study name + statistics
            InfoCard(
                title = uiState.study?.name ?: stringResource(R.string.brapi_sync),
                icon = painterResource(R.drawable.ic_field_sync)
            ) {
                // show synced counts in the top card
                CountRow(
                    stringResource(R.string.synced_observations),
                    uiState.syncedObservationCount
                )
                CountRow(stringResource(R.string.synced_images), uiState.syncedImageCount)
            }

            //the download card
            InfoCard(
                title = stringResource(R.string.download_from, uiState.brapiServerDisplayName),
                icon = painterResource(R.drawable.download)
            ) {
                when (uiState.viewMode) {
                    ViewMode.DOWNLOADING -> {
                        ExportProgressIndicator(uiState.progress, Modifier.fillMaxWidth())
                        Button(
                            onClick = onCancelDownloadClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }

                    ViewMode.SAVING -> {
                        ExportProgressIndicator(uiState.progress, Modifier.fillMaxWidth())
                    }

                    else -> {
                        if (uiState.downloadedInserts > 0 || uiState.downloadedUpdates > 0) {
                            ResultsCard(
                                inserts = uiState.downloadedInserts,
                                updates = uiState.downloadedUpdates,
                                label = stringResource(R.string.downloaded)
                            )
                        }
                        if (uiState.downloadSuccessMessage != null) {
                            ResultRow(
                                uiState.downloadSuccessMessage,
                                painterResource(R.drawable.ic_check_bold),
                                Color.Black
                            )
                        }
                        if (uiState.downloadError != null && uiState.downloadError.isNotEmpty()) {
                            ResultRow(
                                uiState.downloadError,
                                painterResource(R.drawable.ic_transfer_error),
                                MaterialTheme.colorScheme.error
                            )
                        }
                        // If the ViewModel reported pending conflicts, prompt the user to choose a merge strategy.
                        if (uiState.pendingConflictsCount > 0 && !suppressConflictDialog) {
                            var selectedStrategy by remember { mutableStateOf(uiState.downloadMergeStrategy) }
                            // initialize selection map for manual mode
                            val selectionMap = remember { mutableStateMapOf<String, Boolean?>() }
                            uiState.pendingConflicts.forEach { c ->
                                if (!selectionMap.containsKey(c.brapiId)) selectionMap[c.brapiId] = null
                            }

                            AppAlertDialog(
                                positiveButtonText = stringResource(R.string.dialog_ok),
                                negativeButtonText = stringResource(R.string.dialog_cancel),
                                onPositive = {
                                    if (selectedStrategy is MergeStrategy.Manual) {
                                        // convert nullable map to non-nullable by defaulting nulls to 'true' to preserve previous behavior
                                        onApplyManualChoices(selectionMap.mapValues { it.value ?: true })
                                    } else {
                                        onMergeStrategyChange(selectedStrategy)
                                    }
                                    suppressConflictDialog = true
                                },
                                onNegative = {
                                    suppressConflictDialog = true
                                },
                                title = stringResource(R.string.conflict_resolution_strategy),
                                content = {
                                    Column {
                                        Text(
                                            stringResource(
                                                R.string.brapi_conflicts_found,
                                                uiState.pendingConflictsCount
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        MergeConflictStrategy(
                                            selectedStrategy = selectedStrategy,
                                            onStrategyChange = { new -> selectedStrategy = new }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (selectedStrategy is MergeStrategy.Manual) {
                                            PendingConflictsList(
                                                conflicts = uiState.pendingConflicts,
                                                selectionMap = selectionMap,
                                                onToggleAllServer = {
                                                    uiState.pendingConflicts.forEach {
                                                        selectionMap[it.brapiId] = true
                                                    }
                                                },
                                                onToggleAllLocal = {
                                                    uiState.pendingConflicts.forEach {
                                                        selectionMap[it.brapiId] = false
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        // Show last-checked text for download if available (appears above the download button)
                        if (uiState.lastCheckedDownloadText != null) {
                            Text(
                                text = stringResource(
                                    R.string.last_checked,
                                    uiState.lastCheckedDownloadText
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        // Determine if there are unuploaded items that should prompt the user
                        val hasUnuploadedItems = (uiState.newObservationCount + uiState.editedObservationCount + uiState.newImageCount + uiState.editedImageCount) > 0

                        Button(
                            onClick = {
                                suppressConflictDialog = false
                                if (hasUnuploadedItems) {
                                    showUploadPrompt = true
                                } else {
                                    onDownloadClick()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.isInitialized && uiState.viewMode == ViewMode.IDLE
                        ) {
                            Text(stringResource(R.string.brapi_download_button))
                        }

                        if (showUploadPrompt) {
                            AppAlertDialog(
                                positiveButtonText = stringResource(R.string.upload_first),
                                negativeButtonText = stringResource(R.string.cancel),
                                neutralButtonText = stringResource(R.string.download_anyway),
                                onNeutral = {
                                    showUploadPrompt = false
                                    onDownloadClick()
                                },
                                onNegative = { showUploadPrompt = false },
                                title = stringResource(R.string.brapi_download_button),
                                content = {
                                    Text(
                                        stringResource(R.string.there_are_unuploaded_observations_or_images_do_you_want_to_upload_them_before_downloading)
                                    )
                                },
                                onPositive = {
                                    showUploadPrompt = false
                                    onExportClick()
                                }
                            )
                        }
                    }
                }
            }

            //the upload card
            InfoCard(
                title = stringResource(R.string.upload_to_brapi, uiState.brapiServerDisplayName),
                icon = painterResource(R.drawable.upload)
            ) {

                // Determine if there are observations or images to upload
                val totalObservations =
                    uiState.newObservationCount + uiState.editedObservationCount
                val totalImages = uiState.newImageCount + uiState.editedImageCount
                val hasObservationsToUpload = (totalObservations + totalImages) > 0

                if (uiState.viewMode == ViewMode.EXPORTING) {
                    ExportProgressIndicator(uiState.progress, Modifier.fillMaxWidth())
                    Button(onClick = onCancelExportClick, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cancel))
                    }
                } else {

                    if (uiState.uploadError != null) {
                        ResultRow(
                            when (uiState.uploadError) {
                                "401" -> stringResource(R.string.authentication_error)
                                else -> stringResource(
                                    R.string.brapi_upload_error,
                                    uiState.uploadError
                                )
                            },
                            painterResource(R.drawable.ic_transfer_error),
                            MaterialTheme.colorScheme.error
                        )
                    }

                    if (uiState.viewMode == ViewMode.IDLE) {
                        if (uiState.uploadInserts + uiState.uploadEdits + uiState.uploadFails + uiState.uploadImageInserts + uiState.uploadImageEdits + uiState.uploadImageFails > 0) {
                            UploadResultsCard(
                                inserts = uiState.uploadInserts,
                                updates = uiState.uploadEdits,
                                errors = uiState.uploadFails,
                                imageInserts = uiState.uploadImageInserts,
                                imageEdits = uiState.uploadImageEdits,
                                imageErrors = uiState.uploadImageFails,
                                label = stringResource(R.string.uploaded)
                            )
                        }

                        if (uiState.newObservationCount + uiState.editedObservationCount + uiState.newImageCount + uiState.editedImageCount > 0) {
                            CountRow(
                                stringResource(R.string.new_observations),
                                uiState.newObservationCount
                            )
                            CountRow(
                                stringResource(R.string.edited_observations),
                                uiState.editedObservationCount
                            )
                            CountRow(stringResource(R.string.new_images), uiState.newImageCount)
                            CountRow(
                                stringResource(R.string.edited_images),
                                uiState.editedImageCount
                            )
                        }

                    }

                    if (!hasObservationsToUpload) {
                        Text(
                            text = stringResource(R.string.no_observations_to_upload),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Show include images toggle only when there are images available to upload
                    if (totalImages > 0 && uiState.viewMode == ViewMode.IDLE) {
                        Row {
                            Switch(
                                checked = uiState.uploadImages,
                                onCheckedChange = onImageUploadToggle
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.include_images),
                                modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }

                // Show upload button only when there are observations/images to upload
                if (hasObservationsToUpload) {
                    Button(
                        onClick = onExportClick,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.isInitialized && uiState.viewMode == ViewMode.IDLE
                    ) {
                        Text(stringResource(R.string.brapi_upload_button))
                    }
                }
            }
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    icon: Painter,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun ResultsCard(
    inserts: Int,
    updates: Int,
    errors: Int = 0,
    label: String,
    cancelReason: String? = null
) {
    Column {
        if (cancelReason != null) {
            ResultRow(
                text = stringResource(R.string.operation_was_cancelled, cancelReason),
                icon = painterResource(R.drawable.ic_transfer_cancelled),
                tint = MaterialTheme.colorScheme.error
            )
        } else {
            if (inserts > 0) ResultRow(
                text = stringResource(R.string.new_items, inserts, label.lowercase()),
                icon = painterResource(if (label == stringResource(R.string.uploaded)) R.drawable.upload else R.drawable.download),
                tint = Color.Black
            )
            if (updates > 0) ResultRow(
                text = stringResource(R.string.edited_items, updates),
                icon = painterResource(R.drawable.pencil),
                tint = Color.Black
            )
            if (errors > 0) ResultRow(
                text = stringResource(R.string.failed_items, errors),
                icon = painterResource(R.drawable.ic_transfer_error),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun UploadResultsCard(
    inserts: Int,
    updates: Int,
    imageInserts: Int,
    imageEdits: Int,
    imageErrors: Int = 0,
    errors: Int = 0,
    label: String,
    cancelReason: String? = null
) {
    Column {
        if (cancelReason != null) {
            ResultRow(
                text = stringResource(R.string.user_cancelled_operation, cancelReason),
                icon = painterResource(R.drawable.ic_transfer_cancelled),
                tint = MaterialTheme.colorScheme.error
            )
        } else {
            if (inserts > 0) ResultRow(
                text = stringResource(R.string.new_items, inserts, label.lowercase()),
                icon = painterResource(R.drawable.ic_stats_observation),
                tint = Color.Black
            )
            if (updates > 0) ResultRow(
                text = stringResource(R.string.edited_items, updates),
                icon = painterResource(R.drawable.pencil),
                tint = Color.Black
            )
            if (imageInserts > 0) ResultRow(
                text = stringResource(R.string.new_images, imageInserts, label.lowercase()),
                icon = painterResource(R.drawable.ic_trait_camera),
                tint = Color.Black
            )
            if (imageEdits > 0) ResultRow(
                text = stringResource(R.string.edited_images, imageEdits),
                icon = painterResource(R.drawable.pencil),
                tint = Color.Black
            )
            if (imageErrors > 0) ResultRow(
                text = stringResource(R.string.failed_images, imageErrors),
                icon = painterResource(R.drawable.ic_transfer_error),
                tint = MaterialTheme.colorScheme.error
            )
            if (errors > 0) ResultRow(
                text = stringResource(R.string.failed_items, errors),
                icon = painterResource(R.drawable.ic_transfer_error),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}


@Composable
private fun ResultRow(text: String, icon: Painter, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tint
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun CountRow(label: String, count: Int) {
    if (count > 0) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(48.dp)
            )
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}


/**
 * A composable that displays a circular progress indicator for export/download operations.
 *
 * This UI component visualizes progress using a set of nested `CircularProgressIndicator`s.
 * It shows a primary progress ring, a secondary indeterminate spinning ring to indicate
 * active processing, and a background track.
 *
 * When the progress is complete (i.e., `progressState.current == progressState.total`),
 * it displays a large checkmark icon instead of the progress rings, although this won't currently show
 * without adding delays or a finished state in the viewmodel.
 *
 * Below the indicator, it displays a text message from the `progressState` to provide
 * context about the current operation (e.g., "Uploading file 5 of 10...").
 *
 * @param progressState The [Progress] object containing the current state of the operation,
 *   including the progress value and the message to display.
 * @param modifier The modifier to be applied to the `Column` that wraps the component.
 */
@Composable
fun ExportProgressIndicator(
    progressState: Progress,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (progressState.current == progressState.total && progressState.total > 0) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.complete),
                    modifier = Modifier.size(120.dp),
                    tint = Color.Black
                )
            } else {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(130.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.outline,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.0f)
                )
                CircularProgressIndicator(
                    progress = { progressState.primaryProgress },
                    modifier = Modifier.size(120.dp),
                    strokeWidth = 8.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(90.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = progressState.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * A UI component for selecting a merge conflict strategy.
 *
 * @param selectedStrategy The currently selected strategy.
 * @param onStrategyChange A callback invoked when the user selects a new strategy.
 * @param modifier The modifier to be applied to the component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeConflictStrategy(
    selectedStrategy: MergeStrategy,
    onStrategyChange: (MergeStrategy) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        MergeStrategy.Local,
        MergeStrategy.Server,
        MergeStrategy.MostRecent,
        MergeStrategy.Manual
    )
    val labels = options.map { opt ->
        when (opt) {
            is MergeStrategy.Local -> stringResource(R.string.local)
            is MergeStrategy.Server -> stringResource(R.string.server)
            is MergeStrategy.MostRecent -> stringResource(R.string.recent)
            is MergeStrategy.Manual -> stringResource(R.string.manual)
        }
    }

    // two rows layout: split options to avoid text wrapping, a bit complicated, but easier to add more options later
    val splitAt = (options.size + 1) / 2
    val firstRow = options.subList(0, splitAt)
    val secondRow = options.subList(splitAt, options.size)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
            firstRow.forEachIndexed { index, opt ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onStrategyChange(opt) }
                ) {
                    RadioButton(
                        selected = (opt == selectedStrategy),
                        onClick = { onStrategyChange(opt) })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = labels[index], maxLines = 1)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
            secondRow.forEachIndexed { index, opt ->
                // index offset for label lookup
                val label = labels[splitAt + index]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onStrategyChange(opt) }
                ) {
                    RadioButton(
                        selected = (opt == selectedStrategy),
                        onClick = { onStrategyChange(opt) })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = label, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun PendingConflictsList(
    conflicts: List<PendingConflictUi>,
    selectionMap: MutableMap<String, Boolean?>,
    onToggleAllServer: () -> Unit,
    onToggleAllLocal: () -> Unit
) {
    var globalChoice by remember { mutableStateOf(GlobalChoice.NONE) }

    Column {
        // Global toggle label + buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {

            // two evenly spaced toggle buttons that align with the per-item choice buttons
            val serverSelected = globalChoice == GlobalChoice.SERVER
            val localSelected = globalChoice == GlobalChoice.LOCAL

            Button(
                onClick = {
                    onToggleAllServer()
                    globalChoice = GlobalChoice.SERVER
                },
                modifier = Modifier.weight(0.5f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (serverSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (serverSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(modifier = Modifier
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.server_network),
                        contentDescription = stringResource(R.string.server),
                        tint = if (serverSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.server),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    onToggleAllLocal()
                    globalChoice = GlobalChoice.LOCAL
                },
                modifier = Modifier.weight(0.5f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (localSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (localSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(modifier = Modifier
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.cellphone),
                        contentDescription = stringResource(R.string.local),
                        tint = if (localSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.local),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        LazyColumn(modifier = Modifier.height(240.dp)) {
            itemsIndexed(conflicts) { _, c ->
                val id = c.brapiId
                // Treat only explicit 'true' as choosing server; null means unselected
                val chooseServer = selectionMap[id] == true

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {


                        Spacer(modifier = Modifier.height(8.dp))

                        // brapi id row (left-justified)
                        Text(
                            text = id,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp)
                        )

                        // Top row: Server button left, Local button right — now equal halves
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    selectionMap[id] = true
                                    // any manual selection should clear the global "all" toggle highlight
                                    globalChoice = GlobalChoice.NONE
                                },
                                modifier = Modifier.weight(0.5f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (chooseServer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    contentColor = if (chooseServer) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(
                                    text = c.serverValue,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    selectionMap[id] = false
                                    globalChoice = GlobalChoice.NONE
                                },
                                modifier = Modifier.weight(0.5f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!chooseServer && selectionMap[id] != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    contentColor = if (!chooseServer && selectionMap[id] != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(
                                    text = c.localValue,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
