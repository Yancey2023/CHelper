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

package yancey.chelper.core;

import org.jetbrains.annotations.NotNull;

/**
 * 用于存储被选择着的文本
 */
public class SelectedString {
    // 文本
    @NotNull
    public final String text;
    // 光标开始位置
    public final int selectionStart;
    // 光标结束位置
    public final int selectionEnd;

    public SelectedString(@NotNull String text, int position) {
        this.text = text;
        this.selectionStart = position;
        this.selectionEnd = position;
    }

    public SelectedString(@NotNull String text, int selectionStart, int selectionEnd) {
        this.text = text;
        this.selectionStart = selectionStart;
        this.selectionEnd = selectionEnd;
    }

}
