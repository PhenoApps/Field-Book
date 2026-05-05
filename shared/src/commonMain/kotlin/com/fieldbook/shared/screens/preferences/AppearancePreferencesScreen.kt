package com.fieldbook.shared.screens.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.KmpHostScreenType
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_nav_drawer_translate
import com.fieldbook.shared.generated.resources.ic_pref_appearance_theme
import com.fieldbook.shared.generated.resources.preference_language_default
import com.fieldbook.shared.generated.resources.preferences_appearance_application_title
import com.fieldbook.shared.generated.resources.preferences_appearance_language
import com.fieldbook.shared.generated.resources.preferences_appearance_language_description
import com.fieldbook.shared.generated.resources.preferences_appearance_theme_summary
import com.fieldbook.shared.generated.resources.preferences_appearance_theme_title
import com.fieldbook.shared.generated.resources.preferences_appearance_title
import com.fieldbook.shared.preferences.PreferenceKeys
import com.russhwolf.settings.Settings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private data class AppearancePreferenceRow(
    val icon: DrawableResource,
    val title: StringResource,
    val summary: StringResource? = null,
    val value: String? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {}
)

private data class ToolbarOption(
    val value: String,
    val title: StringResource
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearancePreferencesScreen(
    onBack: (() -> Unit)? = null,
    onNavigate: ((KmpHostScreenType) -> Unit)? = null
) {
    val settings = remember { Settings() }
    val languageDefaultSummary = stringResource(Res.string.preference_language_default)

    val languageSummary = settings.getString(
        PreferenceKeys.LANGUAGE_LOCALE_SUMMARY,
        languageDefaultSummary
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.preferences_appearance_title)) },
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
                    PreferenceSectionTitle(Res.string.preferences_appearance_application_title)
                }
                item {
                    AppearancePreferenceListRow(
                        item = AppearancePreferenceRow(
                            icon = Res.drawable.ic_pref_appearance_theme,
                            title = Res.string.preferences_appearance_theme_title,
                            summary = Res.string.preferences_appearance_theme_summary,
                            enabled = false
                        )
                    )
                    HorizontalDivider()
                }
                item {
                    AppearancePreferenceListRow(
                        item = AppearancePreferenceRow(
                            icon = Res.drawable.ic_nav_drawer_translate,
                            title = Res.string.preferences_appearance_language,
                            summary = Res.string.preferences_appearance_language_description,
                            value = languageSummary,
                            onClick = {
                                onNavigate?.invoke(KmpHostScreenType.LANGUAGE_PREFERENCES)
                            }
                        )
                    )
                    HorizontalDivider()
                }

            }
        }
    }

}

@Composable
private fun PreferenceSectionTitle(title: StringResource) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
    )
}

@Composable
private fun AppearancePreferenceListRow(item: AppearancePreferenceRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.enabled, onClick = item.onClick)
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(item.title),
                style = MaterialTheme.typography.bodyLarge
            )
            item.value?.takeIf { it.isNotBlank() }?.let { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } ?: item.summary?.let { summary ->
                Text(
                    text = stringResource(summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

