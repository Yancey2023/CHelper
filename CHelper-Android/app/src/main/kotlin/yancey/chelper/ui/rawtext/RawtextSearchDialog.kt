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

package yancey.chelper.ui.rawtext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.CustomDialog
import yancey.chelper.ui.common.dialog.CustomDialogProperties
import yancey.chelper.ui.common.dialog.DialogContainer
import yancey.chelper.ui.common.widget.Text

/*
 * 通用的可搜索列表选择弹窗，物品 / 槽位 / 族类型三处共用。
 * 点条目即回填并关闭。
 */
@Composable
fun RawtextSearchDialog(
    title: String,
    entries: List<NamedEntry>,
    onPick: (NamedEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, entries) {
        if (query.isBlank()) {
            entries
        } else {
            val q = query.trim().lowercase()
            entries.filter { it.id.lowercase().contains(q) || it.name.lowercase().contains(q) }
        }
    }

    CustomDialog(
        onDismissRequest = onDismiss,
        properties = CustomDialogProperties(usePlatformDefaultWidth = false)
    ) {
        DialogContainer(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp),
            backgroundNoTranslate = true,
            cornerSize = 22.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    )
                    RawtextSmallIconButton(text = "关闭", color = CHelperTheme.colors.textSecondary, onClick = onDismiss)
                }
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CHelperTheme.colors.background)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    textStyle = TextStyle(color = CHelperTheme.colors.textMain, fontSize = 14.sp),
                    cursorBrush = SolidColor(CHelperTheme.colors.mainColor),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = "搜索 ID 或名称…",
                                    style = TextStyle(color = CHelperTheme.colors.textHint, fontSize = 14.sp)
                                )
                            }
                            inner()
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (entries.isEmpty()) "数据加载中或不可用" else "没有匹配项",
                            style = TextStyle(color = CHelperTheme.colors.textHint)
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        items(filtered) { entry ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CHelperTheme.colors.background)
                                    .clickable { onPick(entry) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = entry.name,
                                    style = TextStyle(fontSize = 14.sp, color = CHelperTheme.colors.textMain)
                                )
                                Text(
                                    text = entry.id,
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        color = CHelperTheme.colors.textSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
