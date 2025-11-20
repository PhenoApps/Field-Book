package com.fieldbook.tracker.activities.brapi.io.sync

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R

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
fun BrapiExportScreen(
    uiState: BrapiExportUiState,
    onDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onExportClick: () -> Unit,
    onCancelExportClick: () -> Unit,
    onImageUploadToggle: (Boolean) -> Unit,
    onNavigateUp: () -> Unit,
    onAuthenticate: () -> Unit,
    onMergeStrategyChange: (MergeStrategy) -> Unit,
) {
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
                            contentDescription = stringResource(R.string.dialog_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onAuthenticate) {
                        Icon(
                            painter = painterResource(R.drawable.lock_reset),
                            contentDescription = stringResource(R.string.authenticate)
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            //the download card
            InfoCard(
                title = "Download from BrAPI",
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
                                MaterialTheme.colorScheme.primary
                            )
                        }
                        if (uiState.downloadError != null && uiState.downloadError.isNotEmpty()) {
                            ResultRow(
                                uiState.downloadError,
                                painterResource(R.drawable.ic_transfer_error),
                                MaterialTheme.colorScheme.error
                            )
                        }
                        MergeConflictStrategy(
                            selectedStrategy = uiState.downloadMergeStrategy,
                            onStrategyChange = onMergeStrategyChange
                        )
                        Button(
                            onClick = onDownloadClick,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.isInitialized && uiState.viewMode == ViewMode.IDLE
                        ) {
                            Text(stringResource(R.string.brapi_download_button))
                        }
                    }
                }
            }

            //the upload card
            InfoCard(
                title = stringResource(R.string.upload_to_brapi),
                icon = painterResource(R.drawable.upload)
            ) {
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
                            CountRow(stringResource(R.string.new_observations), uiState.newObservationCount)
                            CountRow(stringResource(R.string.edited_observations), uiState.editedObservationCount)
                            CountRow(stringResource(R.string.new_images), uiState.newImageCount)
                            CountRow(stringResource(R.string.edited_images), uiState.editedImageCount)
                        }

                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = uiState.uploadImages,
                            onCheckedChange = onImageUploadToggle
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.include_images))
                    }

                    Button(
                        onClick = onExportClick,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.isInitialized && uiState.viewMode == ViewMode.IDLE
                    ) {
                        Text(stringResource(R.string.brapi_upload_button))
                    }
                }
            }

            //the synced stats card
            InfoCard(
                title = stringResource(R.string.sync_statistics),
                icon = painterResource(R.drawable.ic_field_sync)
            ) {
                CountRow(stringResource(R.string.synced_observations), uiState.syncedObservationCount)
                CountRow(stringResource(R.string.synced_images), uiState.syncedImageCount)
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
                    tint = MaterialTheme.colorScheme.primary
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
                tint = MaterialTheme.colorScheme.primary
            )
            if (updates > 0) ResultRow(
                text = stringResource(R.string.edited_items, updates),
                icon = painterResource(R.drawable.pencil),
                tint = MaterialTheme.colorScheme.primary
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
                tint = MaterialTheme.colorScheme.primary
            )
            if (updates > 0) ResultRow(
                text = stringResource(R.string.edited_items, updates),
                icon = painterResource(R.drawable.pencil),
                tint = MaterialTheme.colorScheme.primary
            )
            if (imageInserts > 0) ResultRow(
                text = stringResource(R.string.new_images, imageInserts, label.lowercase()),
                icon = painterResource(R.drawable.ic_stats_photo),
                tint = MaterialTheme.colorScheme.primary
            )
            if (imageEdits > 0) ResultRow(
                text = stringResource(R.string.edited_images, imageEdits),
                icon = painterResource(R.drawable.pencil),
                tint = MaterialTheme.colorScheme.primary
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
                    tint = MaterialTheme.colorScheme.primary
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
    Text(stringResource(R.string.conflict_resolution_strategy))
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        listOf(
            MergeStrategy.Local, MergeStrategy.Server,
            MergeStrategy.MostRecent
        ).forEachIndexed { index, label ->
            SegmentedButton(
                selected = (label == selectedStrategy),
                onClick = { onStrategyChange(label) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
            ) {
                Text(
                    when (label) {
                        is MergeStrategy.Server -> stringResource(R.string.server)
                        is MergeStrategy.Local -> stringResource(R.string.local)
                        is MergeStrategy.MostRecent -> stringResource(R.string.recent)
                    }
                )
            }
        }
    }
}

@Preview
@Composable
fun MergeConflictStrategyPreview() {
    var selected: MergeStrategy by remember { mutableStateOf(MergeStrategy.Local) }
    FieldBookTheme {
        MergeConflictStrategy(
            selectedStrategy = selected,
            onStrategyChange = { newStrategy -> selected = newStrategy }
        )
    }
}

@Preview
@Composable
fun ExportProgressIndicatorPreview() {
    val progressState = Progress(
        message = "Uploading file 5 of 10...", current = 4, total = 10
    )
    ExportProgressIndicator(progressState = progressState)
}

@Preview
@Composable
fun CountRowPreview() {
    CountRow(label = "New Observations", count = 15)
}

@Preview
@Composable
fun ResultsCardPreview() {
    ResultsCard(inserts = 5, updates = 3, errors = 1, label = "Downloaded")
}

@Preview
@Composable
fun BrapiExportScreenPreview() {
    val uiState = BrapiExportUiState(
        progress = Progress(
            message = "Uploading file 5 of 10...", current = 4, total = 10
        ),
        isInitialized = true,
        study = null,
        viewMode = ViewMode.IDLE,
        newObservationCount = 10,
        editedObservationCount = 5,
        newImageCount = 2,
        editedImageCount = 1,
        syncedObservationCount = 100,
        syncedImageCount = 50,
    )

    FieldBookTheme {
        BrapiExportScreen(
            uiState = uiState,
            onDownloadClick = {},
            onMergeStrategyChange = {},
            onCancelDownloadClick = {},
            onExportClick = {},
            onCancelExportClick = {},
            onImageUploadToggle = {},
            onNavigateUp = {},
            onAuthenticate = {})
    }
}

@Preview
@Composable
fun InfoCardPreview() {
    InfoCard(
        title = "Sample Card Title",
        icon = painterResource(id = R.drawable.ic_field_sync)
    ) {
        Text("This is the content of the card.")
    }
}