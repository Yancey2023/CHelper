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

import android.text.Editable;
import android.text.TextWatcher;

import java.util.function.Consumer;

/**
 * 对TextWatcher的包装，文本改变事件的监听
 */
public class TextWatcherUtil {

    /**
     * 不允许创建实例
     */
    private TextWatcherUtil() {

    }

    /**
     * 文字改变的事件
     *
     * @param consumer 要执行的事件
     * @return 文字改变的事件
     */
    public static TextWatcher onTextChanged(Consumer<CharSequence> consumer) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                consumer.accept(s);
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        };
    }

    /**
     * 文字改变之后的事件
     *
     * @param consumer 要执行的事件
     * @return 文字改变之后的事件
     */
    public static TextWatcher afterTextChanged(Consumer<Editable> consumer) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                consumer.accept(s);
            }
        };
    }
}
