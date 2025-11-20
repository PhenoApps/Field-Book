package com.fieldbook.tracker.interfaces

import com.fieldbook.tracker.traits.BaseTraitLayout
import com.fieldbook.tracker.traits.LayoutCollections
import com.fieldbook.tracker.views.CollectInputView

interface CollectTraitController: CollectController {
    fun validateData(data: String?): Boolean
    fun navigateIfDataIsValid(data: String?, onValidNavigation: () -> Unit)
    fun getTraitLayouts(): LayoutCollections
    fun isCyclingTraitsAdvances(): Boolean
    fun refreshLock()
    fun inflateTrait(layout: BaseTraitLayout)
    fun getCollectInputView(): CollectInputView
}