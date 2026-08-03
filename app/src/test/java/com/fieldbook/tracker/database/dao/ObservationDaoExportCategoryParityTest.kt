package com.fieldbook.tracker.database.dao

import com.fieldbook.tracker.traits.formats.Formats
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Parity: non-tree BrAPI export buckets match main's in-DAO `when`;
 * local tree architecture stays uploadable via TreeBrapiExportRouting.
 */
class ObservationDaoExportCategoryParityTest {

    private val hostUrl = "https://brapi.example"

    @Test
    fun localNonTreeTraitRoutesToUserCreated() {
        val category = ObservationDao.resolveBrAPIExportCategory(
            format = "numeric",
            source = "local",
            hostUrl = hostUrl,
            dbId = null,
            timestamp = null,
            lastSyncedTime = null,
            isPhoto = false,
        )
        assertEquals("userCreatedTraitObservations", category)
    }

    @Test
    fun localTreeTraitRoutesToNewObservationsNotUserCreated() {
        val category = ObservationDao.resolveBrAPIExportCategory(
            format = Formats.TREE_ARCHITECTURE.getDatabaseName(),
            source = "local",
            hostUrl = hostUrl,
            dbId = null,
            timestamp = null,
            lastSyncedTime = null,
            isPhoto = false,
        )
        assertEquals("newObservations", category)
    }
}
