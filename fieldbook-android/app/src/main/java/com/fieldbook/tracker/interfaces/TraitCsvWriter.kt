package com.fieldbook.tracker.interfaces

import android.content.Context
import android.net.Uri

interface TraitCsvWriter {
    fun writeAllTraitsToCsv(traitFileName: String, context: Context): Uri?
}