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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
    // Starting predictive back also moves settled entries to STARTED. Keep the last RESUMED
    // identity so that only a genuine entry interruption selects the recovery spring.
    val settledEntry = remember(navController) { mutableStateOf<String?>(null) }
    entry?.let { current ->
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME, lifecycleOwner = current) {
            settledEntry.value = current.id
        }
    }
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
        enterTransition = { pageEnter(navController, settledEntry, isPop = false) },
        exitTransition = { pageExit(navController, settledEntry, isPop = false) },
        popEnterTransition = { pageEnter(navController, settledEntry, isPop = true) },
        popExitTransition = { pageExit(navController, settledEntry, isPop = true) },
        // Navigation 2.10 has independent predictive defaults. Keep seeking and settling on
        // the same reverse path instead of switching between its defaults and our page slides.
        predictivePopEnterTransition = { _ ->
            pageEnter(navController, settledEntry, isPop = true)
        },
        predictivePopExitTransition = { _ -> pageExit(navController, settledEntry, isPop = true) },
        builder = builder,
    )
}

private val pageAnimation = tween<IntOffset>(durationMillis = 300)

// Compose uses this spring for PostExit -> Visible, regardless of the supplied enter spec.
// Match it on the other page when reversing an unfinished push to keep both slides together.
private val interruptedReturnAnimation =
    spring<IntOffset>(
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isReturning(
    navController: NavHostController,
    isPop: Boolean,
): Boolean {
    val stack = navController.navigatorProvider[ComposeNavigator::class].backStack.value
    val initialIndex = stack.indexOf(initialState)
    val targetIndex = stack.indexOf(targetState)
    return if (initialIndex >= 0 && targetIndex >= 0) targetIndex < initialIndex else isPop
}

// An interrupted enter can reuse forward callbacks for a reverse segment.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pageEnter(
    navController: NavHostController,
    settledEntry: State<String?>,
    isPop: Boolean,
): EnterTransition {
    val returning = isReturning(navController, isPop)
    val direction = if (returning) -1 else 1
    val interrupted = returning && initialState.id != settledEntry.value
    return slideInHorizontally(if (interrupted) interruptedReturnAnimation else pageAnimation) {
        it * direction
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pageExit(
    navController: NavHostController,
    settledEntry: State<String?>,
    isPop: Boolean,
): ExitTransition {
    val returning = isReturning(navController, isPop)
    val direction = if (returning) 1 else -1
    val interrupted = returning && initialState.id != settledEntry.value
    return slideOutHorizontally(if (interrupted) interruptedReturnAnimation else pageAnimation) {
        it * direction
    }
}
