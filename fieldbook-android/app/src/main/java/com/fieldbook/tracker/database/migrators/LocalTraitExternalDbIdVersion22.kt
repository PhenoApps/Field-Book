package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase
import java.util.UUID

class LocalTraitExternalDbIdVersion22 : FieldBookMigrator {

    companion object {
        const val TAG = "LocalTraitExternalDbIdVersion22"
        const val VERSION = 22
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {
        val idsToUpdate = mutableListOf<String>()

        db.rawQuery(
            """SELECT internal_id_observation_variable FROM observation_variables
               WHERE (external_db_id IS NULL OR external_db_id = '')
                 AND (trait_data_source IS NULL OR trait_data_source = '' OR trait_data_source = 'local')""",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                idsToUpdate.add(cursor.getString(0))
            }
        }

        idsToUpdate.forEach { id ->
            val uuid = UUID.randomUUID().toString()
            db.execSQL(
                "UPDATE observation_variables SET external_db_id = ? WHERE internal_id_observation_variable = ?",
                arrayOf(uuid, id)
            )
        }
    }
}
