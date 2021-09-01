package com.fieldbook.tracker.cropontology

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fieldbook.tracker.cropontology.converters.CategoryConverter
import com.fieldbook.tracker.cropontology.dao.VariableDao
import com.fieldbook.tracker.cropontology.dao.VariableFtsDao
import com.fieldbook.tracker.cropontology.tables.Variable
import com.fieldbook.tracker.cropontology.tables.VariableFts

@androidx.room.Database(entities = [Variable::class, VariableFts::class], version = 1)
@TypeConverters(CategoryConverter::class)
abstract class CropontologyDatabase : RoomDatabase() {

    abstract fun variablesDao(): VariableDao
    abstract fun variablesFtsDao(): VariableFtsDao

    companion object {

        fun getInstance(ctx: Context): CropontologyDatabase {

            return buildDatabase(ctx)

        }

        private fun buildDatabase(ctx: Context): CropontologyDatabase {
            return Room.inMemoryDatabaseBuilder(ctx, CropontologyDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }
    }
}