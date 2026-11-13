/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
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

package yancey.chelper.ui.designsystem.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.RadioButton
import yancey.chelper.ui.designsystem.widget.SelectionItem
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.designsystem.widget.TextButton
import yancey.chelper.ui.designsystem.widget.TextField

/**
 * 举报对话框：选预设理由 + 选填补充说明，提交时把"理由 + 补充"拼成 reason 给后端。
 *
 * 预设是为了：
 * 1. 引导用户给出可分类的原因，方便管理员后台聚合；
 * 2. 不强迫用户写长文，降低使用门槛。
 *
 * 不在这里处理网络请求，由调用方传 `onConfirm(reason)` 收尾，方便 ViewModel 复用 / 上层做 toast。
 */
@Composable
fun ReportDialog(
    onDismissRequest: () -> Unit,
    title: String = "举报",
    targetDescription: String? = null,
    onConfirm: (reason: String) -> Unit,
) {
    val presetReasons = remember {
        listOf("违法违规内容", "色情低俗", "辱骂 / 人身攻击", "广告 / 引流", "抄袭他人作品", "其他")
    }
    var selectedReason by remember { mutableStateOf<String?>(null) }
    val extraNote = rememberTextFieldState()

    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                targetDescription?.takeIf { it.isNotBlank() }?.let { Text("针对：$it") }
                presetReasons.forEach { reason ->
                    SelectionItem(
                        headlineContent = { Text(reason) },
                        leadingContent = {
                            RadioButton(selected = selectedReason == reason, onClick = null)
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                                .selectable(
                                    selected = selectedReason == reason,
                                    role = Role.RadioButton,
                                    onClick = { selectedReason = reason },
                                ),
                    )
                }
                TextField(
                    state = extraNote,
                    hint = "补充说明（可选）",
                    lineLimits =
                        TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 4),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("取消") } },
        confirmButton = {
            TextButton(
                enabled = selectedReason != null,
                onClick = {
                    val core = selectedReason ?: return@TextButton
                    val note = extraNote.text.toString().trim()
                    onDismissRequest()
                    onConfirm(if (note.isNotEmpty()) "$core：$note" else core)
                },
            ) {
                Text("提交")
            }
        },
        containerColor = CHelperTheme.colors.backgroundComponentNoTranslate,
    )
}
