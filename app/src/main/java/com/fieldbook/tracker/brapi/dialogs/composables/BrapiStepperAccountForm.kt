package com.fieldbook.tracker.brapi.dialogs.composables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.drop
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.dialogs.BrapiAccountUiState
import com.fieldbook.tracker.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrapiStepperAccountForm(
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
    onScanBaseUrl: () -> Unit,
    onScanConfig: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onAuthorize: () -> Unit,
) {
    val oidcFlowOptions = stringArrayResource(R.array.pref_brapi_oidc_flow)
    val versionOptions = stringArrayResource(R.array.pref_brapi_version)

    // TextFieldState gives the new Foundation text engine, which properly auto-scrolls
    // the visible text when a cursor handle is dragged to either edge.
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

    val currentStep = uiState.currentStep

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
        // Reserve space for title (~56dp) + step indicator (~60dp) + buttons (~48dp) + card padding (~32dp).
        val scrollAreaMaxHeight = (maxHeight - 196.dp).coerceAtLeast(120.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .pointerInput(Unit) { detectTapGestures {} },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Dialog title
        Text(
            text = stringResource(R.string.brapi_add_account_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 8.dp),
        )

        // Step indicator
        StepIndicator(
            currentStep = currentStep,
            stepLabels = listOf("", "", ""),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )

        // Animated step content
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "step_content",
        ) { step ->
            Column(
                modifier = Modifier
                    .heightIn(max = scrollAreaMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            ) {
                when (step) {
                    0 -> {
                        // Step 1: Input method selection
                        val outline = MaterialTheme.colorScheme.outline
                        val options = listOf(
                            stringResource(R.string.brapi_add_account_guided_setup) to onNext,
                            stringResource(R.string.brapi_add_account_scan_config) to onScanConfig,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            options.forEach { (label, action) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, outline, RoundedCornerShape(8.dp))
                                        .clickable { action() }
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // Step 2: Server URL
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        ) {
                            OutlinedTextField(
                                state = urlFieldState,
                                lineLimits = TextFieldLineLimits.SingleLine,
                                label = { Text(stringResource(R.string.brapi_base_url)) },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = onScanBaseUrl) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_tb_barcode_scan),
                                    contentDescription = stringResource(R.string.dialog_scan),
                                )
                            }
                        }
                    }
                    2 -> {
                        // Step 3: Configuration details
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
                }
            }
        }

        // Sticky button bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            when (currentStep) {
                0 -> {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                }
                1 -> {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.dialog_back))
                    }
                    TextButton(onClick = onNext) {
                        Text(stringResource(R.string.dialog_next))
                    }
                }
                2 -> {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.dialog_back))
                    }
                    TextButton(onClick = onAuthorize) {
                        Text(stringResource(R.string.brapi_save_authorize))
                    }
                }
            }
        }
    }
    } // Card
    } // BoxWithConstraints
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    stepLabels: List<String>,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val outline = MaterialTheme.colorScheme.outline

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stepLabels.forEachIndexed { index, _ ->
                val isDone = index < currentStep
                val isCurrent = index == currentStep
                val isActive = isDone || isCurrent

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isActive) primary else MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, if (isActive) primary else outline, CircleShape),
                ) {
                    Text(
                        text = "${index + 1}",
                        color = if (isActive) onPrimary else outline,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (index < stepLabels.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        color = if (currentStep > index) primary else outline,
                        thickness = 1.5.dp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            stepLabels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index <= currentStep) primary else outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BrapiStepperAccountFormStep0Preview() {
    AppTheme {
        BrapiStepperAccountForm(
            uiState = BrapiAccountUiState(currentStep = 0),
            onUrlChange = {},
            onDisplayNameChange = {},
            onOidcUrlChange = { _, _ -> },
            onOidcClientIdChange = {},
            onOidcScopeChange = {},
            onOidcFlowChange = {},
            onBrapiVersionChange = {},
            onScanBaseUrl = {},
            onScanConfig = {},
            onNext = {},
            onBack = {},
            onCancel = {},
            onAuthorize = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BrapiStepperAccountFormStep1Preview() {
    AppTheme {
        BrapiStepperAccountForm(
            uiState = BrapiAccountUiState(
                currentStep = 1,
                url = "https://test.brapi.org",
                oidcUrl = "https://test.brapi.org/.well-known/openid-configuration",
            ),
            onUrlChange = {},
            onDisplayNameChange = {},
            onOidcUrlChange = { _, _ -> },
            onOidcClientIdChange = {},
            onOidcScopeChange = {},
            onOidcFlowChange = {},
            onBrapiVersionChange = {},
            onScanBaseUrl = {},
            onScanConfig = {},
            onNext = {},
            onBack = {},
            onCancel = {},
            onAuthorize = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BrapiStepperAccountFormStep2Preview() {
    AppTheme {
        BrapiStepperAccountForm(
            uiState = BrapiAccountUiState(
                currentStep = 2,
                url = "https://test.brapi.org",
                displayName = "Test BrAPI Server",
                oidcUrl = "https://test.brapi.org/.well-known/openid-configuration",
                oidcFlow = "Implicit",
                brapiVersion = "V2",
            ),
            onUrlChange = {},
            onDisplayNameChange = {},
            onOidcUrlChange = { _, _ -> },
            onOidcClientIdChange = {},
            onOidcScopeChange = {},
            onOidcFlowChange = {},
            onBrapiVersionChange = {},
            onScanBaseUrl = {},
            onScanConfig = {},
            onNext = {},
            onBack = {},
            onCancel = {},
            onAuthorize = {},
        )
    }
}
