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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.fieldbook.tracker.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrapiManualAccountForm(
    title: String,
    urlState: MutableState<String>,
    displayNameState: MutableState<String>,
    oidcUrlState: MutableState<String>,
    oidcClientIdState: MutableState<String>,
    oidcScopeState: MutableState<String>,
    oidcFlowState: MutableState<String>,
    brapiVersionState: MutableState<String>,
    oidcUrlExplicitlySetState: MutableState<Boolean>,
    isEditMode: Boolean,
    onFetchDisplayName: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val oidcFlowOptions = stringArrayResource(R.array.pref_brapi_oidc_flow)
    val versionOptions = stringArrayResource(R.array.pref_brapi_version)

    // TextFieldState instances give the new Foundation text engine, which properly
    // auto-scrolls the visible text when a cursor handle is dragged to either edge.
    val urlFieldState = rememberTextFieldState(urlState.value)
    val displayNameFieldState = rememberTextFieldState(displayNameState.value)
    val oidcUrlFieldState = rememberTextFieldState(oidcUrlState.value)
    val oidcClientIdFieldState = rememberTextFieldState(oidcClientIdState.value)
    val oidcScopeFieldState = rememberTextFieldState(oidcScopeState.value)

    // When the OIDC URL is updated programmatically (auto-derive or external state change)
    // we store the expected value here so the snapshotFlow collector can skip setting the
    // "explicitly set" flag for that particular update.
    val suppressOidcFlagFor = remember { mutableStateOf<String?>(null) }

    // ── Field → MutableState (user typing) ───────────────────────────────
    LaunchedEffect(urlFieldState) {
        snapshotFlow { urlFieldState.text.toString() }
            .collect { urlState.value = it }
    }
    LaunchedEffect(displayNameFieldState) {
        snapshotFlow { displayNameFieldState.text.toString() }
            .collect { displayNameState.value = it }
    }
    LaunchedEffect(oidcClientIdFieldState) {
        snapshotFlow { oidcClientIdFieldState.text.toString() }
            .collect { oidcClientIdState.value = it }
    }
    LaunchedEffect(oidcScopeFieldState) {
        snapshotFlow { oidcScopeFieldState.text.toString() }
            .collect { oidcScopeState.value = it }
    }
    // OIDC URL: also manage the explicitly-set flag, but only for genuine user edits.
    // .drop(1) skips the initial emission so composition itself doesn't trigger the flag.
    LaunchedEffect(oidcUrlFieldState) {
        snapshotFlow { oidcUrlFieldState.text.toString() }
            .drop(1)
            .collect { newVal ->
                if (newVal != suppressOidcFlagFor.value) {
                    oidcUrlExplicitlySetState.value = true
                }
                suppressOidcFlagFor.value = null
                oidcUrlState.value = newVal
            }
    }

    // ── MutableState → Field (external / programmatic updates) ────────────
    // url: also auto-derives the OIDC URL and kicks off the display-name fetch.
    LaunchedEffect(urlState.value) {
        if (urlFieldState.text.toString() != urlState.value) {
            urlFieldState.edit { replace(0, length, urlState.value) }
        }
        val raw = urlState.value.trim()
        if (raw.isNotEmpty() && raw != "https://") {
            if (!oidcUrlExplicitlySetState.value) {
                val derived = raw.trimEnd('/') + "/.well-known/openid-configuration"
                suppressOidcFlagFor.value = derived
                oidcUrlState.value = derived
            }
            onFetchDisplayName(raw)
        }
    }
    LaunchedEffect(displayNameState.value) {
        if (displayNameFieldState.text.toString() != displayNameState.value) {
            displayNameFieldState.edit { replace(0, length, displayNameState.value) }
        }
    }
    LaunchedEffect(oidcUrlState.value) {
        if (oidcUrlFieldState.text.toString() != oidcUrlState.value) {
            suppressOidcFlagFor.value = oidcUrlState.value
            oidcUrlFieldState.edit { replace(0, length, oidcUrlState.value) }
        }
    }
    LaunchedEffect(oidcClientIdState.value) {
        if (oidcClientIdFieldState.text.toString() != oidcClientIdState.value) {
            oidcClientIdFieldState.edit { replace(0, length, oidcClientIdState.value) }
        }
    }
    LaunchedEffect(oidcScopeState.value) {
        if (oidcScopeFieldState.text.toString() != oidcScopeState.value) {
            oidcScopeFieldState.edit { replace(0, length, oidcScopeState.value) }
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
        // Reserve approximate space for title (~56dp) + buttons (~48dp) + card padding (~32dp)
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
                value = brapiVersionState.value,
                onValueChange = { brapiVersionState.value = it },
                label = stringResource(R.string.preferences_brapi_version),
                options = versionOptions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            RadioPickerField(
                value = oidcFlowState.value,
                onValueChange = { oidcFlowState.value = it },
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
            urlState = remember { mutableStateOf("https://test.brapi.org") },
            displayNameState = remember { mutableStateOf("Test Server") },
            oidcUrlState = remember { mutableStateOf("https://test.brapi.org/.well-known/openid-configuration") },
            oidcClientIdState = remember { mutableStateOf("") },
            oidcScopeState = remember { mutableStateOf("") },
            oidcFlowState = remember { mutableStateOf("OAuth2 Implicit Grant") },
            brapiVersionState = remember { mutableStateOf("V2") },
            oidcUrlExplicitlySetState = remember { mutableStateOf(false) },
            isEditMode = false,
            onFetchDisplayName = {},
            onCancel = {},
            onConfirm = {},
        )
    }
}
