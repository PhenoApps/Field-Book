package com.fieldbook.tracker.traits.tree

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.TraitActivity
import com.fieldbook.tracker.traits.formats.Formats
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
/** Experimental picker + summary-trait creation for tree architecture. */
class TreeTraitCreateInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    companion object {
        @JvmStatic
        @BeforeClass
        fun prepareDevice() {
            TreeInstrumentedDevice.prepareHeadlessEmulator()
        }
    }

    @Before
    fun setUp() {
        TreeInstrumentedDevice.ensureWindowFocus()
        TreeInstrumentedSeed.enableExperimentalTraits()
    }

    @Test
    fun experimentalPicker_listsArchitectureNotSummary() {
        ActivityScenario.launch(TraitActivity::class.java).use { scenario ->
            TreeInstrumentedFlows.openNewTraitDialog(scenario, context)
            TreeInstrumentedUi.clickText(context, R.string.traits_format_experimental)
            assertTrue(TreeInstrumentedUi.waitForText(context, R.string.traits_format_tree_architecture))
            TreeInstrumentedUi.assertNotPresent(context, R.string.traits_format_tree_summary)
        }
    }

    @Test
    fun saveTreeArchitecture_doesNotCreateEmptySummaryTrait() {
        val traitName = "soy tree-carrier-test-${System.currentTimeMillis()}"
        ActivityScenario.launch(TraitActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                TreeInstrumentedSeed.seedStudyTraits(activity)
            }
            TreeInstrumentedFlows.openTreeArchitectureCreator(scenario, context)
            onView(withId(R.id.list_item_trait_parameter_name_et))
                .perform(replaceText(traitName), closeSoftKeyboard())
            onView(withId(R.id.list_item_trait_parameter_resource_file_et))
                .perform(replaceText("trait/tree_collect_smoke.trt"), closeSoftKeyboard())
            onView(withText(R.string.dialog_save)).perform(click())

            scenario.onActivity { activity ->
                runBlocking {
                    val traits = activity.traitRepo.getTraits()
                    val source = traits.firstOrNull { it.name == traitName }
                    val summary = traits.firstOrNull {
                        it.name == "$traitName (summary)" &&
                            it.format == Formats.TREE_SUMMARY.getDatabaseName()
                    }
                    assertTrue(source != null)
                    // Empty companion is deferred until a meaningful TreeSummary flush.
                    assertTrue(summary == null)
                }
            }
        }
    }
}
