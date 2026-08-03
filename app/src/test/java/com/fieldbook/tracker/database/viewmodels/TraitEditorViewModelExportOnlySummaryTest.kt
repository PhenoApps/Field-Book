package com.fieldbook.tracker.database.viewmodels

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.BrapiAccountHelper
import com.fieldbook.tracker.utilities.TreeDerivedTraitHelper
import java.util.ArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Owner test for Avoidable matrix seam: TraitEditorViewModel must keep export-only
 * tree-summary traits forced-hidden on load / updateTraitVisibility / toggleAll.
 * [TreeDerivedTraitHelper.isExportOnlySummary] alone does not cover VM coercion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TraitEditorViewModelExportOnlySummaryTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var database: DataHelper

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("trait_editor_export_only_summary_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        database = mock(DataHelper::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadTraits_forcesExportOnlySummaryHidden_leavesOthersUnchanged() {
        val summary = trait(id = "sum-1", format = "tree summary", visible = true)
        val architecture = trait(id = "arch-1", format = "tree architecture", visible = true)
        val text = trait(id = "text-1", format = "text", visible = false)
        stubTraits(summary, architecture, text)

        val vm = createViewModel()

        val byId = vm.uiState.value.traits.associateBy { it.id }
        assertFalse(byId.getValue("sum-1").visible)
        assertTrue(byId.getValue("arch-1").visible)
        assertFalse(byId.getValue("text-1").visible)
        assertTrue(TreeDerivedTraitHelper.isExportOnlySummary(byId.getValue("sum-1")))
        assertFalse(TreeDerivedTraitHelper.isExportOnlySummary(byId.getValue("arch-1")))
        assertFalse(TreeDerivedTraitHelper.isExportOnlySummary(byId.getValue("text-1")))

        verify(database).updateTraitVisibility("sum-1", false)
    }

    @Test
    fun updateTraitVisibility_keepsExportOnlySummaryForcedHidden() {
        stubTraits(
            trait(id = "sum-1", format = "tree summary", visible = false),
            trait(id = "text-1", format = "text", visible = true),
        )
        val vm = createViewModel()

        vm.updateTraitVisibility("sum-1", true)
        assertFalse(vm.uiState.value.traits.first { it.id == "sum-1" }.visible)
        verify(database, times(1)).updateTraitVisibility("sum-1", false)

        vm.updateTraitVisibility("text-1", false)
        assertFalse(vm.uiState.value.traits.first { it.id == "text-1" }.visible)
        verify(database).updateTraitVisibility("text-1", false)

        vm.updateTraitVisibility("text-1", true)
        assertTrue(vm.uiState.value.traits.first { it.id == "text-1" }.visible)
        verify(database).updateTraitVisibility("text-1", true)
    }

    @Test
    fun toggleAllTraitsVisibility_neverShowsExportOnlySummary() {
        stubTraits(
            trait(id = "sum-1", format = "tree summary", visible = true),
            trait(id = "text-1", format = "text", visible = false),
            trait(id = "num-1", format = "numeric", visible = false),
        )
        val vm = createViewModel()
        assertFalse(vm.uiState.value.traits.first { it.id == "sum-1" }.visible)

        // Show all toggleable traits; summary stays hidden.
        vm.toggleAllTraitsVisibility()
        var byId = vm.uiState.value.traits.associateBy { it.id }
        assertFalse(byId.getValue("sum-1").visible)
        assertTrue(byId.getValue("text-1").visible)
        assertTrue(byId.getValue("num-1").visible)
        verify(database, times(2)).updateTraitVisibility("sum-1", false)

        // Hide all toggleable traits; summary stays hidden.
        vm.toggleAllTraitsVisibility()
        byId = vm.uiState.value.traits.associateBy { it.id }
        assertFalse(byId.getValue("sum-1").visible)
        assertFalse(byId.getValue("text-1").visible)
        assertFalse(byId.getValue("num-1").visible)
        verify(database, times(3)).updateTraitVisibility("sum-1", false)
    }

    private fun createViewModel(): TraitEditorViewModel {
        val repo = TraitRepository(
            context,
            database,
            prefs,
            Dispatchers.Unconfined,
        )
        val brapi = mock(BrapiAccountHelper::class.java)
        return TraitEditorViewModel(repo, prefs, brapi)
    }

    private fun stubTraits(vararg traits: TraitObject) {
        `when`(database.getAllTraitObjects()).thenReturn(ArrayList(traits.toList()))
    }

    private fun trait(id: String, format: String, visible: Boolean): TraitObject =
        TraitObject().apply {
            this.id = id
            this.name = id
            this.format = format
            this.visible = visible
        }
}
