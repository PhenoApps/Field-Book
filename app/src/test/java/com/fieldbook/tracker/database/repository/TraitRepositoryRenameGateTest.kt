package com.fieldbook.tracker.database.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.Formats
import java.io.File
import java.util.ArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Owner test for 09 §3/§4 TraitRepository rename gates:
 * - `hasTree` before [com.fieldbook.tracker.utilities.TreeSchemaLoader.repairTraitRefsAfterRename]
 * - media rename abort-on-partial (leave old folder intact)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TraitRepositoryRenameGateTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var database: DataHelper
    private lateinit var repo: TraitRepository

    private val repositorySource: String by lazy {
        File("src/main/java/com/fieldbook/tracker/database/repository/TraitRepository.kt").readText()
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("trait_repository_rename_gate_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        database = mock(DataHelper::class.java)
        `when`(database.allFieldObjects).thenReturn(ArrayList())
        repo = TraitRepository(context, database, prefs, Dispatchers.Unconfined)
    }

    @Test
    fun updateTrait_renameWithoutTree_skipsRepairResult() = runBlocking {
        val old = trait(id = "1", name = "Height", format = "numeric")
        val updated = trait(id = "1", name = "Plant height", format = "numeric")
        stubRename(old = old, updated = updated, allTraits = listOf(updated))

        val result = repo.updateTrait(updated)

        assertNull(
            "non-tree studies must skip repairTraitRefsAfterRename (traitRefRepair == null)",
            result.traitRefRepair,
        )
    }

    @Test
    fun updateTrait_renameWithTree_runsRepairPath() = runBlocking {
        val old = trait(id = "1", name = "Height", format = "numeric")
        val updated = trait(id = "1", name = "Plant height", format = "numeric")
        val tree = trait(
            id = "2",
            name = "Architecture",
            format = Formats.TREE_ARCHITECTURE.getDatabaseName(),
        )
        stubRename(old = old, updated = updated, allTraits = listOf(updated, tree))

        val result = repo.updateTrait(updated)

        assertNotNull(
            "studies with a tree architecture trait must call repair (non-null result)",
            result.traitRefRepair,
        )
    }

    @Test
    fun updateTrait_sameName_doesNotAttemptRepair() = runBlocking {
        val trait = trait(id = "1", name = "Height", format = "numeric")
        `when`(database.getTraitById("1")).thenReturn(trait)
        `when`(database.updateTrait(trait)).thenReturn(1L)

        val result = repo.updateTrait(trait)

        assertNull(result.traitRefRepair)
    }

    @Test
    fun hasTreeGate_skipsRepairWhenNoTreeArchitecture() {
        val renameBlock = repositorySource
            .substringAfter("suspend fun updateTrait(trait: TraitObject)")
            .substringBefore("/** Best-effort SAF rename")
        assertTrue(
            "updateTrait must compute hasTree from TREE_ARCHITECTURE format",
            renameBlock.contains("Formats.TREE_ARCHITECTURE.getDatabaseName()"),
        )
        assertTrue(
            "hasTree gate must short-circuit before repairTraitRefsAfterRename",
            renameBlock.contains("if (!hasTree)"),
        )
        val hasTreeIdx = renameBlock.indexOf("if (!hasTree)")
        val repairIdx = renameBlock.indexOf("TreeSchemaLoader.repairTraitRefsAfterRename")
        assertTrue(hasTreeIdx >= 0 && repairIdx > hasTreeIdx)
        assertTrue(
            renameBlock.substring(hasTreeIdx, repairIdx).contains("null"),
        )
    }

    @Test
    fun mediaRename_abortOnPartialLeavesOldFolder() {
        val renameBody = repositorySource
            .substringAfter("private fun renameTraitMediaFolderInField")
            .substringBefore("suspend fun updateTraitAlias")

        // Source contract (09 §4): abort-on-partial — never delete old folder on failure.
        assertTrue(renameBody.contains("aborting delete of \$oldFolder"))
        assertTrue(renameBody.contains("copy failed for \$name; leaving \$oldFolder intact"))
        assertTrue(renameBody.contains("leaving \$oldFolder intact"))

        val deleteIdx = renameBody.indexOf("oldDir.delete()")
        assertTrue("success path must delete old folder after full copy", deleteIdx >= 0)
        assertTrue(renameBody.indexOf("aborting delete") in 0 until deleteIdx)
        assertTrue(renameBody.indexOf("copy failed for") in 0 until deleteIdx)
        assertTrue(renameBody.indexOf("copied \$copied/") in 0 until deleteIdx)

        // Each failure Log.w is followed by an early return (no delete).
        val failureReturns = Regex(
            "Log\\.w\\(\\s*TAG,\\s*\"Trait media rename:[^\"]*\\\$oldFolder[^\"]*\"\\s*\\)\\s*\\n\\s*return\\b",
        ).findAll(renameBody).count()
        assertTrue("expected three abort/leave-intact early returns", failureReturns >= 3)
    }

    private fun stubRename(old: TraitObject, updated: TraitObject, allTraits: List<TraitObject>) {
        `when`(database.getTraitById(updated.id)).thenReturn(old)
        `when`(database.updateTrait(updated)).thenReturn(1L)
        `when`(database.getAllTraitObjects()).thenReturn(ArrayList(allTraits))
    }

    private fun trait(id: String, name: String, format: String): TraitObject =
        TraitObject().apply {
            this.id = id
            this.name = name
            this.format = format
        }
}
