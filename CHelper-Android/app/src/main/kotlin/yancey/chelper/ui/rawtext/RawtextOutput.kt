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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.R
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.CustomDialog
import yancey.chelper.ui.common.dialog.DialogContainer
import yancey.chelper.ui.common.layout.Surface
import yancey.chelper.ui.common.widget.Switch
import yancey.chelper.ui.common.widget.Text

/** 预览面板：玩家实际看到的内容 */
@Composable
fun RawtextPreviewPanel(viewModel: RawtextViewModel, preview: AnnotatedString, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, clipCornerSize = 0.dp, horizontalPadding = 0.dp, verticalPadding = 6.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.layout_rawtext_preview_title), style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary))
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.layout_rawtext_preview_auto_scale), style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary))
                Switch(viewModel.autoScale, { viewModel.autoScale = it })
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                RawtextAutoScalePreview(preview, viewModel.autoScale)
            }
        }
    }
}

/** 预览文本：单行过长时按最长行自动缩放字号（可选） */
@Composable
private fun RawtextAutoScalePreview(preview: AnnotatedString, enabled: Boolean) {
    val baseFontSize = 18f
    if (!enabled) {
        BasicText(
            text = preview,
            style = TextStyle(color = Color.White, fontSize = baseFontSize.sp, fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF7CE87C)).padding(1.dp),
        )
        return
    }
    var fontSize by remember { mutableStateOf(baseFontSize) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val avail = constraints.maxWidth.toFloat()
        var locked by remember(preview, avail) { mutableStateOf(false) }
        BasicText(
            text = preview,
            style = TextStyle(color = Color.White, fontSize = fontSize.sp, fontFamily = FontFamily.Monospace),
            softWrap = false,
            modifier = Modifier.wrapContentWidth(unbounded = true).border(1.dp, Color(0xFF7CE87C)).padding(1.dp),
            onTextLayout = { layout ->
                if (!locked) {
                    locked = true
                    var w = 0f
                    for (i in 0 until layout.lineCount) w = maxOf(w, layout.getLineRight(i))
                    if (w > 0f && avail > 0f) {
                        val target = (fontSize * avail / w).coerceIn(2f, baseFontSize)
                        if (kotlin.math.abs(target - fontSize) > 0.02f) fontSize = target
                    }
                }
            },
        )
    }
}

/** 输出 */
@Composable
fun RawtextOutputScreen(viewModel: RawtextViewModel, onCopy: () -> Unit, onImport: () -> Unit) {
    viewModel.revision
    val json = remember(viewModel.revision) { viewModel.pretty() }
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        RawtextSectionLabel(stringResource(R.string.layout_rawtext_output_title))
        RawtextHint(stringResource(R.string.layout_rawtext_output_hint))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RawtextSmallButton(stringResource(R.string.layout_rawtext_copy), onClick = onCopy)
            RawtextSmallButton(stringResource(R.string.layout_rawtext_import), onClick = onImport)
        }
        Spacer(Modifier.height(6.dp))
        Surface(Modifier.fillMaxSize(), clipCornerSize = 10.dp) {
            SelectionContainer {
                Text(
                    json,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = CHelperTheme.colors.textMain),
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(10.dp),
                )
            }
        }
    }
}

/** 导入对话框 */
@Composable
fun RawtextImportDialog(
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    CustomDialog(onDismissRequest = { if (!loading) onDismiss() }) {
        DialogContainer(backgroundNoTranslate = true) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(
                    stringResource(R.string.layout_rawtext_import_title),
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                RawtextHint(stringResource(R.string.layout_rawtext_import_hint))
                RawtextTextField(
                    text,
                    { text = it },
                    Modifier.fillMaxWidth(),
                    placeholder = stringResource(R.string.layout_rawtext_import_placeholder),
                    maxLines = 12,
                )
                error?.let {
                    Text(it, style = TextStyle(color = CHelperTheme.colors.textErrorReason, fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        RawtextSmallButton(stringResource(R.string.layout_rawtext_import_cancel), Modifier.fillMaxWidth(), onClick = { if (!loading) onDismiss() })
                    }
                    Box(Modifier.weight(1f)) {
                        RawtextSmallButton(stringResource(R.string.layout_rawtext_import_confirm), Modifier.fillMaxWidth(), onClick = { if (!loading) onImport(text) })
                    }
                }
            }
        }
    }
}
