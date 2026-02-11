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
import org.jetbrains.annotations.Nullable;

public interface CommandGuiCoreInterface {

    /**
     * 是否更新命令语法结构
     */
    boolean isUpdateStructure();

    /**
     * 是否更新命令参数介绍
     */
    boolean isUpdateParamHint();

    /**
     * 是否更新命令错误原因
     */
    boolean isUpdateErrorReason();

    /**
     * 是否根据光标位置提供补全提示
     */
    boolean isCheckingBySelection();

    /**
     * 是否语法高亮
     */
    boolean isSyntaxHighlight();

    /**
     * 更新命令语法结构
     */
    void updateStructure(@Nullable String structure);

    /**
     * 更新命令参数介绍
     */
    void updateParamHint(@Nullable String paramHint);

    /**
     * 更新命令错误原因
     */
    void updateErrorReason(@Nullable ErrorReason[] errorReasons);

    /**
     * 补全建议列表更新要执行的操作
     */
    void updateSuggestions();

    /**
     * 获取输入框文字
     */
    @NotNull SelectedString getSelectedString();

    /**
     * 设置输入框文字
     */
    void setSelectedString(@NotNull SelectedString selectedString);

    /**
     * 更新语法高亮
     */
    void updateSyntaxHighlight(int @Nullable [] tokens);

}