package com.fieldbook.shared.screens.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.dialog_ok
import com.fieldbook.shared.generated.resources.dialog_warning
import com.fieldbook.shared.generated.resources.ic_adv_language
import com.fieldbook.shared.generated.resources.ic_am
import com.fieldbook.shared.generated.resources.ic_ar
import com.fieldbook.shared.generated.resources.ic_bn
import com.fieldbook.shared.generated.resources.ic_cn
import com.fieldbook.shared.generated.resources.ic_de
import com.fieldbook.shared.generated.resources.ic_fr
import com.fieldbook.shared.generated.resources.ic_in
import com.fieldbook.shared.generated.resources.ic_it
import com.fieldbook.shared.generated.resources.ic_jp
import com.fieldbook.shared.generated.resources.ic_mx
import com.fieldbook.shared.generated.resources.ic_om
import com.fieldbook.shared.generated.resources.ic_pt
import com.fieldbook.shared.generated.resources.ic_ru
import com.fieldbook.shared.generated.resources.ic_sv
import com.fieldbook.shared.generated.resources.ic_us
import com.fieldbook.shared.generated.resources.ic_vi
import com.fieldbook.shared.generated.resources.language_am
import com.fieldbook.shared.generated.resources.language_ar
import com.fieldbook.shared.generated.resources.language_bn
import com.fieldbook.shared.generated.resources.language_de
import com.fieldbook.shared.generated.resources.language_en
import com.fieldbook.shared.generated.resources.language_es
import com.fieldbook.shared.generated.resources.language_fr
import com.fieldbook.shared.generated.resources.language_hi
import com.fieldbook.shared.generated.resources.language_it
import com.fieldbook.shared.generated.resources.language_ja
import com.fieldbook.shared.generated.resources.language_om_rET
import com.fieldbook.shared.generated.resources.language_pt_rBR
import com.fieldbook.shared.generated.resources.language_ru
import com.fieldbook.shared.generated.resources.language_sv_rSE
import com.fieldbook.shared.generated.resources.language_vi_vRN
import com.fieldbook.shared.generated.resources.language_zh_rCN
import com.fieldbook.shared.generated.resources.preference_language_default
import com.fieldbook.shared.generated.resources.preference_language_warning
import com.fieldbook.shared.generated.resources.preferences_appearance_language
import com.fieldbook.shared.preferences.LanguageSelection
import com.fieldbook.shared.preferences.PreferenceKeys
import com.fieldbook.shared.preferences.resolveSystemLanguageSelection
import com.fieldbook.shared.theme.AlertDialog
import com.fieldbook.shared.theme.TextButton
import com.fieldbook.shared.utilities.onLanguageChanged
import com.russhwolf.settings.Settings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val DEFAULT_LANGUAGE_KEY = "com.fieldbook.tracker.preference.language.default"

private data class LanguageItem(
    val key: String,
    val title: StringResource,
    val icon: DrawableResource
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(
    onBack: (() -> Unit)? = null,
) {
    val settings = remember { Settings() }
    val languages = listOf(
        LanguageItem(DEFAULT_LANGUAGE_KEY, Res.string.preference_language_default, Res.drawable.ic_adv_language),
        LanguageItem("am-ET", Res.string.language_am, Res.drawable.ic_am),
        LanguageItem("ar-SA", Res.string.language_ar, Res.drawable.ic_ar),
        LanguageItem("bn-BD", Res.string.language_bn, Res.drawable.ic_bn),
        LanguageItem("de-DE", Res.string.language_de, Res.drawable.ic_de),
        LanguageItem("en-US", Res.string.language_en, Res.drawable.ic_us),
        LanguageItem("es-MX", Res.string.language_es, Res.drawable.ic_mx),
        LanguageItem("fr-FR", Res.string.language_fr, Res.drawable.ic_fr),
        LanguageItem("hi-IN", Res.string.language_hi, Res.drawable.ic_in),
        LanguageItem("it-IT", Res.string.language_it, Res.drawable.ic_it),
        LanguageItem("ja-JP", Res.string.language_ja, Res.drawable.ic_jp),
        LanguageItem("om-ET", Res.string.language_om_rET, Res.drawable.ic_om),
        LanguageItem("pt-BR", Res.string.language_pt_rBR, Res.drawable.ic_pt),
        LanguageItem("ru-RU", Res.string.language_ru, Res.drawable.ic_ru),
        LanguageItem("sv-SE", Res.string.language_sv_rSE, Res.drawable.ic_sv),
        LanguageItem("vi-VN", Res.string.language_vi_vRN, Res.drawable.ic_vi),
        LanguageItem("zh-CN", Res.string.language_zh_rCN, Res.drawable.ic_cn)
    )

    var selectedLanguageId by remember {
        mutableStateOf(settings.getString(PreferenceKeys.LANGUAGE_LOCALE_ID, ""))
    }
    var showWarning by remember { mutableStateOf(false) }

    fun selectLanguage(languageId: String, languageSummary: String) {
        val languageSelection = if (languageId == DEFAULT_LANGUAGE_KEY) {
            resolveSystemLanguageSelection(selectedLanguageId)
        } else {
            LanguageSelection(
                id = normalizeLanguageTag(languageId),
                summary = languageSummary
            )
        }
        val savedLanguageId = languageSelection.id
        selectedLanguageId = savedLanguageId
        settings.putString(PreferenceKeys.LANGUAGE_LOCALE_ID, savedLanguageId)
        settings.putString(PreferenceKeys.LANGUAGE_LOCALE_SUMMARY, languageSelection.summary)
        showWarning = true
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.preferences_appearance_language)) },
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
                items(languages) { language ->
                    val title = stringResource(language.title)
                    val selected = if (language.key == DEFAULT_LANGUAGE_KEY) {
                        selectedLanguageId.isBlank()
                    } else {
                        selectedLanguageId == language.key
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectLanguage(language.key, title) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(language.icon),
                            contentDescription = title,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp),
                            tint = Color.Unspecified
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = selected,
                            onClick = { selectLanguage(language.key, title) }
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(text = stringResource(Res.string.dialog_warning)) },
            text = { Text(text = stringResource(Res.string.preference_language_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWarning = false
                        onLanguageChanged()
                    }
                ) {
                    Text(stringResource(Res.string.dialog_ok))
                }
            }
        )
    }
}

private fun normalizeLanguageTag(languageTag: String): String {
    return when {
        languageTag.startsWith("iw") -> languageTag.replace("iw", "he")
        languageTag.startsWith("ji") -> languageTag.replace("ji", "yi")
        languageTag.startsWith("in") -> languageTag.replace("in", "id")
        else -> languageTag
    }
}
