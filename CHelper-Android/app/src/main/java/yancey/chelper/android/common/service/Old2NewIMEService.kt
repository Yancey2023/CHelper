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

package yancey.chelper.android.common.service

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import yancey.chelper.android.common.util.Settings
import yancey.chelper.core.CHelperCore
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.old2new.Old2NewIMEScreen

/**
 * 旧命令转新命令输入法服务
 */
class Old2NewIMEService : InputMethodService() {

    private var isSystemDarkMode = false
    private var theme by mutableStateOf(CHelperTheme.Theme.Light)
    private var time: Long = 0
    private var oldCommand: String? = null
    private var newCommand: String? = null
    private var lastIsUndo = false

    override fun onCreate() {
        super.onCreate()
        isSystemDarkMode =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        refreshTheme()
    }

    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            setContent {
                CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
                    Old2NewIMEScreen(undo = {
                        if (lastIsUndo) {
                            text = oldCommand!!
                            lastIsUndo = false
                        }
                    })
                }
            }
        }
    }

    override fun onWindowShown() {
        super.onWindowShown()
        refreshTheme()
        if (currentInputConnection == null) {
            return
        }
        // 兼容：QQ的输入框在文字改变后会立刻尝试弹出输入法，onWindowShown()被再次调用，撤回的内容会再次被替换成新版命令
        // 所以检测距离上次设置文字的时间间隔小于0.1s，就不再替换成新版命令
        if (System.currentTimeMillis() - time < 100) {
            return
        }
        val oldCommand = this.text
        // 如果与前一次命令转换后的结果相同，说明用户再次点了文本框，不作处理
        if (oldCommand == this.newCommand) {
            return
        }
        this.oldCommand = oldCommand
        // 转换为新版命令
        newCommand = CHelperCore.old2new(this, oldCommand)
        // 如果与转换后新版指令与旧版一样，也不作处理
        lastIsUndo = oldCommand != newCommand
        if (lastIsUndo) {
            this.text = newCommand!!
        }
    }

    private var text: String
        /**
         * 获取输入框文本
         *
         * @return 输入框的文本
         */
        get() {
            val inputConnection = currentInputConnection
            inputConnection ?: return ""
            // 这里使用Short.MAX_VALUE，因为使用Integer.MAX_VALUE太大，QQ会崩
            val textBeforeCursor =
                inputConnection.getTextBeforeCursor(Short.MAX_VALUE.toInt(), 0)
            val selectedText =
                inputConnection.getSelectedText(Short.MAX_VALUE.toInt())
            val textAfterCursor =
                inputConnection.getTextAfterCursor(Short.MAX_VALUE.toInt(), 0)
            return listOfNotNull(textBeforeCursor, selectedText, textAfterCursor).joinToString("")
        }
        /**
         * 设置输入框文本
         *
         * @param text 要设置的文本
         */
        set(text) {
            val inputConnection = currentInputConnection
            inputConnection ?: return
            time = System.currentTimeMillis()
            // 这里使用Short.MAX_VALUE，因为使用Integer.MAX_VALUE太大，QQ会崩
            val before =
                inputConnection.getTextBeforeCursor(Short.MAX_VALUE.toInt(), 0)
            val selected =
                inputConnection.getSelectedText(Short.MAX_VALUE.toInt())
            val after =
                inputConnection.getTextAfterCursor(Short.MAX_VALUE.toInt(), 0)
            val beforeLength = before?.length ?: 0
            val selectedLength = selected?.length ?: 0
            val afterLength = after?.length ?: 0
            inputConnection.setSelection(0, beforeLength + selectedLength + afterLength)
            inputConnection.commitText(text, text.length)
        }

    private fun refreshTheme() {
        theme = when (Settings.INSTANCE.themeId) {
            "MODE_NIGHT_NO" -> CHelperTheme.Theme.Light
            "MODE_NIGHT_YES" -> CHelperTheme.Theme.Dark
            else -> if (isSystemDarkMode) CHelperTheme.Theme.Dark else CHelperTheme.Theme.Light
        }
        val newNightMode =
            if (theme == CHelperTheme.Theme.Dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        resources.configuration.uiMode =
            (newNightMode or (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()))
    }

}