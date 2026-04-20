package com.fieldbook.shared.utilities

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.dir_plot_data
import com.fieldbook.shared.preferences.GeneralKeys
import com.russhwolf.settings.Settings

class DocumentTreeUtil {
    companion object {
        fun getFieldMediaDirectory(traitName: String?): DocumentFile? {
            if (traitName == null) return null
            val prefs = Settings()
            val field = prefs.getString(GeneralKeys.FIELD_FILE.key, "")
            if (field.isBlank()) return null

            val fieldDir = createDir(Res.string.dir_plot_data.key, field) ?: return null
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
            return createDir(Res.string.dir_plot_data.key, studyName)
        }
    }
}
