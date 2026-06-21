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

package yancey.chelper.ui.common.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Divider
import yancey.chelper.ui.common.widget.DividerVertical
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.common.widget.TextField

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
    onConfirm: (reason: String) -> Unit
) {
    val presetReasons = remember {
        listOf(
            "违法违规内容",
            "色情低俗",
            "辱骂 / 人身攻击",
            "广告 / 引流",
            "抄袭他人作品",
            "其他"
        )
    }
    var selectedReason by remember { mutableStateOf<String?>(null) }
    val extraNote = rememberTextFieldState()

    CustomDialog(onDismissRequest = onDismissRequest) {
        DialogContainer {
            Column {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp, 14.dp),
                    text = title,
                    style = TextStyle(
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = CHelperTheme.colors.textMain
                    )
                )

                if (!targetDescription.isNullOrBlank()) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        text = "针对：$targetDescription",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = CHelperTheme.colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    )
                }

                // 预设理由竖排，比 FlowRow 在窄屏上更好读
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    presetReasons.forEach { reason ->
                        val isSelected = selectedReason == reason
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) CHelperTheme.colors.mainColor.copy(alpha = 0.12f)
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .clickable { selectedReason = reason }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 单选圆点：选中实心，未选空心
                            Box(
                                modifier = Modifier
                                    .width(14.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(
                                        if (isSelected) CHelperTheme.colors.mainColor
                                        else CHelperTheme.colors.background
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = reason,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    color = CHelperTheme.colors.textMain,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                )
                            )
                        }
                    }
                }

                // 补充说明（可选）
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    text = "补充说明（可选）",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = CHelperTheme.colors.textSecondary
                    )
                )
                TextField(
                    state = extraNote,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .height(70.dp),
                    contentAlignment = Alignment.TopStart,
                    hint = "更多细节有助于管理员判断",
                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 4)
                )

                Spacer(Modifier.height(8.dp))
                Divider(0.dp)
                Row(Modifier.height(45.dp)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clickable { onDismissRequest() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "取消",
                            style = TextStyle(
                                fontSize = 18.sp,
                                color = CHelperTheme.colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                    DividerVertical(0.dp)
                    val canSubmit = selectedReason != null
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clickable(enabled = canSubmit) {
                                val core = selectedReason ?: return@clickable
                                val note = extraNote.text.toString().trim()
                                val finalReason = if (note.isNotEmpty()) "$core：$note" else core
                                onDismissRequest()
                                onConfirm(finalReason)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "提交",
                            style = TextStyle(
                                fontSize = 18.sp,
                                color = if (canSubmit) CHelperTheme.colors.mainColor else CHelperTheme.colors.textSecondary,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}
