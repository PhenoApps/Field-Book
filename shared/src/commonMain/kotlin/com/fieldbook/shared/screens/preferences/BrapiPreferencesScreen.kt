package com.fieldbook.shared.screens.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.brapi_base_url
import com.fieldbook.shared.generated.resources.brapi_base_url_default
import com.fieldbook.shared.generated.resources.brapi_base_url_desc
import com.fieldbook.shared.generated.resources.brapi_chunk_size
import com.fieldbook.shared.generated.resources.brapi_display_name
import com.fieldbook.shared.generated.resources.brapi_edit_display_name_default
import com.fieldbook.shared.generated.resources.brapi_oidc_clientid
import com.fieldbook.shared.generated.resources.brapi_oidc_clientid_default
import com.fieldbook.shared.generated.resources.brapi_oidc_clientid_desc
import com.fieldbook.shared.generated.resources.brapi_oidc_scope
import com.fieldbook.shared.generated.resources.brapi_oidc_scope_default
import com.fieldbook.shared.generated.resources.brapi_oidc_scope_desc
import com.fieldbook.shared.generated.resources.brapi_oidc_url
import com.fieldbook.shared.generated.resources.brapi_oidc_url_default
import com.fieldbook.shared.generated.resources.brapi_oidc_url_desc
import com.fieldbook.shared.generated.resources.brapi_pagination
import com.fieldbook.shared.generated.resources.brapi_revoke_auth
import com.fieldbook.shared.generated.resources.brapi_timeout
import com.fieldbook.shared.generated.resources.ic_adv_brapi_base
import com.fieldbook.shared.generated.resources.ic_pref_brapi_client_id
import com.fieldbook.shared.generated.resources.ic_pref_brapi_logout
import com.fieldbook.shared.generated.resources.ic_pref_brapi_name
import com.fieldbook.shared.generated.resources.ic_pref_brapi_pagination
import com.fieldbook.shared.generated.resources.ic_pref_brapi_scope
import com.fieldbook.shared.generated.resources.ic_pref_brapi_timeout
import com.fieldbook.shared.generated.resources.ic_pref_brapi_version
import com.fieldbook.shared.generated.resources.ic_tb_changelog
import com.fieldbook.shared.generated.resources.ic_transfer
import com.fieldbook.shared.generated.resources.ic_view_list_black_24dp
import com.fieldbook.shared.generated.resources.preferences_appearance_collect_labelval_customize
import com.fieldbook.shared.generated.resources.preferences_appearance_collect_labelval_customize_description
import com.fieldbook.shared.generated.resources.preferences_appearance_collect_labelval_customize_label
import com.fieldbook.shared.generated.resources.preferences_appearance_collect_labelval_customize_value
import com.fieldbook.shared.generated.resources.preferences_brapi
import com.fieldbook.shared.generated.resources.preferences_brapi_advanced_title
import com.fieldbook.shared.generated.resources.preferences_brapi_authorization_title
import com.fieldbook.shared.generated.resources.preferences_brapi_barcode_config_scan
import com.fieldbook.shared.generated.resources.preferences_brapi_barcode_config_share
import com.fieldbook.shared.generated.resources.preferences_brapi_barcode_config_summary
import com.fieldbook.shared.generated.resources.preferences_brapi_barcode_config_title
import com.fieldbook.shared.generated.resources.preferences_brapi_cache_invalidate_dialog_title
import com.fieldbook.shared.generated.resources.preferences_brapi_cache_invalidate_title
import com.fieldbook.shared.generated.resources.preferences_brapi_enable_summary
import com.fieldbook.shared.generated.resources.preferences_brapi_enable_title
import com.fieldbook.shared.generated.resources.preferences_brapi_oidc_flow
import com.fieldbook.shared.generated.resources.preferences_brapi_oidc_flow_oauth_code
import com.fieldbook.shared.generated.resources.preferences_brapi_oidc_flow_oauth_implicit
import com.fieldbook.shared.generated.resources.preferences_brapi_server_title
import com.fieldbook.shared.generated.resources.preferences_brapi_traits_title
import com.fieldbook.shared.generated.resources.preferences_brapi_version
import com.fieldbook.shared.generated.resources.preferences_brapi_version_v1
import com.fieldbook.shared.generated.resources.preferences_brapi_version_v2
import com.fieldbook.shared.generated.resources.prefs_brapi_cache_invalidate_choice_daily
import com.fieldbook.shared.generated.resources.prefs_brapi_cache_invalidate_choice_each_time
import com.fieldbook.shared.generated.resources.prefs_brapi_cache_invalidate_choice_none
import com.fieldbook.shared.generated.resources.prefs_brapi_cache_invalidate_choice_weekly
import com.fieldbook.shared.generated.resources.qr_code_share_choose_action_title
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrapiPreferencesScreen(
    onBack: (() -> Unit)? = null
) {
    val defaultBaseUrl = stringResource(Res.string.brapi_base_url_default)
    val defaultDisplayName = stringResource(Res.string.brapi_edit_display_name_default)
    val defaultOidcFlow = stringResource(Res.string.preferences_brapi_oidc_flow_oauth_implicit)
    val defaultOidcUrl = stringResource(Res.string.brapi_oidc_url_default)
    val defaultOidcClientId = stringResource(Res.string.brapi_oidc_clientid_default)
    val defaultOidcScope = stringResource(Res.string.brapi_oidc_scope_default)
    val defaultBrapiVersion = stringResource(Res.string.preferences_brapi_version_v2)
    val defaultCacheInvalidation = stringResource(Res.string.prefs_brapi_cache_invalidate_choice_each_time)
    val defaultValueDisplayMode = stringResource(Res.string.preferences_appearance_collect_labelval_customize_value)

    var brapiEnabled by remember { mutableStateOf(false) }
    var baseUrl by remember(defaultBaseUrl) { mutableStateOf(defaultBaseUrl) }
    var displayName by remember(defaultDisplayName) { mutableStateOf(defaultDisplayName) }
    var oidcFlow by remember(defaultOidcFlow) { mutableStateOf(defaultOidcFlow) }
    var oidcUrl by remember(defaultOidcUrl) { mutableStateOf(defaultOidcUrl) }
    var oidcClientId by remember(defaultOidcClientId) { mutableStateOf(defaultOidcClientId) }
    var oidcScope by remember(defaultOidcScope) { mutableStateOf(defaultOidcScope) }
    var brapiVersion by remember(defaultBrapiVersion) { mutableStateOf(defaultBrapiVersion) }
    var pageSize by remember { mutableStateOf("50") }
    var chunkSize by remember { mutableStateOf("500") }
    var timeout by remember { mutableStateOf("120") }
    var cacheInvalidation by remember(defaultCacheInvalidation) { mutableStateOf(defaultCacheInvalidation) }
    var valueDisplayMode by remember(defaultValueDisplayMode) { mutableStateOf(defaultValueDisplayMode) }
    var dialogState by remember { mutableStateOf<PreferenceDialogState?>(null) }

    val oidcFlowOptions = listOf(
        stringResource(Res.string.preferences_brapi_oidc_flow_oauth_code),
        stringResource(Res.string.preferences_brapi_oidc_flow_oauth_implicit)
    )
    val autoConfigOptions = listOf(
        stringResource(Res.string.preferences_brapi_barcode_config_scan),
        stringResource(Res.string.preferences_brapi_barcode_config_share),
    )
    val brapiVersionOptions = listOf(
        stringResource(Res.string.preferences_brapi_version_v1),
        stringResource(Res.string.preferences_brapi_version_v2)
    )
    val cacheInvalidationOptions = listOf(
        stringResource(Res.string.prefs_brapi_cache_invalidate_choice_each_time),
        stringResource(Res.string.prefs_brapi_cache_invalidate_choice_daily),
        stringResource(Res.string.prefs_brapi_cache_invalidate_choice_weekly),
        stringResource(Res.string.prefs_brapi_cache_invalidate_choice_none)
    )
    val valueDisplayOptions = listOf(
        stringResource(Res.string.preferences_appearance_collect_labelval_customize_value),
        stringResource(Res.string.preferences_appearance_collect_labelval_customize_label)
    )

    val sections = listOf(
        PreferenceSection(
            title = Res.string.preferences_brapi_server_title,
            items = listOf(
                PreferenceItem(
                    icon = Res.drawable.ic_adv_brapi_base,
                    title = Res.string.brapi_base_url,
                    summary = Res.string.brapi_base_url_desc,
                    value = baseUrl,
                    dialogType = PreferenceDialogType.TEXT
                ),
                PreferenceItem(
                    icon = Res.drawable.ic_pref_brapi_name,
                    title = Res.string.brapi_display_name,
                    value = displayName,
                    dialogType = PreferenceDialogType.TEXT
                ),
                PreferenceItem(
                    icon = Res.drawable.ic_pref_brapi_name,
                    title = Res.string.preferences_brapi_barcode_config_title,
                    summary = Res.string.preferences_brapi_barcode_config_summary,
                    dialogTitle = Res.string.qr_code_share_choose_action_title,
                    dialogType = PreferenceDialogType.OPTIONS,
                    options = autoConfigOptions
                ),
                PreferenceItem(
                    icon = Res.drawable.ic_pref_brapi_logout,
                    title = Res.string.brapi_revoke_auth,
                    dialogType = PreferenceDialogType.INFO,
                    isDestructive = true
                )
            )
        ),
        PreferenceSection(
            title = Res.string.preferences_brapi_authorization_title,
            items = listOf(
                PreferenceItem(
                    icon = Res.drawable.ic_pref_brapi_version,
                    title = Res.string.preferences_brapi_oidc_flow,
                    value = oidcFlow,
                    dialogType = PreferenceDialogType.OPTIONS,
                    options = oidcFlowOptions
                ),
                PreferenceItem(
                    icon = Res.drawable.ic_adv_brapi_base,
                    title = Res.string.brapi_oidc_url,
                    summary = Res.string.brapi_oidc_url_desc,
                    value = oidcUrl,
                    dialogType = PreferenceDialogType.TEXT
                ),
                PreferenceItem(
                    icon = Res.drawable.ic_pref_brapi_client_id,
                    title = Res.string.brapi_oidc_clientid,
                    summary = Res.string.brapi_oidc_clientid_desc,
                    value = oidcClientId,
                    dialogType = PreferenceDialogType.TEXT
                ),
                PreferenceItem(
                    icon = Res.drawable.ic_pref_brapi_scope,
                    title = Res.string.brapi_oidc_scope,
                    summary = Res.string.brapi_oidc_scope_desc,
                    value = oidcScope,
                    dialogType = PreferenceDialogType.TEXT
                )
            )
        ),
        PreferenceSection(
            title = Res.string.preferences_brapi_advanced_title,
            items = listOf(
                PreferenceItem(
                    icon = Res.drawable.ic_pref_brapi_version,
                    title = Res.string.preferences_brapi_version,
                    value = brapiVersion,
                    dialogType = PreferenceDialogType.OPTIONS,
                    options = brapiVersionOptions
                ),
                PreferenceItem(
                    icon = Res.drawable.ic_pref_brapi_pagination,
                    title = Res.string.brapi_pagination,
                    value = pageSize,
                    dialogType = PreferenceDialogType.TEXT
                ),
                PreferenceItem(
                    icon = Res.drawable.ic_transfer,
                    title = Res.string.brapi_chunk_size,
                    value = chunkSize,
                    dialogType = PreferenceDialogType.TEXT
                ),
                PreferenceItem(
                    icon = Res.drawable.ic_pref_brapi_timeout,
                    title = Res.string.brapi_timeout,
                    value = timeout,
                    dialogType = PreferenceDialogType.TEXT
                ),
                PreferenceItem(
                    icon = Res.drawable.ic_tb_changelog,
                    title = Res.string.preferences_brapi_cache_invalidate_title,
                    value = cacheInvalidation,
                    dialogTitle = Res.string.preferences_brapi_cache_invalidate_dialog_title,
                    dialogType = PreferenceDialogType.OPTIONS,
                    options = cacheInvalidationOptions
                )
            )
        ),
        PreferenceSection(
            title = Res.string.preferences_brapi_traits_title,
            items = listOf(
                PreferenceItem(
                    icon = Res.drawable.ic_view_list_black_24dp,
                    title = Res.string.preferences_appearance_collect_labelval_customize,
                    summary = Res.string.preferences_appearance_collect_labelval_customize_description,
                    value = valueDisplayMode,
                    dialogType = PreferenceDialogType.OPTIONS,
                    options = valueDisplayOptions
                )
            )
        )
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.preferences_brapi)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    PreferenceToggleRow(
                        title = stringResource(Res.string.preferences_brapi_enable_title),
                        summary = stringResource(Res.string.preferences_brapi_enable_summary),
                        checked = brapiEnabled,
                        onCheckedChange = { brapiEnabled = it }
                    )
                    HorizontalDivider()
                }

                if (brapiEnabled) {
                    sections.forEach { section ->
                        item {
                            Text(
                                text = stringResource(section.title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                            )
                        }
                        items(section.items) { item ->
                            PreferenceRow(
                                item = item,
                                onClick = {
                                    dialogState = when (item.title) {
                                        Res.string.brapi_base_url -> PreferenceDialogState(
                                            title = stringResource(item.dialogTitle),
                                            summary = item.summary?.let { stringResource(it) },
                                            type = item.dialogType,
                                            value = baseUrl,
                                            onSave = { baseUrl = it }
                                        )
                                        Res.string.brapi_display_name -> PreferenceDialogState(
                                            title = stringResource(item.dialogTitle),
                                            type = item.dialogType,
                                            value = displayName,
                                            onSave = { displayName = it }
                                        )
                                        Res.string.preferences_brapi_barcode_config_title -> PreferenceDialogState(
                                            title = stringResource(item.dialogTitle),
                                            summary = item.summary?.let { stringResource(it) },
                                            type = item.dialogType,
                                            options = item.options
                                        )
                                        Res.string.brapi_revoke_auth -> PreferenceDialogState(
                                            title = stringResource(item.dialogTitle),
                                            summary = "Layout placeholder only. Log out is not implemented yet.",
                                            type = item.dialogType
                                        )
                                        Res.string.preferences_brapi_oidc_flow -> PreferenceDialogState(
                                            title = stringResource(item.dialogTitle),
                                            type = item.dialogType,
                                            options = item.options,
                                            value = oidcFlow,
                                            onOptionSelected = { oidcFlow = it }
                                        )
                                        Res.string.brapi_oidc_url -> PreferenceDialogState(
                                            title = stringResource(item.dialogTitle),
                                            summary = item.summary?.let { stringResource(it) },
                                            type = item.dialogType,
                                            value = oidcUrl,
                                            onSave = { oidcUrl = it }
                                        )
                                        Res.string.brapi_oidc_clientid -> PreferenceDialogState(
                                            title = stringResource(item.dialogTitle),
                                            summary = item.summary?.let { stringResource(it) },
                                            type = item.dialogType,
                                            value = oidcClientId,
                                            onSave = { oidcClientId = it }
                                        )
                                        Res.string.brapi_oidc_scope -> PreferenceDialogState(
                                            title = stringResource(item.dialogTitle),
                                            summary = item.summary?.let { stringResource(it) },
                                            type = item.dialogType,
                                            value = oidcScope,
                                            onSave = { oidcScope = it }
                                        )
                                        Res.string.preferences_brapi_version -> PreferenceDialogState(
                                            title = stringResource(item.dialogTitle),
                                            type = item.dialogType,
                                            options = item.options,
                                            value = brapiVersion,
                                            onOptionSelected = { brapiVersion = it }
                                        )
                                        Res.string.brapi_pagination -> PreferenceDialogState(
                                            title = stringResource(item.dialogTitle),
                                            type = item.dialogType,
                                            value = pageSize,
                                            onSave = { pageSize = it }
                                        )
                                        Res.string.brapi_chunk_size -> PreferenceDialogState(
                                            title = stringResource(item.dialogTitle),
                                            type = item.dialogType,
                                            value = chunkSize,
                                            onSave = { chunkSize = it }
                                        )
                                        Res.string.brapi_timeout -> PreferenceDialogState(
                                            title = stringResource(item.dialogTitle),
                                            type = item.dialogType,
                                            value = timeout,
                                            onSave = { timeout = it }
                                        )
                                        Res.string.preferences_brapi_cache_invalidate_title -> PreferenceDialogState(
                                            title = stringResource(item.dialogTitle),
                                            type = item.dialogType,
                                            options = item.options,
                                            value = cacheInvalidation,
                                            onOptionSelected = { cacheInvalidation = it }
                                        )
                                        else -> PreferenceDialogState(
                                            title = stringResource(item.dialogTitle),
                                            summary = item.summary?.let { stringResource(it) },
                                            type = item.dialogType,
                                            options = item.options,
                                            value = valueDisplayMode,
                                            onOptionSelected = { valueDisplayMode = it }
                                        )
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    dialogState?.let { state ->
        when (state.type) {
            PreferenceDialogType.TEXT -> PreferenceTextDialog(
                state = state,
                onDismiss = { dialogState = null }
            )
            PreferenceDialogType.OPTIONS -> PreferenceOptionsDialog(
                state = state,
                onDismiss = { dialogState = null }
            )
            PreferenceDialogType.INFO -> PreferenceInfoDialog(
                state = state,
                onDismiss = { dialogState = null }
            )
        }
    }
}

