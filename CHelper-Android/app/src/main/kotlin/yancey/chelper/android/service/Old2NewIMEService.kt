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

package yancey.chelper.android.service

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import yancey.chelper.android.window.ComposeLifecycleOwner
import yancey.chelper.core.OldCommandConverter
import yancey.chelper.data.SettingsDataStore
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.old2new.Old2NewIMEScreen

/** 旧命令转新命令输入法服务 */
class Old2NewIMEService : InputMethodService() {

    private var isSystemDarkMode = false
    private var theme by mutableStateOf(CHelperTheme.Theme.Light)
    private var time: Long = 0
    private var oldCommand: String? = null
    private var newCommand: String? = null
    private var lastIsUndo = false
    private var inputSession = 0L
    private var conversionJob: Job? = null
    private val converter by lazy { OldCommandConverter(this) }

    private var settingsDataStore = SettingsDataStore(this)
    private lateinit var composeLifecycleOwner: ComposeLifecycleOwner

    override fun onCreate() {
        super.onCreate()
        isSystemDarkMode =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        composeLifecycleOwner =
            ComposeLifecycleOwner().apply {
                attachToDecorView(window.window?.decorView)
                onCreate()
                onStart()
            }
        composeLifecycleOwner.lifecycleScope.launch {
            settingsDataStore.themeId().collect { themeId ->
                theme = CHelperTheme.themeOf(themeId, isSystemDarkMode)
                val newNightMode =
                    if (theme == CHelperTheme.Theme.Dark) Configuration.UI_MODE_NIGHT_YES
                    else Configuration.UI_MODE_NIGHT_NO
                resources.configuration.uiMode =
                    (newNightMode or
                        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        composeLifecycleOwner.apply {
            onStop()
            onDestroy()
            detachFromDecorView(window.window?.decorView)
        }
    }

    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            setContent {
                CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
                    Old2NewIMEScreen(
                        undo = {
                            if (lastIsUndo) {
                                text = oldCommand!!
                                lastIsUndo = false
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onWindowShown() {
        super.onWindowShown()
        composeLifecycleOwner.onResume()
        if (currentInputConnection == null) {
            return
        }
        // 兼容：QQ的输入框在文字改变后会立刻尝试弹出输入法，onWindowShown()被再次调用，撤回的内容会再次被替换成新版命令
        // 所以检测距离上次设置文字的时间间隔小于0.1s，就不再替换成新版命令
        if (SystemClock.elapsedRealtime() - time < 100) {
            return
        }
        val connection = currentInputConnection ?: return
        val snapshot = readSnapshot(connection)
        val original = snapshot.text
        if (original == newCommand) return
        val session = inputSession
        conversionJob?.cancel()
        conversionJob =
            composeLifecycleOwner.lifecycleScope.launch {
                try {
                    val converted = converter.convert(original)
                    if (
                        session != inputSession ||
                            currentInputConnection !== connection ||
                            text != original
                    )
                        return@launch
                    oldCommand = original
                    newCommand = converted
                    lastIsUndo = original != converted
                    if (lastIsUndo) text = converted
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("Old2NewIME", "Unable to convert command", e)
                }
            }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        invalidateConversion()
        oldCommand = null
        newCommand = null
        lastIsUndo = false
        time = 0
    }

    override fun onFinishInput() {
        invalidateConversion()
        super.onFinishInput()
    }

    private fun invalidateConversion() {
        inputSession++
        conversionJob?.cancel()
        conversionJob = null
    }

    override fun onWindowHidden() {
        invalidateConversion()
        super.onWindowHidden()
        composeLifecycleOwner.onPause()
    }

    private data class EditorSnapshot(val text: String, val before: Int, val selected: Int)

    private fun readSnapshot(connection: InputConnection): EditorSnapshot {
        val before =
            connection.getTextBeforeCursor(Short.MAX_VALUE.toInt(), 0)?.toString().orEmpty()
        val selected = connection.getSelectedText(0)?.toString().orEmpty()
        val after = connection.getTextAfterCursor(Short.MAX_VALUE.toInt(), 0)?.toString().orEmpty()
        return EditorSnapshot("$before$selected$after", before.length, selected.length)
    }

    private var text: String
        /**
         * 获取输入框文本
         *
         * @return 输入框的文本
         */
        get() = currentInputConnection?.let { readSnapshot(it).text }.orEmpty()
        /**
         * 设置输入框文本
         *
         * @param text 要设置的文本
         */
        set(text) {
            val inputConnection = currentInputConnection
            inputConnection ?: return
            time = SystemClock.elapsedRealtime()
            // 这里使用Short.MAX_VALUE，因为使用Integer.MAX_VALUE太大，QQ会崩
            val before = inputConnection.getTextBeforeCursor(Short.MAX_VALUE.toInt(), 0)
            val selected = inputConnection.getSelectedText(0)
            val after = inputConnection.getTextAfterCursor(Short.MAX_VALUE.toInt(), 0)
            val beforeLength = before?.length ?: 0
            val selectedLength = selected?.length ?: 0
            val afterLength = after?.length ?: 0
            inputConnection.setSelection(0, beforeLength + selectedLength + afterLength)
            inputConnection.commitText(text, text.length)
        }
}
