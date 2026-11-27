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

package yancey.chelper.ui.feature.library.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.input.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import org.jetbrains.compose.resources.*
import yancey.chelper.domain.library.ChainItem
import yancey.chelper.domain.library.parseMCDStructure
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.dialog.MenuDialog
import yancey.chelper.ui.designsystem.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.Text

@Composable
fun LibraryDetailContent(
    title: String,
    resolvedSource: String,
    ambiguousDefault: String,
    hideMetadata: Boolean,
    onBack: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onLoongFlow: (() -> Unit)? = null,
    onCopy: (String) -> Unit,
    onMessage: (String) -> Unit,
    visualContent: @Composable (String, String, Boolean) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRawSource by remember { mutableStateOf(false) }
    var showLineCopy by remember { mutableStateOf(false) }
    RootViewWithHeaderAndCopyright(
        title = title,
        onBack = onBack,
        headerRight = {
            Row {
                if (onLoongFlow != null) {
                    Icon(
                        id = Res.drawable.download,
                        contentDescription = "游龙导入",
                        modifier =
                            Modifier.clickable(onClick = onLoongFlow).padding(5.dp).size(24.dp),
                    )
                }
                Icon(
                    id = Res.drawable.ellipsis,
                    contentDescription = "更多操作",
                    modifier = Modifier.clickable { showMenu = true }.padding(5.dp).size(24.dp),
                )
            }
        },
    ) {
        if (showRawSource) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(horizontal = 15.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CHelperTheme.colors.backgroundComponent)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
            ) {
                Text(text = resolvedSource)
            }
        } else {
            Box(Modifier.fillMaxSize().padding(horizontal = 15.dp, vertical = 10.dp)) {
                visualContent(resolvedSource, ambiguousDefault, !hideMetadata)
            }
        }
    }

    if (showMenu) {
        MenuDialog(
            onDismissRequest = { showMenu = false },
            data = buildList {
                    if (onEdit != null) add("编辑" to "edit")
                    add("逐行复制" to "line_copy")
                    add("复制全部 MCD 源码" to "copy_all")
                    add((if (showRawSource) "查看可视化" else "查看源码") to "toggle_view")
                    add("关闭" to "close")
                }
                    .toTypedArray(),
            onChoose = { action ->
                when (action) {
                    "edit" -> onEdit?.invoke()
                    "line_copy" -> showLineCopy = true
                    "copy_all" -> {
                        onCopy(resolvedSource)
                    }

                    "toggle_view" -> showRawSource = !showRawSource
                }
            },
        )
    }

    if (showLineCopy) {
        val commands =
            remember(resolvedSource, ambiguousDefault) {
                parseMCDStructure(resolvedSource, ambiguousDefault).chains.flatMap { chain ->
                    chain.items.mapNotNull { item ->
                        when (item) {
                            is ChainItem.Block -> item.block.command.takeIf(String::isNotEmpty)
                            is ChainItem.RawCommand -> item.command.takeIf(String::isNotEmpty)
                            is ChainItem.Comment -> null
                        }
                    }
                }
            }
        if (commands.isEmpty()) {
            DisposableEffect(Unit) {
                onMessage("没有可复制的命令")
                showLineCopy = false
                onDispose {}
            }
        } else {
            LineCopyContent(
                commands = commands,
                onDismiss = { showLineCopy = false },
                onCopy = onCopy,
            )
        }
    }
}
