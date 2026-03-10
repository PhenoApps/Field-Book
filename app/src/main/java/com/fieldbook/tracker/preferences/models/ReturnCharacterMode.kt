package com.fieldbook.tracker.preferences.models

sealed class ReturnCharacterMode(val mode: String) {
    object DoNothing : ReturnCharacterMode("0")
    object NextPlot : ReturnCharacterMode("1")
    object NextTrait : ReturnCharacterMode("2")
}