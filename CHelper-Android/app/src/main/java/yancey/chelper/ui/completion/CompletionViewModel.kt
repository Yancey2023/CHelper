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
import com.hjq.toast.Toaster
import yancey.chelper.android.common.util.FileUtil
import yancey.chelper.android.common.util.HistoryManager
import yancey.chelper.android.common.util.MonitorUtil
import yancey.chelper.android.common.util.Settings
import yancey.chelper.core.CHelperCore
import yancey.chelper.core.CHelperGuiCore
import yancey.chelper.core.CommandGuiCoreInterface
import yancey.chelper.core.ErrorReason
import yancey.chelper.core.SelectedString
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
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
    var core = CHelperGuiCore()
    private var historyManager: HistoryManager? = null
    private var file: File? = null

    fun init(context: Context) {
        historyManager = HistoryManager.getInstance(context)
        file = FileUtil.getFile(context.filesDir.absolutePath, "cache", "lastInput.dat")
        if (Settings.INSTANCE.isSavingWhenPausing) {
            if (file!!.exists()) {
                try {
                    DataInputStream(BufferedInputStream(FileInputStream(file))).use { dataInputStream ->
                        command = TextFieldState(
                            dataInputStream.readUTF(),
                            TextRange(
                                dataInputStream.readInt(),
                                dataInputStream.readInt()
                            )
                        )
                    }
                } catch (_: IOException) {

                }
            }
        }
        core.setCommandGuiCoreInterface(object : CommandGuiCoreInterface {

            override fun isUpdateStructure() = true

            override fun isUpdateParamHint() = true

            override fun isUpdateErrorReason() =
                Settings.INSTANCE.isShowErrorReason || isSyntaxHighlight()

            override fun isCheckingBySelection() = Settings.INSTANCE.isCheckingBySelection

            override fun isSyntaxHighlight() =
                Settings.INSTANCE.isSyntaxHighlight && command.text.length < 200

            override fun updateStructure(structure: String?) {
                this@CompletionViewModel.structure = structure
            }

            override fun updateParamHint(paramHint: String?) {
                this@CompletionViewModel.paramHint = paramHint
            }

            override fun updateErrorReason(errorReasons: Array<ErrorReason?>?) {
                this@CompletionViewModel.errorReasons = errorReasons
            }

            override fun updateSuggestions() {
                this@CompletionViewModel.suggestionsSize = core.getSuggestionsSize()
                this@CompletionViewModel.suggestionsUpdateTimes++
            }

            override fun getSelectedString(): SelectedString {
                val selectionStart =
                    min(command.selection.start, command.selection.end)
                val selectionEnd =
                    max(command.selection.start, command.selection.end)
                return SelectedString(
                    command.text.toString(),
                    selectionStart,
                    selectionEnd
                )
            }

            override fun setSelectedString(selectedString: SelectedString) {
                command.edit {
                    replace(0, length, selectedString.text)
                    selection = TextRange(
                        selectedString.selectionStart,
                        selectedString.selectionEnd
                    )
                }
            }

            override fun updateSyntaxHighlight(tokens: IntArray?) {
                syntaxHighlightTokens = tokens
            }
        })
    }

    fun refreshCHelperCore(context: Context) {
        val cpackPath = Settings.INSTANCE.cpackPath
        if (core.core == null || core.core!!.path != cpackPath) {
            var core1: CHelperCore? = null
            try {
                core1 = CHelperCore.fromAssets(context.assets, cpackPath)
            } catch (throwable: Throwable) {
                Toaster.show("资源包加载失败")
                Log.w("CompletionViewModel", "fail to load resource pack", throwable)
                MonitorUtil.generateCustomLog(throwable, "LoadResourcePackException")
            }
            core.setCore(core1)
        }
    }

    fun onCopy(content: String) {
        historyManager?.add(content)
    }

    override fun onCleared() {
        super.onCleared()
        historyManager?.save()
        // 保存上次的输入内容
        if (file != null && FileUtil.createParentFile(file)) {
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
