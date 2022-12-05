package com.fieldbook.tracker.adapters

import android.content.Context

interface TraitEditorLoader {
    fun queryAndLoadTraits()
    fun displayBrapiInfo(context: Context, traitName: String?, noCheckTrait: Boolean): Boolean
}