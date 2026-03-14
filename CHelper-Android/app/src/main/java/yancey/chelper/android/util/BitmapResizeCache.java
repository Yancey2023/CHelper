/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.android.util;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;
import androidx.compose.ui.graphics.AndroidImageBitmap;
import androidx.compose.ui.graphics.ImageBitmap;

import java.util.concurrent.locks.ReentrantLock;

public class BitmapResizeCache {

    private final ReentrantLock lock = new ReentrantLock();
    private @Nullable Bitmap sourceBitmap;
    private int updateTimes = 0;

    public BitmapResizeCache() {
    }

    public void setBitmap(@Nullable Bitmap sourceBitmap) {
        lock.lock();
        try {
            if (this.sourceBitmap == sourceBitmap) {
                return;
            }
            if (sourceBitmap == null || sourceBitmap.getWidth() == 0 || sourceBitmap.getHeight() == 0) {
                this.sourceBitmap = null;
            } else {
                this.sourceBitmap = sourceBitmap;
            }
            updateTimes++;
        } finally {
            lock.unlock();
        }
    }

    public int getUpdateTimes() {
        return updateTimes;
    }

    public @Nullable ImageBitmap getImageBitmap() {
        return sourceBitmap == null ? null : new AndroidImageBitmap(sourceBitmap);
    }

}
