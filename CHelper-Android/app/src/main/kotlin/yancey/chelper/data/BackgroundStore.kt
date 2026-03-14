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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import yancey.chelper.android.util.MonitorUtil
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class BackgroundStore(private val file: File) {
    private val lock = ReentrantLock()
    private val _backgroundBitmap = MutableStateFlow<ImageBitmap?>(null)
    val backgroundBitmapFlow: StateFlow<ImageBitmap?> = _backgroundBitmap.asStateFlow()

    init {
        if (file.exists()) {
            try {
                BufferedInputStream(FileInputStream(file)).use { inputStream ->
                    setBitmap(BitmapFactory.decodeStream(inputStream))
                }
            } catch (e: IOException) {
                Log.w(TAG, "fail to load background drawable", e)
                MonitorUtil.generateCustomLog(e, "IOException")
            }
        }
    }

    fun setBitmap(backgroundBitmap: Bitmap?) {
        lock.withLock {
            if (this._backgroundBitmap == backgroundBitmap) {
                return
            }
            if (backgroundBitmap == null || backgroundBitmap.getWidth() == 0 || backgroundBitmap.getHeight() == 0) {
                this._backgroundBitmap.value = null
            } else {
                this._backgroundBitmap.value = backgroundBitmap.asImageBitmap()
            }
        }
    }

    @Throws(IOException::class)
    fun setBackGroundDrawable(bitmap: Bitmap?) {
        setBitmap(bitmap)
        if (bitmap == null) {
            if (file.exists() && !file.delete()) {
                throw IOException("fail to delete file")
            }
            return
        }
        val parentFile = file.getParentFile()
        if (parentFile == null || (!parentFile.exists() && !parentFile.mkdirs())) {
            throw IOException("fail to create parent directory")
        }
        BufferedOutputStream(FileOutputStream(file)).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
    }

    companion object {
        private const val TAG = "CustomTheme"

        var INSTANCE: BackgroundStore? = null

        fun init(file: File?) {
            INSTANCE = BackgroundStore(File(file, "background.png"))
        }
    }
}