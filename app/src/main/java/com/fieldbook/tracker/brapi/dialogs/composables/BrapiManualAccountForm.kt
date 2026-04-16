package com.fieldbook.tracker.brapi.dialogs.composables

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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.dialogs.BrapiAccountUiState
import com.fieldbook.tracker.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrapiManualAccountForm(
    title: String,
    uiState: BrapiAccountUiState,
    onUrlChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    /** Called for every OIDC URL change. [isUserEdit] = true marks it as user-initiated,
     *  locking out future auto-derivation from the base URL. */
    onOidcUrlChange: (url: String, isUserEdit: Boolean) -> Unit,
    onOidcClientIdChange: (String) -> Unit,
    onOidcScopeChange: (String) -> Unit,
    onOidcFlowChange: (String) -> Unit,
    onBrapiVersionChange: (String) -> Unit,
    isEditMode: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val oidcFlowOptions = stringArrayResource(R.array.pref_brapi_oidc_flow)
    val versionOptions = stringArrayResource(R.array.pref_brapi_version)

    // TextFieldState instances give the new Foundation text engine, which properly
    // auto-scrolls the visible text when a cursor handle is dragged to either edge.
    val urlFieldState = rememberTextFieldState(uiState.url)
    val displayNameFieldState = rememberTextFieldState(uiState.displayName)
    val oidcUrlFieldState = rememberTextFieldState(uiState.oidcUrl)
    val oidcClientIdFieldState = rememberTextFieldState(uiState.oidcClientId)
    val oidcScopeFieldState = rememberTextFieldState(uiState.oidcScope)

    // When the OIDC URL field is updated programmatically (ViewModel derived or external state
    // change) we store the expected value here so the snapshotFlow collector can distinguish
    // that update from genuine user typing and pass isUserEdit = false.
    val suppressOidcFlagFor = remember { mutableStateOf<String?>(null) }

    // ── Field → ViewModel (user typing) ──────────────────────────────────────
    LaunchedEffect(urlFieldState) {
        snapshotFlow { urlFieldState.text.toString() }
            .collect { onUrlChange(it) }
    }
    LaunchedEffect(displayNameFieldState) {
        snapshotFlow { displayNameFieldState.text.toString() }
            .collect { onDisplayNameChange(it) }
    }
    LaunchedEffect(oidcClientIdFieldState) {
        snapshotFlow { oidcClientIdFieldState.text.toString() }
            .collect { onOidcClientIdChange(it) }
    }
    LaunchedEffect(oidcScopeFieldState) {
        snapshotFlow { oidcScopeFieldState.text.toString() }
            .collect { onOidcScopeChange(it) }
    }
    // OIDC URL: .drop(1) skips the initial emission so first composition doesn't fire
    // onOidcUrlChange. The suppressOidcFlagFor check distinguishes programmatic updates
    // (from ViewModel auto-derivation or applyConfig) from genuine user edits.
    LaunchedEffect(oidcUrlFieldState) {
        snapshotFlow { oidcUrlFieldState.text.toString() }
            .drop(1)
            .collect { newVal ->
                val isUserEdit = newVal != suppressOidcFlagFor.value
                suppressOidcFlagFor.value = null
                onOidcUrlChange(newVal, isUserEdit)
            }
    }

    // ── ViewModel → Field (programmatic / external updates) ───────────────────
    LaunchedEffect(uiState.url) {
        if (urlFieldState.text.toString() != uiState.url) {
            urlFieldState.edit { replace(0, length, uiState.url) }
        }
    }
    LaunchedEffect(uiState.displayName) {
        if (displayNameFieldState.text.toString() != uiState.displayName) {
            displayNameFieldState.edit { replace(0, length, uiState.displayName) }
        }
    }
    LaunchedEffect(uiState.oidcUrl) {
        if (oidcUrlFieldState.text.toString() != uiState.oidcUrl) {
            // Mark this as a programmatic update so the snapshotFlow collector skips
            // setting isUserEdit = true.
            suppressOidcFlagFor.value = uiState.oidcUrl
            oidcUrlFieldState.edit { replace(0, length, uiState.oidcUrl) }
        }
    }
    LaunchedEffect(uiState.oidcClientId) {
        if (oidcClientIdFieldState.text.toString() != uiState.oidcClientId) {
            oidcClientIdFieldState.edit { replace(0, length, uiState.oidcClientId) }
        }
    }
    LaunchedEffect(uiState.oidcScope) {
        if (oidcScopeFieldState.text.toString() != uiState.oidcScope) {
            oidcScopeFieldState.edit { replace(0, length, uiState.oidcScope) }
        }
    }

    // BoxWithConstraints lets us read maxHeight, which shrinks when the keyboard is open
    // (because imePadding() reduces the available content area). We use it to constrain
    // the scrollable region so the card compresses rather than overflowing into the nav bar.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .clickable { onCancel() },
        contentAlignment = Alignment.Center,
    ) {
        // Reserve approximate space for title (~56dp) + buttons (~48dp) + card padding (~32dp).
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
                state = urlFieldState,
                lineLimits = TextFieldLineLimits.SingleLine,
                label = { Text(stringResource(R.string.brapi_base_url)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            OutlinedTextField(
                state = displayNameFieldState,
                lineLimits = TextFieldLineLimits.SingleLine,
                label = { Text(stringResource(R.string.brapi_display_name)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            RadioPickerField(
                value = uiState.brapiVersion,
                onValueChange = onBrapiVersionChange,
                label = stringResource(R.string.preferences_brapi_version),
                options = versionOptions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            RadioPickerField(
                value = uiState.oidcFlow,
                onValueChange = onOidcFlowChange,
                label = stringResource(R.string.preferences_brapi_oidc_flow),
                options = oidcFlowOptions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            OutlinedTextField(
                state = oidcUrlFieldState,
                lineLimits = TextFieldLineLimits.SingleLine,
                label = { Text(stringResource(R.string.brapi_oidc_url)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            OutlinedTextField(
                state = oidcClientIdFieldState,
                lineLimits = TextFieldLineLimits.SingleLine,
                label = { Text(stringResource(R.string.brapi_oidc_clientid)) },
                placeholder = { Text(stringResource(R.string.traits_create_optional)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            OutlinedTextField(
                state = oidcScopeFieldState,
                lineLimits = TextFieldLineLimits.SingleLine,
                label = { Text(stringResource(R.string.brapi_oidc_scope)) },
                placeholder = { Text(stringResource(R.string.traits_create_optional)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.dialog_cancel))
            }
            TextButton(onClick = onConfirm) {
                Text(
                    if (isEditMode) stringResource(R.string.dialog_save)
                    else stringResource(R.string.brapi_save_authorize)
                )
            }
        }
    }
    } // Card
    } // BoxWithConstraints
}

@Preview(showBackground = true)
@Composable
private fun BrapiManualAccountFormPreview() {
    AppTheme {
        BrapiManualAccountForm(
            title = "Add Account",
            uiState = BrapiAccountUiState(
                url = "https://test.brapi.org",
                displayName = "Test Server",
                oidcUrl = "https://test.brapi.org/.well-known/openid-configuration",
                oidcFlow = "OAuth2 Implicit Grant",
                brapiVersion = "V2",
            ),
            onUrlChange = {},
            onDisplayNameChange = {},
            onOidcUrlChange = { _, _ -> },
            onOidcClientIdChange = {},
            onOidcScopeChange = {},
            onOidcFlowChange = {},
            onBrapiVersionChange = {},
            isEditMode = false,
            onCancel = {},
            onConfirm = {},
        )
    }
}
