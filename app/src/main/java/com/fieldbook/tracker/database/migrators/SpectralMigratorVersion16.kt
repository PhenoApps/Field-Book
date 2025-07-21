package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase
import com.fieldbook.tracker.database.Migrator
import com.fieldbook.tracker.database.SpectralProtocolTable
import com.fieldbook.tracker.database.SpectralFactTable
import com.fieldbook.tracker.database.SpectralDeviceTable
import com.fieldbook.tracker.database.SpectralUriTable

/**
 * Adds a Star schema for modeling spectral data.
 * Leaning towards Start/Snowflake approach vs 3NF since device address, and protocol description would be repeated numerously
 * Facts: fact_spectral
 * Dims: spectral_dim_protocol, spectral_dim_device
 *
 * Functional overview:
 *
 * spectral_dim_protocol(id, external_id, title, description, wave_start, wave_end, wave_step)
 *      external_id is the generic 'brapi' id
 *
 * spectral_dim_device(id, name, address)
 *
 * spectral_dim_uri(id, uri)
 *
 * facts_spectral(id, protocol_id, uri_id, device_id, observation_id, color, data, comment, created_at)
 *      data is the base64 encoded waves and lengths
 *      color is a string hex-value
 *      comment and created_at may be somewhat redundant with the observations table in FB, but I like it here
 *
 * Each scan will be added as a row to a trait-specific spectral-protocol-based file.
 * Therefore, each file will have a statically defined header, with all wavelength values for a given protocol.
 * Business logic will have to check if the connected device for a given trait respects this protocol.
 */
class SpectralMigratorVersion16: FieldBookMigrator {

    companion object {
        const val TAG = "SpectralMigratorVersion16"
        const val VERSION = 16
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {

        db.execSQL("DROP TABLE IF EXISTS spectral_dim_protocol;")
        db.execSQL("DROP TABLE IF EXISTS spectral_dim_device;")
        db.execSQL("DROP TABLE IF EXISTS spectral_dim_uri;")
        db.execSQL("DROP TABLE IF EXISTS facts_spectral;")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS ${SpectralProtocolTable.TABLE_NAME} (
                ${SpectralProtocolTable.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${SpectralProtocolTable.EXTERNAL_ID} TEXT NOT NULL UNIQUE,
                ${SpectralProtocolTable.TITLE} TEXT NOT NULL,
                ${SpectralProtocolTable.DESCRIPTION} TEXT NOT NULL,
                ${SpectralProtocolTable.WAVELENGTH_START} REAL NOT NULL,
                ${SpectralProtocolTable.WAVELENGTH_END} REAL NOT NULL,
                ${SpectralProtocolTable.WAVELENGTH_STEP} REAL NOT NULL
            );
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS ${SpectralDeviceTable.TABLE_NAME} (
                ${SpectralDeviceTable.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${SpectralDeviceTable.NAME} TEXT NOT NULL,
                ${SpectralDeviceTable.ADDRESS} TEXT NOT NULL
            );
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS ${SpectralUriTable.TABLE_NAME} (
                ${SpectralUriTable.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${SpectralUriTable.URI} TEXT NOT NULL
            );
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS ${SpectralFactTable.TABLE_NAME} (
                ${SpectralFactTable.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${SpectralFactTable.PROTOCOL_ID} INTEGER NOT NULL,
                ${SpectralFactTable.URI_ID} INTEGER NOT NULL,
                ${SpectralFactTable.DEVICE_ID} INTEGER NOT NULL,
                ${SpectralFactTable.OBSERVATION_ID} INTEGER NOT NULL,
                ${SpectralFactTable.COLOR} TEXT NOT NULL,
                ${SpectralFactTable.DATA} BLOB NOT NULL,
                ${SpectralFactTable.COMMENT} TEXT,
                ${SpectralFactTable.CREATED_AT} TEXT NOT NULL,
                FOREIGN KEY (${SpectralFactTable.OBSERVATION_ID}) REFERENCES ${Migrator.Observation.tableName}(${Migrator.Observation.PK}) ON DELETE CASCADE,
                FOREIGN KEY (${SpectralFactTable.PROTOCOL_ID}) REFERENCES ${SpectralProtocolTable.TABLE_NAME}(${SpectralProtocolTable.ID}),
                FOREIGN KEY (${SpectralFactTable.URI_ID}) REFERENCES ${SpectralUriTable.TABLE_NAME}(${SpectralUriTable.ID}),
                FOREIGN KEY (${SpectralFactTable.DEVICE_ID}) REFERENCES ${SpectralDeviceTable.TABLE_NAME}(${SpectralDeviceTable.ID})
            );
        """)
    }
}