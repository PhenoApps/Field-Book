package com.fieldbook.tracker.interfaces

import android.text.TextWatcher
import com.fieldbook.tracker.traits.LayoutCollections

interface CollectTraitController: CollectController {
    fun validateData(): Boolean
    fun getTraitLayouts(): LayoutCollections
    fun getCvText(): TextWatcher
    fun isCyclingTraitsAdvances(): Boolean
    fun refreshLock()
}