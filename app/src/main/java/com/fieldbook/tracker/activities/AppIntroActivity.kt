package com.fieldbook.tracker.activities

import android.Manifest
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.utilities.Constants
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment

class AppIntroActivity : AppIntro() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make sure you don't call setContentView!

        // Call addSlide passing your Fragments.
        // You can use AppIntroFragment to use a pre-built fragment

        addSlide(AppIntroFragment.createInstance(
            "Welcome!",
            "This is a demo of the Field Book app",
            backgroundColorRes = R.color.main_primary))

        addSlide(AppIntroFragment.createInstance(
            "Permission Request",
            "In order to access your camera, you must give permissions.",
            imageDrawable = R.drawable.ic_experimental,
            backgroundColorRes = R.color.main_primary))

        // Here we ask for camera permission on slide 2
        askForPermissions(
            permissions = Constants.permissions,
            slideNumber = 2,
            required = true
        )

    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        // Decide what to do when the user clicks on "Skip"
        finish()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        // Decide what to do when the user clicks on "Done"
        finish()
    }
}