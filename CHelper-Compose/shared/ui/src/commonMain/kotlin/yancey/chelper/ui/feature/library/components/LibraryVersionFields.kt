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
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.designsystem.widget.TextField
import yancey.chelper.ui.feature.library.mcd.color

@Composable
fun LibraryVersionFields(
    name: TextFieldState,
    description: TextFieldState,
    version: TextFieldState,
    tags: TextFieldState,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .padding(16.dp)
    ) {
        Text(
            text = "版本信息",
            style =
                TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CHelperTheme.colors.textMain,
                ),
        )
        Spacer(Modifier.height(14.dp))
        TextField(
            state = name,
            hint = stringResource(Res.string.upload_field_name),
            modifier = Modifier.fillMaxWidth(),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        Spacer(Modifier.height(10.dp))
        TextField(
            state = description,
            hint = stringResource(Res.string.upload_field_description),
            modifier = Modifier.fillMaxWidth(),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(
                state = version,
                hint = stringResource(Res.string.upload_field_version),
                modifier = Modifier.weight(1f),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            TextField(
                state = tags,
                hint = stringResource(Res.string.upload_field_tags),
                modifier = Modifier.weight(1f),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
        }
    }
}
