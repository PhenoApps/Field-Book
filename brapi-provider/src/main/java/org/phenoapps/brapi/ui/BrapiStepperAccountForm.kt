package org.phenoapps.brapi.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phenoapps.brapi.R

@Composable
fun BrapiStepperAccountForm(
    uiState: BrapiAccountUiState,
    onUrlChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
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
    val oidcFlowOptions = stringArrayResource(R.array.pheno_brapi_oidc_flow_options)
    val versionOptions = stringArrayResource(R.array.pheno_brapi_version_options)
    val currentStep = uiState.currentStep

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .clickable { onCancel() },
        contentAlignment = Alignment.Center,
    ) {
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
                Text(
                    text = stringResource(R.string.pheno_brapi_add_account_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 8.dp),
                )

                StepIndicator(
                    currentStep = currentStep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                (slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "brapi_step_content",
                ) { step ->
                    Column(
                        modifier = Modifier
                            .heightIn(max = scrollAreaMaxHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        when (step) {
                            0 -> InputMethodStep(onNext, onScanConfig)
                            1 -> UrlStep(uiState.url, onUrlChange, onScanBaseUrl)
                            2 -> DetailsStep(
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
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    when (currentStep) {
                        0 -> TextButton(onClick = onCancel) {
                            Text(stringResource(R.string.pheno_brapi_dialog_cancel))
                        }
                        1 -> {
                            TextButton(onClick = onBack) {
                                Text(stringResource(R.string.pheno_brapi_dialog_back))
                            }
                            TextButton(onClick = onNext) {
                                Text(stringResource(R.string.pheno_brapi_dialog_next))
                            }
                        }
                        2 -> {
                            TextButton(onClick = onBack) {
                                Text(stringResource(R.string.pheno_brapi_dialog_back))
                            }
                            TextButton(onClick = onAuthorize) {
                                Text(stringResource(R.string.pheno_brapi_save_authorize))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InputMethodStep(
    onManualInput: () -> Unit,
    onScanConfig: () -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline
    val options = listOf(
        stringResource(R.string.pheno_brapi_add_account_guided_setup) to onManualInput,
        stringResource(R.string.pheno_brapi_add_account_scan_config) to onScanConfig,
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
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun UrlStep(
    url: String,
    onUrlChange: (String) -> Unit,
    onScanBaseUrl: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            singleLine = true,
            label = { Text(stringResource(R.string.pheno_brapi_base_url)) },
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onScanBaseUrl) {
            Icon(
                painter = painterResource(R.drawable.pheno_brapi_ic_barcode_scan),
                contentDescription = stringResource(R.string.pheno_brapi_dialog_scan),
            )
        }
    }
}

@Composable
internal fun DetailsStep(
    uiState: BrapiAccountUiState,
    versionOptions: Array<String>,
    oidcFlowOptions: Array<String>,
    onDisplayNameChange: (String) -> Unit,
    onBrapiVersionChange: (String) -> Unit,
    onOidcFlowChange: (String) -> Unit,
    onOidcUrlChange: (url: String, isUserEdit: Boolean) -> Unit,
    onOidcClientIdChange: (String) -> Unit,
    onOidcScopeChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = uiState.displayName,
        onValueChange = onDisplayNameChange,
        singleLine = true,
        label = { Text(stringResource(R.string.pheno_brapi_display_name)) },
        trailingIcon = {
            if (uiState.isFetchingDisplayName) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )

    RadioPickerField(
        value = uiState.brapiVersion,
        onValueChange = onBrapiVersionChange,
        label = stringResource(R.string.pheno_brapi_version),
        options = versionOptions,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )

    RadioPickerField(
        value = uiState.oidcFlow,
        onValueChange = onOidcFlowChange,
        label = stringResource(R.string.pheno_brapi_oidc_flow),
        options = oidcFlowOptions,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )

    OutlinedTextField(
        value = uiState.oidcUrl,
        onValueChange = { onOidcUrlChange(it, true) },
        singleLine = true,
        label = { Text(stringResource(R.string.pheno_brapi_oidc_url)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )

    OutlinedTextField(
        value = uiState.oidcClientId,
        onValueChange = onOidcClientIdChange,
        singleLine = true,
        label = { Text(stringResource(R.string.pheno_brapi_oidc_client_id)) },
        placeholder = { Text(stringResource(R.string.pheno_brapi_optional)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )

    OutlinedTextField(
        value = uiState.oidcScope,
        onValueChange = onOidcScopeChange,
        singleLine = true,
        label = { Text(stringResource(R.string.pheno_brapi_oidc_scope)) },
        placeholder = { Text(stringResource(R.string.pheno_brapi_optional)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
}

@Composable
private fun StepIndicator(
    currentStep: Int,
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
            repeat(3) { index ->
                val isActive = index <= currentStep
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

                if (index < 2) {
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

        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(3) { index ->
                Text(
                    text = "",
                    color = if (index <= currentStep) primary else outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
