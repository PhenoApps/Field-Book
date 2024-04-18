package com.fieldbook.tracker.activities.brapi.hackathon.cropontology

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fieldbook.tracker.activities.brapi.hackathon.cropontology.converters.CategoryConverter
import com.fieldbook.tracker.activities.brapi.hackathon.cropontology.dao.BrapiStudyDao
import com.fieldbook.tracker.activities.brapi.hackathon.cropontology.dao.StudyFtsDao
import com.fieldbook.tracker.activities.brapi.hackathon.cropontology.tables.BrapiStudy
import com.fieldbook.tracker.activities.brapi.hackathon.cropontology.tables.BrapiStudyFts

@androidx.room.Database(entities = [BrapiStudy::class, BrapiStudyFts::class], version = 1)
@TypeConverters(CategoryConverter::class)
abstract class FtsDatabase : RoomDatabase() {

    abstract fun studyDao(): BrapiStudyDao
    abstract fun studyFtsDao(): StudyFtsDao

    companion object {

        fun getInstance(ctx: Context): FtsDatabase {

            return buildDatabase(ctx)

        }

        private fun buildDatabase(ctx: Context): FtsDatabase {
            return Room.inMemoryDatabaseBuilder(ctx, FtsDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }
    }
}