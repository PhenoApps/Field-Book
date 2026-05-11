package com.fieldbook.tracker.database.dao.spectral

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.SpectralUriTable
import javax.inject.Inject

class UriDao @Inject constructor(private val helper: DataHelper) {

    fun insertUri(uri: String): Long {
        helper.open()
        val values = ContentValues().apply {
            put(SpectralUriTable.URI, uri)
        }
        return helper.db.insertWithOnConflict(
            SpectralUriTable.TABLE_NAME,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun getUri(uriId: Int): String {
        helper.open()
        val cursor = helper.db.rawQuery(
            "SELECT ${SpectralUriTable.URI} FROM ${SpectralUriTable.TABLE_NAME} WHERE ${SpectralUriTable.ID} = ?",
            arrayOf(uriId.toString())
        )
        cursor.use { cursor ->
            if (cursor.moveToFirst()) {
                val uri = cursor.getColumnIndex(SpectralUriTable.URI)
                if (uri >= 0) {
                    return cursor.getString(uri)
                }
            }
            return ""  // Return empty string if no matching URI is found
        }
    }

    fun getUriId(uri: String): Int {
        helper.open()
        val cursor = helper.db.rawQuery(
            "SELECT ${SpectralUriTable.ID} FROM ${SpectralUriTable.TABLE_NAME} WHERE ${SpectralUriTable.URI} = ?",
            arrayOf(uri)
        )

        cursor.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getColumnIndex(SpectralUriTable.ID)
                if (id >= 0) {
                    return cursor.getInt(id)
                }
            }
            return -1  // Return -1 if no matching URI is found
        }
    }
}