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

package yancey.chelper.ui.designsystem.layout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.Switch
import yancey.chelper.ui.designsystem.widget.Text

@Composable
fun NameAndContent(
    name: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name)
            if (description != null) {
                Text(
                    text = description,
                    style = TextStyle(color = CHelperTheme.colors.textSecondary, fontSize = 14.sp),
                )
            }
        }
        content()
    }
}

@Composable
fun NameAndAction(
    name: String,
    description: String? = null,
    iconId: DrawableResource = Res.drawable.chevron_right,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    NameAndContent(
        name = name,
        description = description,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Icon(iconId, Modifier.size(24.dp), name)
    }
}

@Composable
fun NameAndValue(name: String, value: String) {
    NameAndContent(name) {
        SelectionContainer {
            Text(text = value, style = TextStyle(color = CHelperTheme.colors.textSecondary))
        }
    }
}

@Composable
fun CollectionName(name: String) {
    Text(text = name, modifier = Modifier.padding(horizontal = 25.dp, vertical = 15.dp))
}

@Composable
fun SettingsItem(
    name: String,
    description: String?,
    checked: Boolean?,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    NameAndContent(name = name, description = description, modifier = modifier) {
        if (checked != null) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
