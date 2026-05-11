package com.fieldbook.tracker.preferences.enums

sealed class BarcodeScanningOptions(val value: String) {
    object Move: BarcodeScanningOptions("0")
    object EnterValue: BarcodeScanningOptions("1")
    object EnterIfNotId: BarcodeScanningOptions("2")
    object Ask: BarcodeScanningOptions("3")
}