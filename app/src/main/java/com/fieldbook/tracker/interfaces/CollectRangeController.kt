package com.fieldbook.tracker.interfaces

import com.fieldbook.tracker.views.CollectInputView

interface CollectRangeController: CollectController {
    fun validateData(data: String?): Boolean
    fun navigateIfDataIsValid(data: String?, onValidNavigation: () -> Unit)
    fun initWidgets(rangeSuppress: Boolean)
    fun cancelAndFinish()
    fun callFinish()
    fun refreshMain()
    fun isReturnFirstTrait(): Boolean
    fun getNonExistingTraits(plotIndex: Int): List<Int>
    fun existsAllTraits(traitIndex: Int, plotIndex: Int): Int
    fun existsTrait(plotIndex: Int): Boolean
    fun getCollectInputView(): CollectInputView
    fun askUserSendCrashReport(e: Exception)
}