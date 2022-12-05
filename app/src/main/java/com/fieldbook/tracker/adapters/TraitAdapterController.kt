package com.fieldbook.tracker.adapters

import android.content.Context
import com.fieldbook.tracker.database.DataHelper

interface TraitAdapterController {
    fun queryAndLoadTraits()
    fun displayBrapiInfo(context: Context, traitName: String?, noCheckTrait: Boolean): Boolean
    fun getDatabase(): DataHelper
}