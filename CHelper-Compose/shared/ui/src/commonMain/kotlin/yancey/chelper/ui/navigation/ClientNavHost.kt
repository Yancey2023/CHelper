/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.get
import yancey.chelper.ui.platform.PerformanceState

/** One motion path for forward, button-back and predictive-back navigation on both hosts. */
@Composable
fun ClientNavHost(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
    builder: NavGraphBuilder.() -> Unit,
) {
    val entry by navController.currentBackStackEntryAsState()
    val screen =
        entry
            ?.destination
            ?.route
            ?.substringBefore('/')
            ?.substringBefore('?')
            ?.substringAfterLast('.') ?: "starting"
    PerformanceState("screen", screen.take(64))
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        // Moving only the foreground avoids independent slide clocks diverging on interruption.
        enterTransition = { pageEnter(navController, isPop = false) },
        exitTransition = { pageExit(navController, isPop = false) },
        popEnterTransition = { pageEnter(navController, isPop = true) },
        popExitTransition = { pageExit(navController, isPop = true) },
        // Navigation 2.10 has independent predictive defaults. Keep seeking and settling on
        // the same reverse path instead of switching between its defaults and our page slides.
        predictivePopEnterTransition = { _ -> pageEnter(navController, isPop = true) },
        predictivePopExitTransition = { _ -> pageExit(navController, isPop = true) },
        builder = builder,
    )
}

private val pageAnimation = tween<IntOffset>(durationMillis = 300)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isReturning(
    navController: NavHostController,
    isPop: Boolean,
): Boolean {
    val stack = navController.navigatorProvider[ComposeNavigator::class].backStack.value
    val initialIndex = stack.indexOf(initialState)
    val targetIndex = stack.indexOf(targetState)
    return if (initialIndex >= 0 && targetIndex >= 0) targetIndex < initialIndex else isPop
}

// An interrupted enter can reuse forward callbacks for a reverse segment. Read its direction
// when offsets are resolved, including transitions retained for the page underneath.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pageEnter(
    navController: NavHostController,
    isPop: Boolean,
) = slideInHorizontally(pageAnimation) { if (isReturning(navController, isPop)) 0 else it }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pageExit(
    navController: NavHostController,
    isPop: Boolean,
) =
    slideOutHorizontally(pageAnimation) { if (isReturning(navController, isPop)) it else 0 } +
        // Keep the stationary background composed until the entering page covers it completely.
        ExitTransition.KeepUntilTransitionsFinished
