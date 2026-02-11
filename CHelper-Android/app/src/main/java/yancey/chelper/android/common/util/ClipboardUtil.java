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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * 剪切板相关的工具类
 */
public class ClipboardUtil {

    private static final String TAG = "ClipboardUtil";

    /**
     * 不允许创建实例
     */
    private ClipboardUtil() {

    }

    /**
     * 写入剪切板
     *
     * @param context      上下文
     * @param charSequence 文本
     * @return 是否成功
     */
    public static boolean setText(Context context, CharSequence charSequence) {
        try {
            ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager == null) {
                return false;
            }
            clipboardManager.setPrimaryClip(ClipData.newPlainText(null, charSequence));
            return true;
        } catch (Throwable throwable) {
            Log.e(TAG, "fail to set text in clipboard service", throwable);
            MonitorUtil.generateCustomLog(throwable, "ClipboardException");
            return false;
        }
    }

    /**
     * 读取剪切板
     *
     * @param context 上下文
     * @return 读取的文本
     */
    public static @Nullable CharSequence getText(Context context) {
        try {
            ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
                return null;
            }
            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return null;
            }
            return clip.getItemAt(0).coerceToText(context);
        } catch (Throwable e) {
            Log.e(TAG, "fail to get text in clipboard service", e);
            MonitorUtil.generateCustomLog(e, "ClipboardException");
            return null;
        }
    }

}
