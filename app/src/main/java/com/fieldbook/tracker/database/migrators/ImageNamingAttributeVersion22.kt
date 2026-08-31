package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase

/**
 * Adds the observation unit image naming attribute column to the studies table.
 * This attribute is used to name captured media, by default it's the same as the unique id.
 */
class ImageNamingAttributeVersion22 : FieldBookMigrator {

    companion object {
        const val TAG = "ImageNamingAttributeVersion22"
        const val VERSION = 22
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {
        db.execSQL("ALTER TABLE studies ADD COLUMN observation_unit_image_naming_attribute TEXT")
        db.execSQL("UPDATE studies SET observation_unit_image_naming_attribute = study_unique_id_name")
    }
}
