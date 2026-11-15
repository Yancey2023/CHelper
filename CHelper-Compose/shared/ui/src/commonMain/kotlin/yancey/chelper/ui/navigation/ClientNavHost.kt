package yancey.chelper.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost

/** One motion path for forward, button-back and predictive-back navigation on both hosts. */
@Composable
fun ClientNavHost(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
    builder: NavGraphBuilder.() -> Unit,
) {
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
