package com.fieldbook.shared.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_pref_profile_person
import com.fieldbook.shared.generated.resources.ic_star_circle_outline
import com.fieldbook.shared.generated.resources.ic_pref_appearance
import com.fieldbook.shared.generated.resources.ic_pref_navigation
import com.fieldbook.shared.generated.resources.ic_map_search
import com.fieldbook.shared.generated.resources.ic_pref_sounds
import com.fieldbook.shared.generated.resources.ic_adv_brapi
import com.fieldbook.shared.generated.resources.ic_pref_system
import com.fieldbook.shared.generated.resources.ic_database_cog
import com.fieldbook.shared.generated.resources.ic_experimental
import com.fieldbook.shared.generated.resources.settings_profile
import com.fieldbook.shared.generated.resources.preferences_features_title
import com.fieldbook.shared.generated.resources.preferences_appearance_title
import com.fieldbook.shared.generated.resources.preferences_behavior_title
import com.fieldbook.shared.generated.resources.preferences_location_title
import com.fieldbook.shared.generated.resources.preferences_sounds
import com.fieldbook.shared.generated.resources.preferences_brapi
import com.fieldbook.shared.generated.resources.preferences_system_title
import com.fieldbook.shared.generated.resources.preferences_storage_title
import com.fieldbook.shared.generated.resources.preferences_experimental_title
import com.fieldbook.shared.theme.MainTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private data class PreferenceItem(
    val icon: DrawableResource,
    val title: StringResource,
    val key: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(onBack: (() -> Unit)? = null) {
    MainTheme {
        val preferenceItems = listOf(
            PreferenceItem(
                icon = Res.drawable.ic_pref_profile_person,
                title = Res.string.settings_profile,
                key = "pref_key_profile_settings"
            ),
            PreferenceItem(
                icon = Res.drawable.ic_star_circle_outline,
                title = Res.string.preferences_features_title,
                key = "pref_key_feature_settings"
            ),
            PreferenceItem(
                icon = Res.drawable.ic_pref_appearance,
                title = Res.string.preferences_appearance_title,
                key = "pref_key_ui_settings"
            ),
            PreferenceItem(
                icon = Res.drawable.ic_pref_navigation,
                title = Res.string.preferences_behavior_title,
                key = "pref_key_ux_settings"
            ),
            PreferenceItem(
                icon = Res.drawable.ic_map_search,
                title = Res.string.preferences_location_title,
                key = "pref_key_location_settings"
            ),
            PreferenceItem(
                icon = Res.drawable.ic_pref_sounds,
                title = Res.string.preferences_sounds,
                key = "pref_key_sounds_settings"
            ),
            PreferenceItem(
                icon = Res.drawable.ic_adv_brapi,
                title = Res.string.preferences_brapi,
                key = "pref_key_brapi_settings"
            ),
            PreferenceItem(
                icon = Res.drawable.ic_pref_system,
                title = Res.string.preferences_system_title,
                key = "pref_key_system_settings"
            ),
            PreferenceItem(
                icon = Res.drawable.ic_database_cog,
                title = Res.string.preferences_storage_title,
                key = "pref_key_storage_settings"
            ),
            PreferenceItem(
                icon = Res.drawable.ic_experimental,
                title = Res.string.preferences_experimental_title,
                key = "pref_key_experimental_settings"
            )
        )
        Surface(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(text = "Preferences") },
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
                    items(preferenceItems) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(item.icon),
                                contentDescription = item.key,
                                modifier = Modifier.padding(end = 16.dp).size(24.dp)
                            )
                            Text(
                                text = stringResource(item.title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Divider()
                    }
                }
            }
        }
    }
}
