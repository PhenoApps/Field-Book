package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase

/**
 * Example migrator
 */
class RefactorMigratorVersion13: FieldBookMigrator {
    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {

        try {
            db.beginTransaction()
            refactorDatabase(db)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            db.endTransaction()
        }
    }

    private fun refactorDatabase(db: SQLiteDatabase) {

        //delete old tables
        db.execSQL("DROP TABLE IF EXISTS exp_id")
        db.execSQL("DROP TABLE IF EXISTS plot_attributes")
        db.execSQL("DROP TABLE IF EXISTS plot_values")
        db.execSQL("DROP TABLE IF EXISTS plots")
        db.execSQL("DROP TABLE IF EXISTS `range`")
        db.execSQL("DROP TABLE IF EXISTS user_traits")
        db.execSQL("DROP TABLE IF EXISTS traits")

        //drop unused columns
        db.execSQL("ALTER TABLE observation_units DROP COLUMN position_coordinate_x")
        db.execSQL("ALTER TABLE observation_units DROP COLUMN position_coordinate_y")
        db.execSQL("ALTER TABLE observation_units DROP COLUMN position_coordinate_x_type")
        db.execSQL("ALTER TABLE observation_units DROP COLUMN position_coordinate_y_type")
        db.execSQL("ALTER TABLE observation_units DROP COLUMN additional_info")
        db.execSQL("ALTER TABLE observation_units DROP COLUMN germplasm_db_id")
        db.execSQL("ALTER TABLE observation_units DROP COLUMN germplasm_name")
        db.execSQL("ALTER TABLE observation_units DROP COLUMN observation_level")

    }
}