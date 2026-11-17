package com.fieldbook.tracker.devices.spectrometers.innospectra.models

import android.os.Parcelable
import com.fieldbook.tracker.devices.spectrometers.innospectra.models.Section
import kotlinx.parcelize.Parcelize

@Parcelize
class Config(
    val name: String,
    val repeats: Int,
    var currentIndex: Int = 0,
    var sections: Array<Section>? = null): Parcelable
