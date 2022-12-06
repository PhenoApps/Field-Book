package com.fieldbook.tracker.views

import android.content.SharedPreferences
import com.fieldbook.tracker.database.DataHelper

interface CollectRangeController {
    fun moveToSearch(command: String,
                     plotIndices: IntArray?,
                     rangeId: String?,
                     plotId: String?,
                     data: String?,
                     traitIndex: Int): Boolean
    fun repeatUpdate()
    fun validateData(): Boolean
    fun getTraitBox(): TraitBoxView
    fun getDatabase(): DataHelper
    fun initWidgets(rangeSuppress: Boolean)
    fun cancelAndFinish()
    fun callFinish()
    fun getPreferences(): SharedPreferences
    fun playSound(sound: String)
    fun resetGeoNavMessages()
    fun refreshMain()
    fun getNonExistingTraits(plotIndex: Int): List<Int>
    fun existsAllTraits(traitIndex: Int, plotIndex: Int): Int
    fun existsTrait(plotIndex: Int): Boolean
}