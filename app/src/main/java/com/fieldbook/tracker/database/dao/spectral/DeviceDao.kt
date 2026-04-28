package com.fieldbook.tracker.database.dao.spectral

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.SpectralDeviceTable
import com.fieldbook.tracker.database.models.spectral.Device
import javax.inject.Inject

class DeviceDao @Inject constructor(private val helper: DataHelper) {

    fun insertDevice(device: Device): Long {
        helper.open()
        val values = ContentValues().apply {
            put(SpectralDeviceTable.NAME, device.name)
            put(SpectralDeviceTable.ADDRESS, device.address)
        }
        return helper.db.insertWithOnConflict(
            SpectralDeviceTable.TABLE_NAME,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun getDeviceId(name: String, address: String): Int {

        helper.open()

        val cursor = helper.db.rawQuery(
            "SELECT ${SpectralDeviceTable.ID} FROM ${SpectralDeviceTable.TABLE_NAME} WHERE ${SpectralDeviceTable.NAME} = ? AND ${SpectralDeviceTable.ADDRESS} = ?",
            arrayOf(name, address)
        )

        cursor.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getColumnIndex(SpectralDeviceTable.ID)
                if (id >= 0) {
                    return cursor.getInt(id)
                }
            }
            return -1  // Return -1 if no matching device is found
        }
    }

    fun getDevice(id: Int? = null, name: String? = null, address: String? = null): Device? {
        helper.open()

        val cursor = helper.db.rawQuery(
            "SELECT * FROM ${SpectralDeviceTable.TABLE_NAME} WHERE ${SpectralDeviceTable.ID} = ? OR ${SpectralDeviceTable.NAME} = ? OR ${SpectralDeviceTable.ADDRESS} = ?",
            arrayOf((id ?: -1).toString(), name ?: "", address ?: "")
        )

        cursor.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(SpectralDeviceTable.ID)
                val nameIndex = cursor.getColumnIndex(SpectralDeviceTable.NAME)
                val addressIndex = cursor.getColumnIndex(SpectralDeviceTable.ADDRESS)

                return Device(
                    id = cursor.getInt(idIndex),
                    name = cursor.getString(nameIndex),
                    address = cursor.getString(addressIndex)
                )
            }
        }

        return null  // Return a default device if no match is found
    }
}