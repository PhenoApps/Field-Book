package com.fieldbook.tracker.traits.tree

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.fieldbook.tracker.activities.TraitActivity
import com.fieldbook.tracker.dialogs.NewTraitDialog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object TreeInstrumentedFlows {

    fun openNewTraitDialog(activity: TraitActivity, context: Context) {
        val latch = CountDownLatch(1)
        activity.runOnUiThread {
            try {
                activity.supportFragmentManager.findFragmentByTag("NewTraitDialog")?.let { existing ->
                    if (existing is NewTraitDialog) {
                        existing.dismissAllowingStateLoss()
                    }
                }
                activity.supportFragmentManager.executePendingTransactions()
                NewTraitDialog(activity).show(activity.supportFragmentManager, "NewTraitDialog")
                activity.supportFragmentManager.executePendingTransactions()
            } finally {
                latch.countDown()
            }
        }
        check(latch.await(10, TimeUnit.SECONDS)) { "Timed out showing NewTraitDialog" }
        // Must not call waitForIdleSync from the main thread (ActivityScenario.onActivity).
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        TreeInstrumentedUi.waitForNewTraitDialog(context)
    }

    fun openTreeArchitectureCreator(activity: TraitActivity, context: Context) {
        openNewTraitDialog(activity, context)
        TreeInstrumentedUi.pickExperimentalTreeArchitecture(context)
    }

    fun openNewTraitDialog(scenario: ActivityScenario<TraitActivity>, context: Context) {
        lateinit var activity: TraitActivity
        scenario.onActivity { activity = it }
        openNewTraitDialog(activity, context)
    }

    fun openTreeArchitectureCreator(scenario: ActivityScenario<TraitActivity>, context: Context) {
        lateinit var activity: TraitActivity
        scenario.onActivity { activity = it }
        openTreeArchitectureCreator(activity, context)
    }
}
