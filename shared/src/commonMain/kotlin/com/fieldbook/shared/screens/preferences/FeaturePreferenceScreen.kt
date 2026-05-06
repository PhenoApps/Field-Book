package com.fieldbook.shared.screens.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_tb_barcode_scan
import com.fieldbook.shared.generated.resources.ic_tb_datagrid
import com.fieldbook.shared.generated.resources.ic_tb_forward
import com.fieldbook.shared.generated.resources.ic_tb_help
import com.fieldbook.shared.generated.resources.preferences_appearance_application_title
import com.fieldbook.shared.generated.resources.preferences_appearance_collect_toolbar_title
import com.fieldbook.shared.generated.resources.preferences_appearance_tutorial_description
import com.fieldbook.shared.generated.resources.preferences_features_title
import com.fieldbook.shared.generated.resources.preferences_general_feature_barcode_text
import com.fieldbook.shared.generated.resources.preferences_general_feature_datagrid
import com.fieldbook.shared.generated.resources.preferences_general_feature_datagrid_description
import com.fieldbook.shared.generated.resources.preferences_general_feature_next_missing
import com.fieldbook.shared.generated.resources.tutorial_dialog_title
import com.fieldbook.shared.preferences.PreferenceKeys
import com.russhwolf.settings.Settings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private data class FeaturePreferenceRow(
    val icon: DrawableResource,
    val title: StringResource,
    val summary: StringResource? = null,
    val checked: Boolean = false,
    val enabled: Boolean = false,
    val onCheckedChange: (Boolean) -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturePreferenceScreen(
    onBack: (() -> Unit)? = null
) {
    val settings = remember { Settings() }
    var datagridEnabled by remember {
        mutableStateOf(settings.getBoolean(PreferenceKeys.DATAGRID_SETTING, false))
    }

    fun setDatagridEnabled(enabled: Boolean) {
        datagridEnabled = enabled
        settings.putBoolean(PreferenceKeys.DATAGRID_SETTING, enabled)
    }

    val systemFeatures = listOf(
        FeaturePreferenceRow(
            icon = Res.drawable.ic_tb_help,
            title = Res.string.tutorial_dialog_title,
            summary = Res.string.preferences_appearance_tutorial_description
        )
    )
    val toolbarFeatures = listOf(
        FeaturePreferenceRow(
            icon = Res.drawable.ic_tb_datagrid,
            title = Res.string.preferences_general_feature_datagrid,
            summary = Res.string.preferences_general_feature_datagrid_description,
            checked = datagridEnabled,
            enabled = true,
            onCheckedChange = ::setDatagridEnabled
        ),
        FeaturePreferenceRow(
            icon = Res.drawable.ic_tb_forward,
            title = Res.string.preferences_general_feature_next_missing
        ),
        FeaturePreferenceRow(
            icon = Res.drawable.ic_tb_barcode_scan,
            title = Res.string.preferences_general_feature_barcode_text
        )
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.preferences_features_title)) },
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
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    FeaturePreferenceSectionTitle(Res.string.preferences_appearance_application_title)
                }
                systemFeatures.forEach { feature ->
                    item {
                        FeaturePreferenceListRow(feature)
                        HorizontalDivider()
                    }
                }
                item {
                    FeaturePreferenceSectionTitle(Res.string.preferences_appearance_collect_toolbar_title)
                }
                toolbarFeatures.forEach { feature ->
                    item {
                        FeaturePreferenceListRow(feature)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturePreferenceListRow(item: FeaturePreferenceRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(enabled = item.enabled) { item.onCheckedChange(!item.checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .graphicsLayer { alpha = if (item.enabled) 1f else 0.4f },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = stringResource(item.title),
            modifier = Modifier
                .padding(end = 16.dp)
                .size(24.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = stringResource(item.title),
                style = MaterialTheme.typography.bodyLarge
            )
            item.summary?.let { summary ->
                Text(
                    text = stringResource(summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (item.enabled) {
            Switch(
                checked = item.checked,
                onCheckedChange = item.onCheckedChange
            )
        }
    }
}

@Composable
private fun FeaturePreferenceSectionTitle(title: StringResource) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
    )
}
