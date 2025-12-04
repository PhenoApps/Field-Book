package com.fieldbook.tracker.ui.navigation.controllers

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.fieldbook.tracker.ui.navigation.routes.TraitDetail

class TraitNavController(
    navController: NavHostController,
) : BaseNavController(navController) {

    fun navigateToTraitDetail(traitId: String, from: NavBackStackEntry) {
        navigateIfResumed(from, TraitDetail(traitId))
    }

    fun navigateBack() = back()
}