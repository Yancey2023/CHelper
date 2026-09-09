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

package yancey.chelper.ui.rawtext

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import yancey.chelper.data.RawtextDataStore
import yancey.chelper.data.SettingsDataStore

/**
 * 原始 JSON 文本（titleraw）编辑器的状态与业务逻辑。
 * 持有可嵌套的元素树、调试模拟状态，并在开启自动保存时把元素树草稿与调试状态落盘。
 */
class RawtextViewModel(application: Application) : AndroidViewModel(application) {
    private val draftJson = Json { ignoreUnknownKeys = true }
    private val dataStore = RawtextDataStore(application.applicationContext)
    private val settingsDataStore = SettingsDataStore(application.applicationContext)

    val elements = mutableStateListOf<RawtextElement>()
    var revision by mutableIntStateOf(0)
        private set

    val debug = RawtextDebugState()

    /** 预览自动缩放：单行过长时按最长行缩放字号（默认开启） */
    var autoScale by mutableStateOf(true)

    var autoSaveEnabled by mutableStateOf(true)
        private set

    private var restored = false

    /** 恢复（草稿/调试/自动保存开关）完成的信号：嵌入场景需等它结束再注入初始内容，避免竞态覆盖 */
    private val restoredSignal = CompletableDeferred<Unit>()

    init {
        // 进入功能时才惰性装载：翻译键表（预览中文名）与补全内核租约，随版本/分支（主包段）联动
        viewModelScope.launch { loadDatasets() }
        restorePreferences()
    }

    /** 等待首帧恢复流程（restorePreferences）结束 */
    suspend fun awaitRestored() {
        restoredSignal.await()
    }

    private suspend fun currentSegment(): String {
        val branch = runCatching { settingsDataStore.cpackBranch().first() }.getOrNull() ?: "release/experiment"
        return branch.replace('-', '/')
    }

    private suspend fun loadDatasets() {
        val segment = currentSegment()
        try {
            // 翻译键表（预览中文名）与补全内核同段装载：键表走主包 readFile，内核走共享 KernelCache 租约
            RawtextDatasets.loadTranslateFromMainPack(getApplication(), segment)
            withContext(Dispatchers.IO) {
                RawtextCompletionKernel.ensure(getApplication(), segment)
            }
        } catch (throwable: Throwable) {
            Log.w("RawtextViewModel", "fail to load datasets from main pack segment $segment", throwable)
        }
    }

    private fun restorePreferences() {
        viewModelScope.launch {
            val prefs = dataStore.preferences().first()
            autoSaveEnabled = prefs.autoSaveEnabled
            prefs.debug?.let { runCatching { applyDebug(it) } }
            if (prefs.autoSaveEnabled && !prefs.draft.isNullOrBlank()) {
                runCatching { applyDraft(prefs.draft) }
            }
            restored = true
            restoredSignal.complete(Unit)
            // 草稿恢复后补一次修订号，触发 targets 重算，避免调试面板不显示
            touch()
        }
    }

    fun touch() {
        revision++
    }

    fun add(el: RawtextElement) {
        elements.add(el)
        touch()
        onContentChanged()
    }

    fun removeAt(i: Int) {
        elements.removeAt(i)
        touch()
        onContentChanged()
    }

    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j in elements.indices) {
            val t = elements[i]
            elements[i] = elements[j]
            elements[j] = t
            touch()
            onContentChanged()
        }
    }

    /** 用已解析好的元素整体替换（解析请在后台线程完成） */
    fun replaceElements(list: List<RawtextElement>) {
        elements.clear()
        elements.addAll(list)
        touch()
        onContentChanged()
    }

    suspend fun importFromText(text: String): Result<Unit> = runCatching {
        val list = withContext(Dispatchers.Default) { RawtextJson.importText(text) }
        replaceElements(list)
    }

    suspend fun applyPreset(item: RawtextPresets.RawtextPresetItem): Result<Unit> = runCatching {
        val list = withContext(Dispatchers.Default) { item.build() }
        replaceElements(list)
    }

    fun collectTargets(): RawtextDebugTargets = RawtextDebugEngine.collect(elements)

    /** 首帧后延迟加载翻译识别符，避免进入编辑器时卡顿 */
    fun ensureTranslate() {
        viewModelScope.launch {
            val segment = currentSegment()
            try {
                RawtextDatasets.loadTranslateFromMainPack(getApplication(), segment)
            } catch (throwable: Throwable) {
                Log.w("RawtextViewModel", "fail to load translate keys from main pack segment $segment", throwable)
            }
        }
    }

    fun pretty(): String = RawtextJson.pretty(elements)

    fun compact(): String = RawtextJson.compact(elements)

    fun setAutoSave(enabled: Boolean) {
        autoSaveEnabled = enabled
        viewModelScope.launch {
            dataStore.setAutoSave(enabled)
            if (enabled) dataStore.saveDraft(serializeDraft())
        }
    }

    /** 调试模拟值变化后调用，持久化模拟状态 */
    fun persistDebug() {
        if (!restored) return
        viewModelScope.launch { dataStore.saveDebug(serializeDebug()) }
    }

    /** 内容变化后调用：开了自动保存才落盘，避免无意义 IO。 */
    private fun onContentChanged() {
        if (!restored || !autoSaveEnabled) return
        viewModelScope.launch { dataStore.saveDraft(serializeDraft()) }
    }

    // ---- 草稿 / 调试状态序列化 ----

    private fun serializeDraft(): String =
        draftJson.encodeToString(ListSerializer(RawtextElement.serializer()), elements.toList())

    private fun applyDraft(draft: String) {
        val list = draftJson.decodeFromString(ListSerializer(RawtextElement.serializer()), draft)
        elements.clear()
        elements.addAll(list)
    }

    private fun serializeDebug(): String =
        draftJson.encodeToString(RawtextDebugSnapshot.serializer(), RawtextDebugSnapshot.from(debug))

    private fun applyDebug(value: String) {
        val snapshot = draftJson.decodeFromString(RawtextDebugSnapshot.serializer(), value)
        debug.apply(snapshot)
    }

    override fun onCleared() {
        super.onCleared()
        // 归还补全内核租约（内核生命周期归共享 KernelCache 管理）
        RawtextCompletionKernel.release()
    }
}
