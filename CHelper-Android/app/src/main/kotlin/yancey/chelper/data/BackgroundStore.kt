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

package yancey.chelper.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import yancey.chelper.android.util.MonitorUtil

class BackgroundStore(private val file: File, private val maxEdge: Int = 2048) {
    private val state = BackgroundRequestState<ImageBitmap?>(null)
    val backgroundBitmapFlow = state.values
    val backgroundBitmap
        get() = state.value

    private val atomicFile = AtomicFile(file)
    private val requests = Mutex()
    private val writes = Mutex()

    private suspend fun operation(action: suspend () -> Unit) {
        operations.async { writes.withLock { action() } }.await()
    }

    // A selected background is an application preference: finishing an Activity must not abort its
    // commit.
    private val operations = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    suspend fun load() {
        val revision = requests.withLock { state.beginLoad() } ?: return
        operation {
            try {
                val first = atomicFile.openReadOrNull() ?: return@operation
                val bitmap = decode(first) { atomicFile.openRead() }
                if (!requests.withLock { state.publish(revision, bitmap.asImageBitmap()) })
                    bitmap.recycle()
            } catch (e: IOException) {
                Log.w(TAG, "Unable to load background", e)
                MonitorUtil.generateCustomLog(e, "BackgroundReadException")
            }
        }
    }

    suspend fun replace(resolver: ContentResolver, uri: Uri) {
        val revision = requests.withLock { state.begin() }
        operation {
            if (!state.isCurrent(revision)) return@operation
            val bitmap = decode { resolver.openInputStream(uri) }
            var published = false
            try {
                if (!state.isCurrent(revision)) return@operation
                file.parentFile?.mkdirs()
                val output = atomicFile.startWrite()
                try {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                        throw IOException("无法保存图片")
                    // Request creation and the disk/UI commit share a lock, without blocking the UI
                    // thread.
                    requests.withLock {
                        if (!state.isCurrent(revision)) {
                            atomicFile.failWrite(output)
                            return@operation
                        }
                        atomicFile.finishWrite(output)
                        published = state.publish(revision, bitmap.asImageBitmap())
                    }
                } catch (e: Exception) {
                    atomicFile.failWrite(output)
                    throw e
                }
            } finally {
                // Published bitmaps remain owned by Compose; obsolete decoded images can be
                // released now.
                if (!published) bitmap.recycle()
            }
        }
    }

    suspend fun restore() {
        val revision = requests.withLock { state.begin() }
        operation {
            requests.withLock {
                if (!state.isCurrent(revision)) return@operation
                atomicFile.delete()
                if (file.exists()) throw IOException("无法删除背景图片")
                state.publish(revision, null)
            }
        }
    }

    internal fun decode(open: () -> InputStream?): Bitmap =
        decode(open() ?: throw IOException("无法打开图片"), open)

    private fun decode(first: InputStream, open: () -> InputStream?): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        first.buffered().use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("无法解析图片")
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = backgroundSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        return (open() ?: throw IOException("无法打开图片")).buffered().use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("无法解析图片")
    }

    companion object {
        private const val TAG = "BackgroundStore"
        lateinit var INSTANCE: BackgroundStore
            private set

        fun init(file: File, maxEdge: Int = 2048) {
            INSTANCE = BackgroundStore(file.resolve("background.png"), maxEdge)
        }
    }
}
