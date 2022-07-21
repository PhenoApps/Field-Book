package com.fieldbook.tracker.fragments

import org.phenoapps.fragments.tts.LocaleListFragment
import java.util.*

class FieldBookLocaleListFragment: LocaleListFragment() {

    override fun onItemClicked(model: Locale) {
        super.onItemClicked(model)
        activity?.finish()
    }
}