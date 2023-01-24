package com.fieldbook.tracker.interfaces

import android.text.TextWatcher
import android.widget.EditText
import com.fieldbook.tracker.traits.LayoutCollections

interface CollectTraitController: CollectController {
    fun validateData(): Boolean
    fun getEtCurVal(): EditText
    fun setEtCurVal(editText: EditText)
    fun getTraitLayouts(): LayoutCollections
    fun getCvText(): TextWatcher
    fun isCyclingTraitsAdvances(): Boolean
    fun refreshLock()
}