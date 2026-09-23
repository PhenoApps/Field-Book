package com.fieldbook.tracker.database.migrators

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.fieldbook.tracker.database.LabelTemplateTable
import com.fieldbook.tracker.zpl.TemplateRepository

class ZplTemplateMigratorVersion22 : FieldBookMigrator {

    companion object {
        const val VERSION = 22
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {
        // Create the label_templates table, names are unique since templates are saved by name
        db.execSQL("CREATE TABLE IF NOT EXISTS ${LabelTemplateTable.TABLE_NAME} (${LabelTemplateTable.ID} INTEGER PRIMARY KEY AUTOINCREMENT, ${LabelTemplateTable.NAME} TEXT NOT NULL UNIQUE, ${LabelTemplateTable.ZPL} TEXT)")

        // Install the built-in templates with the table, so every new or upgraded database gets them once
        for ((name, zpl) in TemplateRepository.BUILT_IN_TEMPLATES) {
            val values = ContentValues().apply {
                put(LabelTemplateTable.NAME, name)
                put(LabelTemplateTable.ZPL, zpl)
            }
            db.insertWithOnConflict(LabelTemplateTable.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }
}
