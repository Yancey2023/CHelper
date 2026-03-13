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

package yancey.chelper.ui.completion

import android.content.Context
import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hjq.toast.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.android.util.HistoryManager
import yancey.chelper.android.util.MonitorUtil
import yancey.chelper.core.CHelperCore
import yancey.chelper.core.ErrorReason
import yancey.chelper.core.SelectedString
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

class CompletionViewModel : ViewModel() {
    var isShowMenu by mutableStateOf(false)
    var command by mutableStateOf(TextFieldState())
    var structure by mutableStateOf<String?>(null)
    var paramHint by mutableStateOf<String?>(null)
    var errorReasons by mutableStateOf<Array<ErrorReason?>?>(null)
    var suggestionsSize by mutableIntStateOf(0)
    var suggestionsUpdateTimes by mutableIntStateOf(0)
    var syntaxHighlightTokens by mutableStateOf<IntArray?>(null)
    var core: CHelperCore? = null
    var lastInput: SelectedString = SelectedString("", 0, 0)
    private var historyManager: HistoryManager? = null
    private var file: File? = null
    private var isResumed = false

    fun init(context: Context) {
        historyManager = HistoryManager.getInstance(context)
        file = context.filesDir.resolve("cache").resolve("lastInput.dat")
    }

    fun resumeText() {
        if (isResumed) {
            return
        }
        isResumed = true
        if (file!!.exists()) {
            try {
                viewModelScope.launch {
                    command = withContext(Dispatchers.IO) {
                        DataInputStream(BufferedInputStream(file!!.inputStream())).use { dataInputStream ->
                            return@withContext TextFieldState(
                                dataInputStream.readUTF(),
                                TextRange(
                                    dataInputStream.readInt(),
                                    dataInputStream.readInt()
                                )
                            )
                        }
                    }
                }
            } catch (_: IOException) {

            }
        }
    }

    fun onSelectionChanged(
        isCheckingBySelection: Boolean,
        isSyntaxHighlight: Boolean,
        isShowErrorReason: Boolean
    ) {
        val selectedString = SelectedString(
            command.text.toString(),
            min(command.selection.start, command.selection.end),
            max(command.selection.start, command.selection.end)
        )
        val isSyntaxHighlight = isSyntaxHighlight && command.text.length < 200
        val isUpdateErrorReason = isShowErrorReason || isSyntaxHighlight
        if (selectedString.text.isEmpty()) {
            // 输入内容为空
            lastInput = selectedString
            // 显示欢迎词
            structure = "欢迎使用CHelper"
            // 显示作者信息
            paramHint = "作者：Yancey"
            // 更新错误原因
            if (isUpdateErrorReason) {
                errorReasons = null
            }
            // 通知内核
            if (core != null) {
                core!!.onTextChanged(selectedString.text, 0)
            }
            // 更新补全提示
            suggestionsSize = core?.getSuggestionsSize() ?: 0
            suggestionsUpdateTimes++
            return
        }
        if (core == null) {
            return
        }
        if (selectedString.text == lastInput.text) {
            if (selectedString.selectionStart == lastInput.selectionStart) {
                return
            }
            lastInput = selectedString
            // 文本内容不变和光标都改变了
            // 如果关闭了"根据光标位置提供补全提示"，就什么都不做
            if (!isCheckingBySelection) {
                return
            }
            // 通知内核
            core!!.onSelectionChanged(selectedString.selectionStart)
        } else {
            lastInput = selectedString
            // 文本内容和光标都改变了
            // 如果关闭了"根据光标位置提供补全提示"，就在通知内核时把光标位置当成在文本最后面
            val selectionStart = if (isCheckingBySelection) {
                selectedString.selectionStart
            } else {
                selectedString.text.length
            }
            // 通知内核
            core!!.onTextChanged(selectedString.text, selectionStart)
            // 更新颜色
            syntaxHighlightTokens = if (isSyntaxHighlight) {
                core!!.getSyntaxToken()
            } else {
                null
            }
            // 更新命令语法结构
            structure = core!!.getStructure()
            // 更新错误原因
            if (isUpdateErrorReason) {
                errorReasons = core!!.getErrorReasons()
            }
        }
        // 更新命令参数介绍
        paramHint = core!!.getParamHint()
        // 更新补全提示列表
        suggestionsSize = core!!.getSuggestionsSize()
        suggestionsUpdateTimes++
    }

    fun onItemClick(which: Int) {
        if (core == null) {
            return
        }
        val result = core!!.onSuggestionClick(which)
        if (result != null) {
            command.edit {
                replace(0, length, result.text)
                selection = TextRange(
                    result.selection,
                    result.selection
                )
            }
        }
    }

    fun refreshCHelperCore(
        context: Context,
        cpackBranch: String,
        isCheckingBySelection: Boolean,
        isSyntaxHighlight: Boolean,
        isShowErrorReason: Boolean
    ) {
        if (cpackBranch.isEmpty()) {
            core?.close()
            core = null
            return
        }
        var cpackPath: String? = null
        for (filename in context.assets.list("cpack")!!) {
            if (filename!!.startsWith(cpackBranch)) {
                cpackPath = "cpack/$filename"
            }
        }
        if (core == null || core!!.path != cpackPath) {
            var newCore: CHelperCore? = null
            try {
                newCore = CHelperCore.fromAssets(context.assets, cpackPath)
            } catch (throwable: Throwable) {
                Toaster.show("资源包加载失败")
                Log.w("CompletionViewModel", "fail to load resource pack", throwable)
                MonitorUtil.generateCustomLog(throwable, "LoadResourcePackException")
            }
            if (newCore != null) {
                core?.close()
                core = newCore
                lastInput = SelectedString("", 0, 0)
                onSelectionChanged(isCheckingBySelection, isSyntaxHighlight, isShowErrorReason)
            }
        }
    }

    fun onCopy(content: String) {
        historyManager?.add(content)
    }

    override fun onCleared() {
        super.onCleared()
        historyManager?.save()
        core?.close()
        // 保存上次的输入内容
        if (file != null) {
            file?.parentFile?.mkdirs()
            try {
                DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { dataOutputStream ->
                    dataOutputStream.writeUTF(command.text.toString())
                    dataOutputStream.writeInt(command.selection.start)
                    dataOutputStream.writeInt(command.selection.end)
                }
            } catch (_: IOException) {

            }
        }
    }
}
