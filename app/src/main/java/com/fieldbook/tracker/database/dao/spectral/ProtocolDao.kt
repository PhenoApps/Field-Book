package com.fieldbook.tracker.database.dao.spectral

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.SpectralProtocolTable
import com.fieldbook.tracker.database.models.spectral.Protocol
import javax.inject.Inject

class ProtocolDao @Inject constructor(private val helper: DataHelper) {

    fun insertProtocol(protocol: Protocol): Long {
        helper.open()
        val values = ContentValues().apply {
            put(SpectralProtocolTable.EXTERNAL_ID, protocol.externalId)
            put(SpectralProtocolTable.TITLE, protocol.title)
            put(SpectralProtocolTable.DESCRIPTION, protocol.description)
            put(SpectralProtocolTable.WAVELENGTH_START, protocol.waveStart)
            put(SpectralProtocolTable.WAVELENGTH_END, protocol.waveEnd)
            put(SpectralProtocolTable.WAVELENGTH_STEP, protocol.waveStep)
        }
        return helper.db.insertWithOnConflict(
            SpectralProtocolTable.TABLE_NAME,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun getProtocolId(title: String): Int {
        helper.open()

        val cursor = helper.db.rawQuery(
            "SELECT ${SpectralProtocolTable.ID} FROM ${SpectralProtocolTable.TABLE_NAME} WHERE ${SpectralProtocolTable.TITLE} = ?",
            arrayOf(title)
        )

        cursor.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getColumnIndex(SpectralProtocolTable.ID)
                if (id >= 0) {
                    return cursor.getInt(id)
                }
            }
            return -1  // Return -1 if no matching protocol is found
        }
    }
}