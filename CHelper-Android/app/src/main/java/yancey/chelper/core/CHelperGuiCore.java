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

import java.io.Closeable;

/**
 * 对CHelperCore的包装，增加了Gui相关的逻辑
 */
public class CHelperGuiCore implements Closeable {

    /**
     * 软件内核
     */
    private @Nullable CHelperCore core;

    /**
     * GUI相关的接口
     */
    private @Nullable CommandGuiCoreInterface commandGuiCoreInterface;

    /**
     * 上一次输入的命令
     */
    private SelectedString lastInput = new SelectedString("", 0, 0);

    public CHelperGuiCore() {
    }

    public void setCommandGuiCoreInterface(@NotNull CommandGuiCoreInterface commandGuiCoreInterface) {
        this.commandGuiCoreInterface = commandGuiCoreInterface;
    }

    /**
     * 当光标改变时要执行的内容
     */
    public void onSelectionChanged() {
        if (commandGuiCoreInterface == null) {
            return;
        }
        SelectedString selectedString = commandGuiCoreInterface.getSelectedString();
        if (selectedString.text.isEmpty()) {
            // 输入内容为空
            lastInput = selectedString;
            // 显示欢迎词
            if (commandGuiCoreInterface.isUpdateStructure()) {
                commandGuiCoreInterface.updateStructure("欢迎使用CHelper");
            }
            // 显示作者信息
            if (commandGuiCoreInterface.isUpdateParamHint()) {
                commandGuiCoreInterface.updateParamHint("作者：Yancey");
            }
            // 更新错误原因
            if (commandGuiCoreInterface.isUpdateErrorReason()) {
                commandGuiCoreInterface.updateErrorReason(null);
            }
            // 通知内核
            if (core != null) {
                core.onTextChanged(selectedString.text, 0);
            }
            // 更新补全提示
            commandGuiCoreInterface.updateSuggestions();
            return;
        }
        if (core == null) {
            return;
        }
        if (selectedString.text.equals(lastInput.text)) {
            if (selectedString.selectionStart == lastInput.selectionStart) {
                return;
            }
            lastInput = selectedString;
            // 文本内容不变和光标都改变了
            // 如果关闭了"根据光标位置提供补全提示"，就什么都不做
            if (!commandGuiCoreInterface.isCheckingBySelection()) {
                return;
            }
            // 通知内核
            core.onSelectionChanged(selectedString.selectionStart);
        } else {
            lastInput = selectedString;
            // 文本内容和光标都改变了
            // 如果关闭了"根据光标位置提供补全提示"，就在通知内核时把光标位置当成在文本最后面
            int selectionStart;
            if (commandGuiCoreInterface.isCheckingBySelection()) {
                selectionStart = selectedString.selectionStart;
            } else {
                selectionStart = selectedString.text.length();
            }
            // 通知内核
            core.onTextChanged(selectedString.text, selectionStart);
            // 更新颜色
            if (commandGuiCoreInterface.isSyntaxHighlight()) {
                commandGuiCoreInterface.updateSyntaxHighlight(core.getSyntaxToken());
            } else {
                commandGuiCoreInterface.updateSyntaxHighlight(null);
            }
            // 更新命令语法结构
            if (commandGuiCoreInterface.isUpdateStructure()) {
                commandGuiCoreInterface.updateStructure(core.getStructure());
            }
            // 更新错误原因
            if (commandGuiCoreInterface.isUpdateErrorReason()) {
                commandGuiCoreInterface.updateErrorReason(core.getErrorReasons());
            }
        }
        // 更新命令参数介绍
        if (commandGuiCoreInterface.isUpdateParamHint()) {
            commandGuiCoreInterface.updateParamHint(core.getParamHint());
        }
        // 更新补全提示列表
        commandGuiCoreInterface.updateSuggestions();
    }

    /**
     * 当补全提示被使用时要执行的内容
     *
     * @param which 第几个补全提示，从0开始
     */
    public void onItemClick(int which) {
        if (core == null || commandGuiCoreInterface == null) {
            return;
        }
        ClickSuggestionResult result = core.onSuggestionClick(which);
        if (result != null) {
            commandGuiCoreInterface.setSelectedString(new SelectedString(result.text, result.selection, result.selection));
        }
    }

    /**
     * 获取当前命令的补全提示数量
     */
    public int getSuggestionsSize() {
        if (core == null) {
            return 0;
        }
        return core.getSuggestionsSize();
    }

    /**
     * 获取当前命令其中一个补全提示
     *
     * @param which 第几个补全提示，从0开始
     */
    public @Nullable Suggestion getSuggestion(int which) {
        if (core == null) {
            return null;
        }
        return core.getSuggestion(which);
    }

    /**
     * 获取当前命令的所有补全提示
     * 由于性能原因，不建议使用这个方法，建议按需获取
     *
     * @return 所有补全提示
     */
    public @Nullable Suggestion[] getSuggestions() {
        if (core == null) {
            return null;
        }
        return core.getSuggestions();
    }

    /**
     * 获取软件内核
     *
     * @return 软件内核
     */
    @Nullable
    public CHelperCore getCore() {
        return core;
    }

    /**
     * 设置软件内核
     *
     * @param core 软件内核
     */
    public void setCore(@Nullable CHelperCore core) {
        // 先关闭之前的内核
        if (this.core != null) {
            this.core.close();
        }
        // 设置新内核
        this.core = core;
        // 更新界面
        lastInput = new SelectedString("", 0, 0);
        onSelectionChanged();
    }

    /**
     * 关闭内核，释放内存
     */
    @Override
    public void close() {
        if (core != null) {
            core.close();
            core = null;
        }
        commandGuiCoreInterface = null;
        lastInput = new SelectedString("", 0, 0);
    }

}
