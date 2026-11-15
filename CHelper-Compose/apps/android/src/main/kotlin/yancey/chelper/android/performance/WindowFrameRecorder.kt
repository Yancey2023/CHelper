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

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import android.view.Window
import androidx.metrics.performance.FrameDataApi24
import androidx.metrics.performance.FrameDataApi31
import androidx.metrics.performance.JankStats
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import yancey.chelper.BuildConfig

@Serializable
private data class FrameSample(
    val type: String = "frame",
    val startNs: Long,
    val uiNs: Long,
    val cpuNs: Long?,
    val overrunNs: Long?,
    val jank: Boolean,
    val states: Map<String, String>,
)

@Serializable
private data class CaptureMetadata(
    val type: String = "session",
    val schema: Int = 1,
    val model: String = Build.MODEL,
    val sdk: Int = Build.VERSION.SDK_INT,
    val variant: String = BuildConfig.BUILD_TYPE,
    val version: String = BuildConfig.VERSION_NAME,
    val refreshHz: Float,
    val window: String,
)

@Serializable
private data class CaptureSummary(
    val type: String = "summary",
    val observed: Long,
    val lost: Long,
    val written: Int,
    val truncated: Boolean,
)

/** No disk IO, JSON encoding, uploads or unbounded queues in the frame callback. */
class WindowFrameRecorder(context: Context, window: Window, name: String) : AutoCloseable {
    private val active = AtomicBoolean(true)
    private val observed = AtomicLong()
    private val lost = AtomicLong()
    private val frames = Channel<FrameSample>(4096)
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val tracker: JankStats
    private val json = Json { encodeDefaults = true }

    init {
        val application = context.applicationContext
        val filename = "frames-${System.currentTimeMillis()}-${Process.myPid()}-$name.jsonl"
        val metadata =
            CaptureMetadata(refreshHz = window.decorView.display?.refreshRate ?: 60f, window = name)
        writer.launch {
            try {
                val directory =
                    File(
                        application.getExternalFilesDir(null) ?: application.filesDir,
                        "performance",
                    )
                val file = File(directory, filename)
                directory.mkdirs()
                // Bound retained sessions as well as the in-memory queue. Never touch other files.
                directory
                    .listFiles { entry ->
                        entry.name.startsWith("frames-") && entry.extension == "jsonl"
                    }
                    ?.sortedByDescending(File::lastModified)
                    ?.drop(19)
                    ?.forEach(File::delete)
                file.bufferedWriter().use { output ->
                    output.appendLine(json.encodeToString(metadata))
                    output.flush()
                    var count = 0
                    for (frame in frames) {
                        output.appendLine(json.encodeToString(frame))
                        if (++count % 30 == 0 || frame.jank) output.flush()
                        if (count >= 100_000) break
                    }
                    output.appendLine(
                        json.encodeToString(
                            CaptureSummary(
                                observed = observed.get(),
                                lost = lost.get(),
                                written = count,
                                truncated = count >= 100_000,
                            )
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("FrameRecorder", "Performance capture could not be written", error)
            } finally {
                active.set(false)
                frames.close()
                writer.cancel()
            }
        }
        tracker =
            JankStats.createAndTrack(window) { data ->
                if (active.get()) {
                    observed.incrementAndGet()
                    val copy =
                        FrameSample(
                            startNs = data.frameStartNanos,
                            uiNs = data.frameDurationUiNanos,
                            cpuNs = (data as? FrameDataApi24)?.frameDurationCpuNanos,
                            overrunNs =
                                if (Build.VERSION.SDK_INT >= 31)
                                    (data as? FrameDataApi31)?.frameOverrunNanos
                                else null,
                            jank = data.isJank,
                            states =
                                data.states
                                    .filter { it.key in performanceStateKeys }
                                    .associate { it.key to it.value },
                        )
                    if (frames.trySend(copy).isFailure) lost.incrementAndGet()
                }
            }
    }

    override fun close() {
        active.set(false)
        tracker.isTrackingEnabled = false
        frames.close()
    }
}
