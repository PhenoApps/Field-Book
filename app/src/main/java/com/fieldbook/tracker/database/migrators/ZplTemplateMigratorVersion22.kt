package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase
import com.fieldbook.tracker.database.LabelTemplateTable

class ZplTemplateMigratorVersion22 : FieldBookMigrator {

    companion object {
        const val VERSION = 22
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {
        // Create the label_templates table
        db.execSQL("CREATE TABLE IF NOT EXISTS ${LabelTemplateTable.TABLE_NAME} (${LabelTemplateTable.ID} INTEGER PRIMARY KEY AUTOINCREMENT, ${LabelTemplateTable.NAME} TEXT, ${LabelTemplateTable.ZPL} TEXT)")
    }
}
