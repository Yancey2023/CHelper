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

package yancey.chelper.ui.common.layout

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.R
import yancey.chelper.ui.ShowTextScreenKey
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Switch
import yancey.chelper.ui.common.widget.Text

@Composable
fun NameAndContent(
    name: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name)
            if (description != null) {
                Text(
                    text = description,
                    style = TextStyle(
                        color = CHelperTheme.colors.textSecondary,
                        fontSize = 14.sp,
                    )
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
    @DrawableRes iconId: Int = R.drawable.chevron_right,
    onClick: () -> Unit
) {
    NameAndContent(
        name = name,
        description = description,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(iconId, Modifier.size(24.dp), name)
    }
}

@Composable
fun NameAndValue(name: String, value: String) {
    NameAndContent(name) {
        SelectionContainer {
            Text(
                text = value,
                style = TextStyle(
                    color = CHelperTheme.colors.textSecondary
                ),
            )
        }
    }
}

@Composable
fun NameAndLink(name: String, link: Uri) {
    val context = LocalContext.current
    NameAndAction(name, null, R.drawable.external_link) {
        context.startActivity(Intent(Intent.ACTION_VIEW, link))
    }
}

@Composable
fun NameAndAsset(
    navController: NavHostController,
    name: String,
    assetsPath: String,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current
    NameAndAction(name) {
        coroutineScope.launch {
            val content = withContext(Dispatchers.IO) {
                context.assets.open(assetsPath).bufferedReader().use { it.readText() }
            }
            navController.navigate(ShowTextScreenKey(name, content))
        }
    }
}

@Composable
fun CollectionName(name: String) {
    Text(
        text = name,
        modifier = Modifier.padding(horizontal = 25.dp, vertical = 15.dp)
    )
}

@Composable
fun SettingsItem(
    name: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    NameAndContent(name = name, description = description) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
