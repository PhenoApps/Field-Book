package com.fieldbook.tracker.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.ui.theme.enums.AppTextType
import com.fieldbook.tracker.ui.theme.enums.AppThemeType
import com.fieldbook.tracker.zpl.TemplateRepository
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.labelprint.ui.LabelTemplateListItem
import org.phenoapps.labelprint.ui.LabelTemplatesScreen
import java.io.IOException
import javax.inject.Inject

/**
 * Lists label templates for adding, editing, duplicating, exporting, deleting and choosing the default.
 * Templates themselves are edited in [ZplEditorActivity].
 */
@AndroidEntryPoint
class LabelTemplatesActivity : ThemedActivity() {

    @Inject
    lateinit var templateRepository: TemplateRepository

    private val templates = mutableStateOf<List<LabelTemplateListItem>>(emptyList())

    private var pendingExportZpl: String = ""

    private val editorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { loadTemplates() }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(pendingExportZpl.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, getString(R.string.export_successful), Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Toast.makeText(this, getString(R.string.export_failed, e.message), Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val themeIndex = prefs.getString(PreferenceKeys.THEME, "0")?.toInt() ?: 0
            val themeType = when (themeIndex) {
                1 -> AppThemeType.HighContrast
                2 -> AppThemeType.Blue
                else -> AppThemeType.Default
            }
            val textIndex = prefs.getString(PreferenceKeys.TEXT_THEME, "1")?.toInt() ?: 1
            val textType = AppTextType.entries.find { it.index == textIndex } ?: AppTextType.MEDIUM

            AppTheme(themeTypeOverride = themeType, textTypeOverride = textType) {
                LabelTemplatesScreen(
                    templates = templates.value,
                    onBack = { finish() },
                    onAdd = { openEditor(templateId = null) },
                    onEdit = { id -> openEditor(templateId = id) },
                    onSetDefault = { id ->
                        templateRepository.setDefaultTemplateId(id)
                        loadTemplates()
                    },
                    onDuplicate = { id ->
                        templateRepository.duplicateTemplate(id)
                        loadTemplates()
                    },
                    onExport = ::exportTemplate,
                    onDelete = { id ->
                        templateRepository.deleteTemplate(id)
                        // picks a new default if the deleted template was the default
                        templateRepository.ensureDefaultTemplate()
                        CollectActivity.reloadData = true
                        loadTemplates()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadTemplates()
    }

    private fun loadTemplates() {
        templateRepository.ensureDefaultTemplate()
        val defaultId = templateRepository.getDefaultTemplateId()

        templates.value = templateRepository.getAllTemplates().entries
            .sortedBy { it.value.lowercase() }
            .map { (id, name) ->
                LabelTemplateListItem(
                    id = id,
                    name = name,
                    isDefault = id == defaultId,
                    traitCount = templateRepository.countTraitsUsing(id)
                )
            }
    }

    private fun openEditor(templateId: String?) {
        val intent = Intent(this, ZplEditorActivity::class.java).apply {
            if (templateId != null) putExtra(ZplEditorActivity.EXTRA_TEMPLATE_ID, templateId)
        }
        editorLauncher.launch(intent)
    }

    private fun exportTemplate(id: String) {
        val zpl = templateRepository.getTemplate(id) ?: return
        val name = templateRepository.getTemplateName(id) ?: "template"
        pendingExportZpl = zpl
        exportLauncher.launch("$name.zpl")
    }
}
