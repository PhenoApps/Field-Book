package com.fieldbook.tracker.vuzix

import android.content.Context
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.InfoBarAdapter
import com.fieldbook.tracker.objects.TraitObject

interface VuzixController {

    fun getContext(): Context
    fun getUniqueName(): String
    fun getPlotId(): String
    fun getCurrentTrait(): TraitObject
    fun getEditTextCurrentValue(): String
    fun getPrefixData(): Array<String>
    fun getStudyId(): String

    fun moveTrait(dir: String)
    fun movePlot(dir: String)
    fun selectTrait(index: Int)
    fun setEditTextCurrentValue(value: String)
}