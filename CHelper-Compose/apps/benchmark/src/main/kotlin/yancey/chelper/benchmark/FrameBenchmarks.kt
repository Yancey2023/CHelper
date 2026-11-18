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

package yancey.chelper.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrameBenchmarks {
    @get:Rule val benchmark = MacrobenchmarkRule()
    private val interactionCompilation =
        if (
            InstrumentationRegistry.getArguments().getString("chelper.requireBaselineProfile") ==
                "true"
        ) {
            CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require)
        } else {
            CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Disable,
                warmupIterations = 3,
            )
        }

    @Test
    fun settingsAndScroll() =
        benchmark.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = interactionCompilation,
            iterations = 5,
            setupBlock = { home() },
            measureBlock = { settings() },
        )

    @Test
    fun commandInput() =
        benchmark.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = interactionCompilation,
            iterations = 5,
            setupBlock = { home() },
            measureBlock = { command() },
        )

    @Test
    fun longCommandScroll() =
        benchmark.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = interactionCompilation,
            iterations = 5,
            setupBlock = { prepareLongCommand() },
            measureBlock = { scrollLongCommand() },
        )

    @Test
    fun libraryRevealAndScroll() =
        benchmark.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = interactionCompilation,
            iterations = 5,
            setupBlock = { home() },
            measureBlock = { library() },
        )
}

/**
 * Cold launches use system tracing; the target may be stopped before SDK tracing can acknowledge.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmarks {
    @get:Rule val benchmark = MacrobenchmarkRule()

    @Test
    fun coldStartupWithoutProfile() =
        benchmark.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                home()
                pressHome()
            },
            measureBlock = { home() },
        )
}

/** Run only after generateBaselineProfile and rebuilding the target APK. */
@RunWith(AndroidJUnit4::class)
class PackagedProfileBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()

    @Test
    fun coldStartupWithPackagedProfile() =
        benchmark.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode =
                CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                home()
                pressHome()
            },
            measureBlock = { home() },
        )
}
