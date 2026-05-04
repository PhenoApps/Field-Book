package com.fieldbook.shared.utilities

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.dir_plot_data
import com.fieldbook.shared.preferences.GeneralKeys
import com.russhwolf.settings.Settings
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

class DocumentTreeUtil {
    companion object {
        private fun plotDataDirectoryName(): String = runBlocking {
            getString(Res.string.dir_plot_data)
        }

        fun getPlotDataDirectory(): DocumentFile? =
            getDirectory(Res.string.dir_plot_data)

        fun getFieldMediaDirectory(traitName: String?): DocumentFile? {
            val prefs = Settings()
            val field = prefs.getString(GeneralKeys.FIELD_FILE.key, "")
            return getFieldMediaDirectory(field, traitName)
        }

        fun getFieldMediaDirectory(fieldName: String?, traitName: String?): DocumentFile? {
            if (traitName == null) return null
            val field = fieldName.orEmpty()
            if (field.isBlank()) return null

            val fieldDir = createDir(plotDataDirectoryName(), field) ?: return null
            var traitDir = fieldDir.findFile(traitName)
            if (traitDir == null || !traitDir.exists()) {
                fieldDir.createDirectory(traitName)
            }
            traitDir = fieldDir.findFile(traitName)
            if (traitDir != null && traitDir.findFile(".nomedia")?.exists() != true) {
                traitDir.createFile("*/*", ".nomedia")
            }
            return traitDir
        }

        fun getStudyMediaDirectory(studyName: String?): DocumentFile? {
            if (studyName.isNullOrBlank()) return null
            return createDir(plotDataDirectoryName(), studyName)
        }
    }
}
