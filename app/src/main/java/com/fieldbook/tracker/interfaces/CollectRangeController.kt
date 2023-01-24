package com.fieldbook.tracker.interfaces

interface CollectRangeController: CollectController {
    fun validateData(): Boolean
    fun initWidgets(rangeSuppress: Boolean)
    fun cancelAndFinish()
    fun callFinish()
    fun refreshMain()
    fun getNonExistingTraits(plotIndex: Int): List<Int>
    fun existsAllTraits(traitIndex: Int, plotIndex: Int): Int
    fun existsTrait(plotIndex: Int): Boolean
}