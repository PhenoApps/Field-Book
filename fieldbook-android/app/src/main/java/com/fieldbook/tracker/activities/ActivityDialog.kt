package com.fieldbook.tracker.activities

import android.os.Bundle
import com.fieldbook.tracker.R

open class ActivityDialog : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawableResource(R.drawable.inset_border)
    }
}