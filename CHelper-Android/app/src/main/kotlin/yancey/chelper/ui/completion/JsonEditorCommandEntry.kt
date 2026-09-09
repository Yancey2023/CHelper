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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import yancey.chelper.ui.JsonEditorSession
import yancey.chelper.ui.RawtextScreenKey
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Text

/**
 * 检测 tellraw/titleraw 中 JSON 文本参数（rawtext）的起点：
 * - `tellraw <目标> <json>`：第二参数即 JSON；
 * - `titleraw <目标> <title|subtitle|actionbar> <json>`：第三参数（titleLocation 后）才 JSON，
 *   且位置词必须先输入完（其后有空白），避免在输入位置词时就出现入口。
 * 目标参数可以是选择器/普通词或带引号的玩家名。非 JSON 参数位（如 titleraw clear/times）返回 null。
 */
fun detectJsonParameterStart(text: String): Int? {
    // titleraw：目标 + 位置词(title/subtitle/actionbar) 之后的第三参数位
    val titleRaw = Regex("^/?titleraw\\s+(\"[^\"]*\"|\\S+)\\s+(title|subtitle|actionbar)\\s+").find(text)
    if (titleRaw != null) {
        return titleRaw.range.last + 1
    }
    // tellraw：目标之后的第二参数位
    val tellRaw = Regex("^/?tellraw\\s+(\"[^\"]*\"|\\S+)\\s+").find(text) ?: return null
    return tellRaw.range.last + 1
}

/**
 * 以"补全项"样式渲染的 JSON 编辑器入口（由补全列表在 JSON 参数位插入，样式与普通建议一致）。
 * 点击 → [JsonEditorSession.begin] 快照参数起点与已输入内容 → 跳转本环境内已注册的
 * rawtext 页（主界面/悬浮窗各自 NavHost 中均为同一窗口内的页面切换）。
 */
@Composable
fun JsonEditorActionRow(
    text: String,
    jsonStart: Int,
    navController: NavHostController,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                JsonEditorSession.begin(
                    jsonStart,
                    text.substring(jsonStart).ifBlank { null }
                )
                navController.navigate(RawtextScreenKey)
            }
            .padding(5.dp)
    ) {
        Text(
            text = "✎ 用 JSON 编辑器编辑",
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(
                fontSize = 14.sp,
                color = CHelperTheme.colors.mainColor,
            ),
        )
        Text(
            text = "打开 rawtext 编辑器，完成后自动插入此处",
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(
                color = CHelperTheme.colors.textSecondary,
                fontSize = 14.sp,
            ),
        )
    }
}

/**
 * 消费 rawtext 页回填结果（常驻于命令页）。
 *
 * 实现为轻量轮询而非依赖导航/生命周期事件：rawtext 页 finish() 后结果写入
 * [JsonEditorSession]，本页无论处于前台/被覆盖/悬浮窗状态都能在数百毫秒内消费回填；
 * 非发起方（无 pending）consume 恒为空操作，不会串窗。页面销毁时协程自动结束。
 */
@Composable
fun JsonEditorSessionConsumer(
    viewModel: CompletionViewModel,
    isCheckingBySelection: Boolean,
    isSyntaxHighlight: Boolean,
    isShowErrorReason: Boolean,
) {
    LaunchedEffect(Unit) {
        while (true) {
            JsonEditorSession.consume()?.let { (start, json) ->
                viewModel.command.edit {
                    replace(start, length, json)
                    selection = TextRange(start + json.length)
                }
                viewModel.onSelectionChanged(
                    isCheckingBySelection,
                    isSyntaxHighlight,
                    isShowErrorReason
                )
            }
            delay(300)
        }
    }
}
