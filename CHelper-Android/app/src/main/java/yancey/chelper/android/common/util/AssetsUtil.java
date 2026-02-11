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

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 软件内置资源管理相关的工具类
 */
public class AssetsUtil {

    /**
     * 不允许创建实例
     */
    private AssetsUtil() {

    }

    /**
     * 读取内置资源中的文本
     *
     * @param context 上下文
     * @param path    资源路径
     * @return 读取的文本
     */
    public static String readStringFromAssets(Context context, String path) {
        try (BufferedInputStream bis = new BufferedInputStream(context.getAssets().open(path))) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            for (int count; (count = bis.read(buffer)) != -1; ) {
                os.write(buffer, 0, count);
            }
            return os.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
