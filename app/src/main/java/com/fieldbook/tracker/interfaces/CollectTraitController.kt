package com.fieldbook.tracker.interfaces

import com.fieldbook.tracker.traits.BaseTraitLayout
import com.fieldbook.tracker.traits.LayoutCollections
import com.fieldbook.tracker.views.CollectInputView

interface CollectTraitController: CollectController {
    fun validateData(): Boolean
    fun getTraitLayouts(): LayoutCollections
    fun isCyclingTraitsAdvances(): Boolean
    fun refreshLock()
    fun inflateTrait(layout: BaseTraitLayout)
    fun getCollectInputView(): CollectInputView
}