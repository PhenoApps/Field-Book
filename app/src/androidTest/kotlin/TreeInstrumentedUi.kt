package com.fieldbook.tracker.traits.tree

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.fieldbook.tracker.R
import org.junit.Assert.assertTrue

object TreeInstrumentedUi {

    private fun device(): UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun waitForText(context: Context, resId: Int, timeoutMs: Long = 20_000): Boolean {
        val label = context.getString(resId)
        return waitForText(label, timeoutMs)
    }

    fun waitForText(label: String, timeoutMs: Long = 20_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (device().hasObject(By.text(label))) return true
            runCatching {
                device().findObjects(By.scrollable(true)).forEach {
                    runCatching { it.scroll(Direction.DOWN, 0.6f) }
                }
            }
            Thread.sleep(250)
        }
        return device().hasObject(By.text(label))
    }

    fun waitForNewTraitDialog(context: Context) {
        assertTrue(
            "New Trait dialog did not open",
            waitForText(context, R.string.trait_creator_title_layout),
        )
    }

    fun clickText(context: Context, resId: Int) {
        val label = context.getString(resId)
        clickText(label)
    }

    fun clickText(label: String) {
        val selector = By.text(label)
        assertTrue("Expected '$label' on screen", device().wait(Until.hasObject(selector), 20_000))
        repeat(3) { attempt ->
            try {
                device().findObject(selector)?.click()
                return
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                if (attempt == 2) throw AssertionError("Stale UI object for '$label'")
                Thread.sleep(250)
            }
        }
    }

    fun assertNotPresent(context: Context, resId: Int) {
        val label = context.getString(resId)
        assertTrue(
            "Did not expect '$label' on screen",
            device().wait(Until.gone(By.text(label)), 2_000),
        )
    }

    fun pickExperimentalTreeArchitecture(context: Context) {
        clickText(context, R.string.traits_format_experimental)
        clickText(context, R.string.traits_format_tree_architecture)
    }
}
