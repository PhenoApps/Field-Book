package com.fieldbook.tracker.preferences.enums

sealed class TransferSource(val value: String) {
    object LOCAL : TransferSource("local")
    object BRAPI : TransferSource("brapi")
    object CLOUD : TransferSource("cloud")
    object ASK : TransferSource("ask")

    companion object {
        @JvmField
        val DEFAULT: TransferSource = ASK

        fun fromValue(value: String?): TransferSource {
            return when (value) {
                "local" -> LOCAL
                "brapi" -> BRAPI
                "cloud" -> CLOUD
                "ask" -> ASK
                else -> DEFAULT
            }
        }
    }
}