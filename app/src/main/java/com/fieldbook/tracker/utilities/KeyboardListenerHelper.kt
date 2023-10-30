package com.fieldbook.tracker.utilities

import android.graphics.Rect
import android.view.View
import javax.inject.Inject

/**
 * Used to detect when softkeyboard is visible
 *
 * connect should be called on the container view of a fragment/activity,
 * the function parameter will return the height of the keypad, and if it is displayed or not
 */

class KeyboardListenerHelper @Inject constructor() {

    private var isKeyboardShowing = false
    fun connect(view: View, function: (Boolean, Int) -> Unit) {

        // ContentView is the root view of the layout of this activity/fragment
        view.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            view.getWindowVisibleDisplayFrame(r)
            val screenHeight = view.rootView.height

            // r.bottom is the position above soft keypad or device button.
            // if keypad is shown, the r.bottom is smaller than that before.
            val keypadHeight = screenHeight - r.bottom

            if (keypadHeight > screenHeight * 0.15) { // 0.15 ratio is perhaps enough to determine keypad height.
                // keyboard is opened
                if (!isKeyboardShowing) {
                    isKeyboardShowing = true
                    function(true, keypadHeight - r.top)
                }
            }
            else {
                // keyboard is closed
                if (isKeyboardShowing) {
                    isKeyboardShowing = false
                    function(false, keypadHeight - r.top)
                }
            }
        }
    }
}