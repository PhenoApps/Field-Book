package com.fieldbook.tracker.traits.tree

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

object TreeInstrumentedDevice {

    fun prepareHeadlessEmulator() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        shell.executeShellCommand("settings put global window_animation_scale 0")
        shell.executeShellCommand("settings put global transition_animation_scale 0")
        shell.executeShellCommand("settings put global animator_duration_scale 0")
        // Soft IME thrashing (TextTraitLayout requestFocus → showSoftInput) prevents
        // ActivityScenario idle sync and hangs Collect instrumented proofs.
        shell.executeShellCommand("settings put secure show_ime_with_hard_keyboard 0")
        shell.executeShellCommand(
            "ime disable com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME",
        )
        // Location is optional for flush geo tags; missing runtime grant prints SecurityException.
        shell.executeShellCommand(
            "pm grant com.fieldbook.tracker.debug android.permission.ACCESS_FINE_LOCATION",
        )
        shell.executeShellCommand(
            "pm grant com.fieldbook.tracker.debug android.permission.ACCESS_COARSE_LOCATION",
        )
        shell.executeShellCommand("wm dismiss-keyguard")
        shell.executeShellCommand("input keyevent 82")
        device.pressMenu()
    }

    fun ensureWindowFocus() {
        prepareHeadlessEmulator()
    }
}
