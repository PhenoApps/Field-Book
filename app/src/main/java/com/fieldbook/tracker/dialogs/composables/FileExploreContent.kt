package com.fieldbook.tracker.dialogs.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.fieldbook.tracker.dialogs.FileExploreDialogFragment.FileItem

@Composable
fun FileExplorerContent(
    title: String,
    currentPath: DocumentFile?,
    loadFiles: (onComplete: (List<FileItem>) -> Unit) -> Unit,
    handleItemClick: (FileItem, onComplete: (List<FileItem>) -> Unit) -> Unit,
    cancelButtonText: String,
    onDismiss: () -> Unit,
) {
    var fileList by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // when dialog opens/path changes, load files
    LaunchedEffect(currentPath) {
        loadFiles { files ->
            fileList = files
            isLoading = false
        }
    }

    AppAlertDialog(
        title = title,
        content = { // file/dir list content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(fileList) { item ->
                            FileItemRow(
                                item = item,
                                onItemClick = {
                                    handleItemClick(item) { files ->
                                        fileList = files
                                        isLoading = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        negativeButtonText = cancelButtonText,
        onNegative = onDismiss,
    )
}

@Preview
@Composable
private fun FileExplorerContentPreview() {
    FileExplorerContent(
        title = "Title",
        currentPath = null,
        loadFiles = {},
        handleItemClick = { _, _ -> {} },
        cancelButtonText = "Cancel",
    ) { }
}