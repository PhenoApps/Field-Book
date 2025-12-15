package com.fieldbook.tracker.preferences.enums

sealed class BarcodeScanningOptions(val value: String) {
    object EnterValue: BarcodeScanningOptions("0")
    object Move: BarcodeScanningOptions("1")
    object Ask: BarcodeScanningOptions("2")
    object EnterIfNotId: BarcodeScanningOptions("3")
}