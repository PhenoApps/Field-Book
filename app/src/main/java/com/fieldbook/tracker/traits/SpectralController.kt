package com.fieldbook.tracker.traits

import com.fieldbook.tracker.database.viewmodels.SpectralViewModel

interface SpectralController {
    fun getSpectralViewModel(): SpectralViewModel
}