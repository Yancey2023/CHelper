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

package yancey.chelper.android.performance

import android.view.View
import androidx.metrics.performance.PerformanceMetricsState
import yancey.chelper.ui.platform.PerformanceStateSink

internal val performanceStateKeys =
    setOf("screen", "library_phase", "library_highlight", "command_analysis", "interaction")

/** Scoped state registration: outgoing screens must not erase an incoming screen's state. */
class WindowPerformanceState(view: View) : PerformanceStateSink {
    private val holder = PerformanceMetricsState.getHolderForHierarchy(view)
    private val entries = linkedMapOf<String, LinkedHashMap<Any, String>>()

    override fun enter(key: String, value: String): () -> Unit {
        require(key in performanceStateKeys)
        require(value.length <= 64 && value.none { it == '/' || it == '\n' })
        val token = Any()
        entries.getOrPut(key) { linkedMapOf() }[token] = value
        publish(key)
        return {
            entries[key]?.remove(token)
            publish(key)
        }
    }

    fun refresh() {
        entries.keys.forEach(::publish)
    }

    private fun publish(key: String) {
        val value = entries[key]?.values?.lastOrNull()
        if (value == null) holder.state?.removeState(key) else holder.state?.putState(key, value)
    }
}
