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

package yancey.chelper.ui

import androidx.compose.runtime.mutableIntStateOf

/**
 * tellraw/titleraw 的 JSON 参数 → rawtext 编辑器的跨页会话（主界面与悬浮窗共用）。
 *
 * 流程：命令页（CompletionScreen，含悬浮窗版）点"用 JSON 编辑器编辑" → [begin] 记录
 * JSON 参数起点与已输入内容 → navigate(RawtextScreenKey)；rawtext 页读到 [pending]
 * 进入"嵌入会话"模式（预填 [Pending.initialJson]、关闭自动保存、顶栏提供
 * "插入到命令并返回"）；点完成后 [finish] 写入结果并自增 [revision]（Compose 可观察），
 * 命令页（无论前后台组合状态）随 [revision] 变化自动消费 [resultJson] 回填原参数区。
 *
 * 取消/放弃 = [cancel]（命令文本保持原样）；会话为应用级单例，同一时刻只有一个，
 * 命令页消费后即清空；仅 [begin] 发起方持有 pending，另一个窗口的监听 consume 为空操作。
 */
object JsonEditorSession {

    /** 打开会话时快照的 JSON 参数区上下文 */
    data class Pending(
        /** 命令文本中 JSON 参数的起点（替换范围 = [jsonStart, 命令文本末尾)） */
        val jsonStart: Int,
        /** 已输入的 JSON 片段（可能不完整/不可解析；空参数时为 null） */
        val initialJson: String?,
    )

    @Volatile
    var pending: Pending? = null
        private set

    /** rawtext 页完成后的结果（compact 单行 JSON）；命令页消费后置空 */
    @Volatile
    var resultJson: String? = null
        private set

    private val _revision = mutableIntStateOf(0)

    /** 每次 [finish] 自增；组合中读取本属性即可订阅变化 */
    val revision: Int
        get() = _revision.intValue

    /** 命令页发起会话 */
    fun begin(jsonStart: Int, initialJson: String?) {
        pending = Pending(jsonStart, initialJson)
        resultJson = null
    }

    /** rawtext 页"插入到命令并返回"：写入结果并结束会话（pending 保留到命令页 consume 后清理） */
    @Synchronized
    fun finish(result: String) {
        resultJson = result
        _revision.intValue++
    }

    /** 命令页消费结果（回填完成后调用）；无结果/非发起方返回 null */
    @Synchronized
    fun consume(): Pair<Int, String>? {
        val jsonStart = pending?.jsonStart
        val result = resultJson ?: return null
        if (jsonStart == null) {
            return null
        }
        pending = null
        resultJson = null
        return jsonStart to result
    }

    /** 取消（rawtext 页放弃/系统返回）：命令文本保持原样 */
    @Synchronized
    fun cancel() {
        pending = null
        resultJson = null
    }
}
