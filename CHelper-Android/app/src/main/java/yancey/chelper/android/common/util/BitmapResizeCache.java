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

package yancey.chelper.android.common.util;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import androidx.annotation.Nullable;
import androidx.compose.ui.graphics.AndroidImageBitmap;
import androidx.compose.ui.graphics.ImageBitmap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class BitmapResizeCache {

    private final ReentrantLock lock = new ReentrantLock();
    private final List<Bitmap> cache = new ArrayList<>();
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
            cache.clear();
            updateTimes++;
        } finally {
            lock.unlock();
        }
    }

    public boolean isSourceFileMiss() {
        return sourceBitmap == null;
    }

    public int getWidth() {
        return sourceBitmap == null ? 0 : sourceBitmap.getWidth();
    }

    public int getHeight() {
        return sourceBitmap == null ? 0 : sourceBitmap.getHeight();
    }

    public int getUpdateTimes() {
        return updateTimes;
    }

    public @Nullable Bitmap getBitmap(int targetWidth, int targetHeight) {
        lock.lock();
        try {
            if (sourceBitmap == null) {
                return null;
            }
            for (int i = cache.size() - 1; i >= 0; i--) {
                Bitmap bitmap = cache.get(i);
                if (bitmap.getWidth() == targetWidth && bitmap.getHeight() == targetHeight) {
                    cache.remove(i);
                    cache.add(bitmap);
                    return bitmap;
                }
            }
            int sourceWidth = sourceBitmap.getWidth();
            int sourceHeight = sourceBitmap.getHeight();
            float scaleX = (float) targetWidth / sourceWidth;
            float scaleY = (float) targetHeight / sourceHeight;
            float scale = Math.max(scaleX, scaleY);
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            float skipX = sourceWidth - targetWidth / scale;
            float skipY = sourceHeight - targetHeight / scale;
            Bitmap resultBitmap = Bitmap.createBitmap(
                    sourceBitmap,
                    (int) (skipX / 2),
                    (int) (skipY / 2),
                    (int) (sourceWidth - skipX),
                    (int) (sourceHeight - skipY),
                    matrix,
                    false
            );
            if (cache.size() > 4) {
                cache.remove(0);
            }
            cache.add(resultBitmap);
            return resultBitmap;
        } finally {
            lock.unlock();
        }
    }

    public @Nullable ImageBitmap getImageBitmap() {
        return sourceBitmap == null ? null : new AndroidImageBitmap(sourceBitmap);
    }

}
