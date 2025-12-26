package com.fieldbook.shared.utilities

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.dir_plot_data
import com.fieldbook.shared.preferences.GeneralKeys
import com.russhwolf.settings.Settings

class DocumentTreeUtil {
    /**
     * Static functions to be used to handle exports.
     * These functions will attempt to create these directories if they do not exist.
     */
    companion object {
        fun getFieldMediaDirectory(traitName: String?): DocumentFile? {
            if (traitName == null) return null
            val prefs = Settings()
            val field = prefs.getString(GeneralKeys.FIELD_FILE.key, "")
            if (field.isNotBlank()) {
                val plotDataDirName = Res.string.dir_plot_data.key
                val fieldDir = createDir(plotDataDirName, field)
                if (fieldDir != null) {
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
            }
            return null
        }
    }
}
