package com.fieldbook.tracker.views

import android.content.SharedPreferences
import android.text.TextWatcher
import android.widget.EditText
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.traits.LayoutCollections

interface CollectTraitController {
    fun getDatabase(): DataHelper
    fun getRangeBox(): RangeBoxView
    fun getPreferences(): SharedPreferences
    fun validateData(): Boolean
    fun playSound(sound: String)
    fun getEtCurVal(): EditText
    fun setEtCurVal(editText: EditText)
    fun getTraitLayouts(): LayoutCollections
    fun getCvText(): TextWatcher
    fun isCyclingTraitsAdvances(): Boolean
    fun refreshLock()
}