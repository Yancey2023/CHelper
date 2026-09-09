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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Text

/** 灰色小字说明 */
@Composable
fun RawtextHint(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary),
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

/** 小节标题 */
@Composable
fun RawtextSectionLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CHelperTheme.colors.mainColor),
    )
}

/** 小号操作按钮（内嵌在主色块中） */
@Composable
fun RawtextSmallButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CHelperTheme.colors.mainColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}

/** 小号切换 chip */
@Composable
fun RawtextChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = CHelperTheme.colors.mainColor
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) color else color.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(color = if (selected) Color.White else color, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/** 紧凑单行/多行输入框（无补全，等宽字体，CHelper Surface 背景） */
@Composable
fun RawtextTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minLines: Int = 1,
    maxLines: Int = 1,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = maxLines == 1,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = TextStyle(color = CHelperTheme.colors.textMain, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
        cursorBrush = SolidColor(CHelperTheme.colors.mainColor),
        modifier = modifier,
        decorationBox = { inner ->
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(CHelperTheme.colors.background).padding(horizontal = 8.dp, vertical = 4.dp)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = TextStyle(color = CHelperTheme.colors.textHint, fontSize = 14.sp))
                }
                inner()
            }
        },
    )
}
