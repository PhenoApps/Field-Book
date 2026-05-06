package org.phenoapps.brapi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phenoapps.brapi.R

@Composable
fun BrapiManualAccountForm(
    title: String,
    uiState: BrapiAccountUiState,
    onUrlChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onOidcUrlChange: (url: String, isUserEdit: Boolean) -> Unit,
    onOidcClientIdChange: (String) -> Unit,
    onOidcScopeChange: (String) -> Unit,
    onOidcFlowChange: (String) -> Unit,
    onBrapiVersionChange: (String) -> Unit,
    isEditMode: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val oidcFlowOptions = stringArrayResource(R.array.pheno_brapi_oidc_flow_options)
    val versionOptions = stringArrayResource(R.array.pheno_brapi_version_options)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .clickable { onCancel() },
        contentAlignment = Alignment.Center,
    ) {
        val scrollAreaMaxHeight = (maxHeight - 136.dp).coerceAtLeast(120.dp)

        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .pointerInput(Unit) { detectTapGestures {} },
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 8.dp),
                )

                Column(
                    modifier = Modifier
                        .heightIn(max = scrollAreaMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.url,
                        onValueChange = onUrlChange,
                        singleLine = true,
                        label = { Text(stringResource(R.string.pheno_brapi_base_url)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )

                    DetailsStep(
                        uiState = uiState,
                        versionOptions = versionOptions,
                        oidcFlowOptions = oidcFlowOptions,
                        onDisplayNameChange = onDisplayNameChange,
                        onBrapiVersionChange = onBrapiVersionChange,
                        onOidcFlowChange = onOidcFlowChange,
                        onOidcUrlChange = onOidcUrlChange,
                        onOidcClientIdChange = onOidcClientIdChange,
                        onOidcScopeChange = onOidcScopeChange,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.pheno_brapi_dialog_cancel))
                    }
                    TextButton(onClick = onConfirm) {
                        Text(
                            if (isEditMode) stringResource(R.string.pheno_brapi_dialog_save)
                            else stringResource(R.string.pheno_brapi_save_authorize),
                        )
                    }
                }
            }
        }
    }
}
