package com.fieldbook.tracker.ui.navigation.controllers

import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.poi.ss.formula.functions.T

/**
 * Base class for all activity OR graph OR feature-level navigation controllers.
 *
 * Each graph should extend this base class to specify explicit navigation methods
 *
 * Provides common navigation behaviors such as:
 * - avoid duplicate navigation caused by recomposition
 * - type-safe routes
 * - consistent NavOptions default behavior (launchSingleTop, restoreState)
 *  and support for custom NavOptions using the NavOptionsBuilder
 */
open class BaseNavController(
    protected val navController: NavHostController
) {

    /**
     * Performs navigation only if the originating backStackEntry is currently in
     * the RESUMED state
     *
     * This prevents multiple navigations from firing due to recomposition
     *
     * the [builder] allows having custom navigation options
     * example usage:
     *  navigateIfResumed(from, TraitDetail(id)) {
     *      popUpTo(navController.graph.startDestinationId) {
     *          inclusive = false
     *      }
     *  }
     */
    @MainThread
    internal inline fun <reified T : Any> navigateIfResumed(
        from: NavBackStackEntry,
        destination: T,
        noinline builder: (NavOptionsBuilder.() -> Unit)? = null
    ) {
        if (from.lifecycleIsResumed()) {
            navController.navigate(destination) {
                launchSingleTop = true // have only one copy per route in the stack
                restoreState = true
                builder?.invoke(this)
            }
        }
    }

    fun back() = navController.navigateUp()

    /**
     * Pops back to the start destination of the graph
     */
    fun backToRoot() {
        navController.popBackStack(
            navController.graph.startDestinationId,
            inclusive = false
        )
    }
}

/**
 * If the lifecycle is not resumed it means this NavBackStackEntry already processed a nav event
 *
 * This is used to de-duplicate navigation events
 */
internal fun NavBackStackEntry.lifecycleIsResumed() = this.lifecycle.currentState == Lifecycle.State.RESUMED