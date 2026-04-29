package com.fieldbook.tracker.database.models.spectral

data class Protocol(
    val id: Int,
    val externalId: String,
    val title: String,
    val description: String,
    val waveStart: Float,
    val waveEnd: Float,
    val waveStep: Float
)