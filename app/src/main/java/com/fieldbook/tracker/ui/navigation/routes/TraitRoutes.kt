package com.fieldbook.tracker.ui.navigation.routes

import kotlinx.serialization.Serializable

@Serializable
object TraitGraph

@Serializable
object TraitEditor

@Serializable
data class TraitDetail(val traitId: String)
