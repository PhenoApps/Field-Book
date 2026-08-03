package com.fieldbook.tracker.database.viewmodels

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.TreeDerivedTraitHelper
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
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Owner test for Residual matrix seam: TraitDetailViewModel must keep export-only
 * tree-summary traits forced-hidden on updateTraitVisibility.
 * [TreeDerivedTraitHelper.isExportOnlySummary] alone does not cover VM coercion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TraitDetailViewModelExportOnlySummaryTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var database: DataHelper

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("trait_detail_export_only_summary_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        database = mock(DataHelper::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun updateTraitVisibility_keepsExportOnlySummaryForcedHidden() {
        val summary = trait(id = "sum-1", format = "tree summary", visible = false)
        assertTrue(TreeDerivedTraitHelper.isExportOnlySummary(summary))

        val vm = createViewModel()
        vm.updateTraitVisibility(summary, true)

        val state = vm.uiState.value as TraitDetailUiState.Success
        assertFalse(state.trait.visible)
        verify(database).updateTraitVisibility("sum-1", false)
    }

    @Test
    fun updateTraitVisibility_passesThroughRequestedVisibility_forNonSummary() {
        val text = trait(id = "text-1", format = "text", visible = true)
        assertFalse(TreeDerivedTraitHelper.isExportOnlySummary(text))

        val vm = createViewModel()

        vm.updateTraitVisibility(text, false)
        var state = vm.uiState.value as TraitDetailUiState.Success
        assertFalse(state.trait.visible)
        verify(database).updateTraitVisibility("text-1", false)

        vm.updateTraitVisibility(text, true)
        state = vm.uiState.value as TraitDetailUiState.Success
        assertTrue(state.trait.visible)
        verify(database).updateTraitVisibility("text-1", true)
    }

    private fun createViewModel(): TraitDetailViewModel {
        val repo = TraitRepository(
            context,
            database,
            prefs,
            Dispatchers.Unconfined,
        )
        return TraitDetailViewModel(repo, prefs, Dispatchers.Unconfined)
    }

    private fun trait(id: String, format: String, visible: Boolean): TraitObject =
        TraitObject().apply {
            this.id = id
            this.name = id
            this.format = format
            this.visible = visible
        }
}
