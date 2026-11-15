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

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
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
        enterTransition = { slideInHorizontally(pageAnimation) { it } },
        exitTransition = { slideOutHorizontally(pageAnimation) { -it } },
        popEnterTransition = { returnEnter() },
        popExitTransition = { returnExit() },
        // Navigation 2.10 has independent predictive defaults. Keep seeking and settling on
        // the same reverse path instead of switching between its defaults and our page slides.
        predictivePopEnterTransition = { _ -> returnEnter() },
        predictivePopExitTransition = { _ -> returnExit() },
        builder = builder,
    )
}

private val pageAnimation = tween<IntOffset>(durationMillis = 300)

private fun returnEnter() = slideInHorizontally(pageAnimation) { -it }

private fun returnExit() = slideOutHorizontally(pageAnimation) { it }
