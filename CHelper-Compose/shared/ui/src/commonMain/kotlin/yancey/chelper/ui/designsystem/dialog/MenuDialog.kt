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

package yancey.chelper.ui.designsystem.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import yancey.chelper.ui.designsystem.widget.Divider
import yancey.chelper.ui.designsystem.widget.Text

@Composable
fun MenuDialog(
    data: Array<Pair<String, String>>,
    onChoose: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppDialog(onDismissRequest = onDismissRequest) {
        DialogContainer(cornerSize = 20.dp, backgroundNoTranslate = true) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                for ((index, pair) in data.withIndex()) {
                    if (index != 0) {
                        Divider(0.dp)
                    }
                    Box(
                        modifier =
                            Modifier.fillMaxWidth().height(45.dp).clickable(role = Role.Button) {
                                onDismissRequest.invoke()
                                onChoose(pair.second)
                            }
                    ) {
                        Text(
                            text = pair.first,
                            modifier = Modifier.align(alignment = Alignment.Center),
                            style = TextStyle(textAlign = TextAlign.Center),
                        )
                    }
                }
            }
        }
    }
}
