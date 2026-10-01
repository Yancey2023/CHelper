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

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hjq.toast.Toaster
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.android.util.MonitorUtil
import yancey.chelper.core.CHelperCore
import yancey.chelper.core.CommandContext
import yancey.chelper.core.ErrorReason
import yancey.chelper.core.KernelCache
import yancey.chelper.core.SelectedString
import yancey.chelper.core.Suggestion
import yancey.chelper.data.CopyHistoryDataStore
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

class CompletionViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    var isShowMenu by mutableStateOf(false)
    var isCommandEditorMode by mutableStateOf(false)
    var command by mutableStateOf(TextFieldState())
    var structure by mutableStateOf<String?>(null)
    var paramHint by mutableStateOf<String?>(null)
    var errorReasons by mutableStateOf<Array<ErrorReason>?>(null)
    var suggestionsSize by mutableIntStateOf(0)
    var suggestionsUpdateTimes by mutableIntStateOf(0)
    var syntaxHighlightTokens by mutableStateOf<IntArray?>(null)
    var nodeCount by mutableIntStateOf(0)
    var core: CHelperCore? = null

    /**
     * 已持有的共享内核租约（段 + 启用拓展包指纹；KernelCache 管理内核生命周期，
     * 页面只租借不 close）。仅主线程读写。
     */
    private var heldSegment: String? = null

    private var heldFingerprint: String? = null

    /**
     * 异步取内核的串行执行器 + 代际号：切换过快时旧结果按代际丢弃并归还租约。
     */
    private var composeGeneration = 0

    private val composeDispatcher =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "chelper-compose") }
            .asCoroutineDispatcher()

    /**
     * 当前命令文本对应的命令上下文，文本内容改变时重新创建
     */
    var context: CommandContext? = null
        private set

    /**
     * 当前补全提示列表对应的光标位置
     * 补全提示是按光标位置计算的，点击补全提示时需要用同一个位置
     */
    private var suggestionIndex = 0

    /**
     * 当前命令上下文对应的文本内容，用于避免文本不变时重复解析
     */
    private var contextText: String? = null
    var lastInput: SelectedString = SelectedString("", 0, 0)
    var syntaxHighlightMaxLength = 20000
    private val copyHistoryDataStore = CopyHistoryDataStore(appContext)
    private val file: File = appContext.filesDir.resolve("cache").resolve("lastInput.dat")
    private var isResumed = false

    fun resumeText() {
        if (isResumed) {
            return
        }
        isResumed = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    file.readCachedCommand()
                }?.let { command = it }
            } catch (_: IOException) {

            }
        }
    }

    /**
     * 获取当前补全提示列表中的其中一个补全提示
     *
     * @param which 第几个补全提示，从0开始
     */
    fun getSuggestion(which: Int): Suggestion? {
        return context?.getSuggestion(suggestionIndex, which)
    }

    fun onSelectionChanged(
        isCheckingBySelection: Boolean,
        isSyntaxHighlight: Boolean,
        isShowErrorReason: Boolean
    ) {
        val core = core ?: return
        val selectedString = SelectedString(
            command.text.toString(),
            min(command.selection.start, command.selection.end),
            max(command.selection.start, command.selection.end)
        )
        val isSyntaxHighlight =
            isSyntaxHighlight && command.text.length < syntaxHighlightMaxLength
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
            // 清除语法高亮
            syntaxHighlightTokens = null
            // 重新解析命令
            refreshContext(selectedString.text)
            nodeCount = 0
            // 更新补全提示
            suggestionIndex = 0
            suggestionsSize = context?.getSuggestionsSize(0) ?: 0
            suggestionsUpdateTimes++
            return
        }
        if (selectedString.text == lastInput.text) {
            if (selectedString.selectionStart == lastInput.selectionStart) {
                return
            }
            lastInput = selectedString
            // 只有光标改变了
            // 如果关闭了"根据光标位置提供补全提示"，就什么都不做
            if (!isCheckingBySelection) {
                return
            }
            // 文本内容不变，无需重新解析，直接用新的光标位置查询
            suggestionIndex = selectedString.selectionStart
        } else {
            lastInput = selectedString
            // 文本内容改变了，需要重新解析命令
            // 如果关闭了"根据光标位置提供补全提示"，就把光标位置当成在文本最后面
            suggestionIndex = if (isCheckingBySelection) {
                selectedString.selectionStart
            } else {
                selectedString.text.length
            }
            refreshContext(selectedString.text)
            // 更新颜色
            syntaxHighlightTokens = if (isSyntaxHighlight) {
                context?.syntaxToken
            } else {
                null
            }
            // 更新命令语法结构
            structure = context?.structure
            nodeCount = context?.nodeCount ?: 0
            // 更新错误原因
            if (isUpdateErrorReason) {
                errorReasons = context?.errorReasons
            }
        }
        // 更新命令参数介绍
        paramHint = context?.getParamHint(suggestionIndex)
        // 更新补全提示列表
        suggestionsSize = context?.getSuggestionsSize(suggestionIndex) ?: 0
        suggestionsUpdateTimes++
    }

    fun onItemClick(which: Int) {
        val result = context?.applySuggestion(suggestionIndex, which) ?: return
        command.edit {
            replace(0, length, result.text)
            selection = TextRange(
                result.selection,
                result.selection
            )
        }
    }

    /**
     * 文本内容改变时重新解析命令，生成新的命令上下文
     * 文本内容不变时不会重复解析
     */
    private fun refreshContext(text: String) {
        if (context != null && contextText == text) {
            return
        }
        context?.close()
        context = try {
            core?.createContext(text)
        } catch (throwable: Throwable) {
            Log.w("CompletionViewModel", "fail to create CommandContext", throwable)
            null
        }
        contextText = if (context != null) text else null
    }

    fun refreshCHelperCore(
        context: Context,
        cpackBranch: String,
        isCheckingBySelection: Boolean,
        isSyntaxHighlight: Boolean,
        isShowErrorReason: Boolean
    ) {
        // 主路径：内核来自共享 KernelCache（按主包启用段 + 启用拓展包配置租借，
        // 与库页高亮/rawtext 同段复用，内核生命周期由缓存管理，页面不 close）。
        // 空段 = 无可用版本，清空并归还租约。
        val segment = cpackBranch.replace('-', '/')
        if (segment.isEmpty()) {
            composeGeneration++
            releaseHeldCore()
            this.context?.close()
            this.context = null
            contextText = null
            core = null
            nodeCount = 0
            return
        }
        val generation = ++composeGeneration
        viewModelScope.launch(composeDispatcher) {
            var acquired = false
            try {
                // 读取当前启用拓展包配置指纹（桥内缓存，廉价）；配置变化也会触发重建
                val fingerprint = KernelCache.currentFingerprint(context)
                if (segment == heldSegment && fingerprint == heldFingerprint && core != null) {
                    // 段与包配置都没变（如重新进入命令页）：无需重建，同步一次 UI 状态即可
                    withContext(Dispatchers.Main.immediate) {
                        if (generation == composeGeneration) {
                            onSelectionChanged(
                                isCheckingBySelection,
                                isSyntaxHighlight,
                                isShowErrorReason
                            )
                        }
                    }
                    return@launch
                }
                val newCore = KernelCache.acquire(context, segment)
                acquired = newCore != null
                withContext(Dispatchers.Main.immediate) {
                    if (generation != composeGeneration) {
                        // 期间又切换了版本/配置：归还这次租约，丢弃
                        KernelCache.release(segment)
                        return@withContext
                    }
                    if (newCore == null) {
                        // 合成失败：透传 KernelCache 记录的原始原因，便于定位（常见：包文件校验不通过）
                        val reason = KernelCache.lastComposeFailure()
                        Toaster.show(
                            if (reason.isNullOrEmpty()) "资源包加载失败" else "资源包加载失败：" + reason
                        )
                        return@withContext
                    }
                    // 先归还旧租约（段或包配置任一变化都要换内核），再挂新内核
                    releaseOldHeld(segment, fingerprint)
                    heldSegment = segment
                    heldFingerprint = fingerprint
                    this@CompletionViewModel.context?.close()
                    this@CompletionViewModel.context = null
                    contextText = null
                    core = newCore
                    lastInput = SelectedString("", 0, 0)
                    onSelectionChanged(isCheckingBySelection, isSyntaxHighlight, isShowErrorReason)
                }
            } catch (cancel: CancellationException) {
                if (acquired) {
                    KernelCache.release(segment)
                }
                throw cancel
            } catch (throwable: Throwable) {
                Log.w("CompletionViewModel", "fail to acquire kernel", throwable)
                MonitorUtil.generateCustomLog(throwable, "ComposeCoreException")
                withContext(Dispatchers.Main.immediate) {
                    if (generation == composeGeneration) {
                        Toaster.show("资源包加载失败：" + (throwable.message ?: ""))
                    }
                }
            }
        }
    }

    /** 归还当前内核租约（内核生命周期归 KernelCache 管理） */
    private fun releaseHeldCore() {
        val segment = heldSegment ?: return
        val fingerprint = heldFingerprint ?: ""
        heldSegment = null
        heldFingerprint = null
        KernelCache.release(segment, fingerprint)
    }

    /**
     * 换内核前归还旧租约：仅当旧配置（段/包指纹）与目标不同才需要释放；
     * 相同则保留旧租约（缓存命中路径会直接复用内核）。
     */
    private fun releaseOldHeld(segment: String, fingerprint: String) {
        val oldSegment = heldSegment ?: return
        val oldFingerprint = heldFingerprint
        if (oldSegment != segment || oldFingerprint != fingerprint) {
            KernelCache.release(oldSegment, oldFingerprint ?: "")
        }
    }

    fun onCopy(content: String) {
        viewModelScope.launch {
            copyHistoryDataStore.add(content)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 使在途的取内核任务失效；其取消路径会自行归还租约（release 幂等）
        composeGeneration++
        releaseHeldCore()
        context?.close()
        context = null
        contextText = null
        core = null
        // 保存上次的输入内容
        try {
            file.writeCachedCommand(command)
        } catch (_: IOException) {

        }
    }

    private fun File.readCachedCommand(): TextFieldState? {
        if (!exists()) {
            return null
        }
        return try {
            DataInputStream(BufferedInputStream(inputStream())).use { dataInputStream ->
                val text = dataInputStream.readUTF()
                val start = dataInputStream.readInt()
                val end = dataInputStream.readInt()
                TextFieldState(text, TextRange(start, end))
            }
        } catch (_: EOFException) {
            delete()
            null
        } catch (_: IOException) {
            delete()
            null
        }
    }

    private fun File.writeCachedCommand(command: TextFieldState) {
        parentFile?.mkdirs()
        val tempFile = resolveSibling("$name.tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tempFile))).use { dataOutputStream ->
            dataOutputStream.writeUTF(command.text.toString())
            dataOutputStream.writeInt(command.selection.start)
            dataOutputStream.writeInt(command.selection.end)
        }
        if (exists() && !delete()) {
            tempFile.delete()
            throw IOException("Failed to replace cached command file: $absolutePath")
        }
        if (!tempFile.renameTo(this)) {
            tempFile.delete()
            throw IOException("Failed to move cached command file into place: $absolutePath")
        }
    }
}
