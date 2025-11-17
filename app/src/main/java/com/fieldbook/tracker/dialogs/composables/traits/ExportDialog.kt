package com.fieldbook.tracker.dialogs.composables.traits

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fieldbook.tracker.R
import com.fieldbook.tracker.dialogs.composables.AppAlertDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ExportDialog(
    onCancel: () -> Unit,
    onExport: (String) -> Unit
) {
    val timeStamp = SimpleDateFormat("yyyy-MM-dd-hh-mm-ss", Locale.getDefault())
    val defaultName = "trait_export_${timeStamp.format(Calendar.getInstance().time)}.trt"

    var fileName by remember { mutableStateOf(defaultName) }

    AppAlertDialog(
        title = stringResource(R.string.traits_dialog_export),
        content = {
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("Filename") },
                modifier = Modifier.Companion.fillMaxWidth(),
                singleLine = true
            )
        },
        positiveButtonText = stringResource(R.string.dialog_save),
        onPositive = { onExport(fileName) },
        negativeButtonText = stringResource(R.string.dialog_cancel),
        onNegative = onCancel
    )
}