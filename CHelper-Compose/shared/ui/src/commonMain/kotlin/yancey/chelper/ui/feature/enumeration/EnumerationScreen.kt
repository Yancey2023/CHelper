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

package yancey.chelper.ui.feature.enumeration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.dialog.IsConfirmDialog
import yancey.chelper.ui.designsystem.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.designsystem.widget.Button
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.designsystem.widget.TextField
import yancey.chelper.ui.platform.LocalWideLayout

@Composable
fun EnumerationScreenTimes(viewModel: EnumerationViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(Res.string.layout_enumeration_times_label))
        TextField(
            state = viewModel.times,
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxWidth().padding(start = 15.dp),
            verticalPadding = 0.dp,
            lineLimits = TextFieldLineLimits.SingleLine,
        )
    }
}

@Composable
fun EnumerationScreenVariable(modifier: Modifier = Modifier, viewModel: EnumerationViewModel) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp)) {
            Text(
                text = stringResource(Res.string.layout_enumeration_variable_name),
                modifier = Modifier.weight(1f),
                style = TextStyle(textAlign = TextAlign.Center),
            )
            Spacer(modifier = Modifier.width(15.dp))
            Text(
                text = stringResource(Res.string.layout_enumeration_initial_value),
                modifier = Modifier.weight(1f),
                style = TextStyle(textAlign = TextAlign.Center),
            )
            Spacer(modifier = Modifier.width(15.dp))
            Text(
                text = stringResource(Res.string.layout_enumeration_interval),
                modifier = Modifier.weight(1f),
                style = TextStyle(textAlign = TextAlign.Center),
            )
            Spacer(modifier = Modifier.width(45.dp))
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(viewModel.variableList) { index, variable ->
                Spacer(modifier = Modifier.height(15.dp))
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(horizontal = 15.dp).heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        state = variable.name,
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                        verticalPadding = 0.dp,
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    Spacer(modifier = Modifier.width(15.dp))
                    TextField(
                        state = variable.start,
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                        verticalPadding = 0.dp,
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    Spacer(modifier = Modifier.width(15.dp))
                    TextField(
                        state = variable.interval,
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                        verticalPadding = 0.dp,
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    Spacer(modifier = Modifier.width(15.dp))
                    Icon(
                        id = Res.drawable.x,
                        modifier =
                            Modifier.clickable(onClick = { viewModel.variableList.removeAt(index) })
                                .size(24.dp),
                    )
                }
                if (index == viewModel.variableList.size) {
                    Spacer(modifier = Modifier.height(15.dp))
                }
            }
        }
    }
}

@Composable
fun EnumerationScreenButton(modifier: Modifier = Modifier, viewModel: EnumerationViewModel) {
    Column(modifier = modifier) {
        Button(text = stringResource(Res.string.layout_enumeration_add_variable)) {
            viewModel.variableList.add(DataVariable())
        }
        Button(text = stringResource(Res.string.layout_enumeration_run)) { viewModel.run() }
    }
}

@Composable
fun EnumerationContent(viewModel: EnumerationViewModel, onCopy: (String) -> Unit) {
    RootViewWithHeaderAndCopyright(stringResource(Res.string.layout_enumeration_title)) {
        if (!LocalWideLayout.current) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.height(15.dp))
                TextField(
                    state = viewModel.expression,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp).height(100.dp),
                    hint = stringResource(Res.string.layout_enumeration_expression_hint),
                )
                Spacer(Modifier.height(15.dp))
                EnumerationScreenTimes(viewModel = viewModel)
                Spacer(Modifier.height(15.dp))
                EnumerationScreenVariable(modifier = Modifier.weight(1f), viewModel = viewModel)
                EnumerationScreenButton(viewModel = viewModel)
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f)) {
                    Spacer(Modifier.height(15.dp))
                    TextField(
                        state = viewModel.expression,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp).weight(1f),
                        hint = stringResource(Res.string.layout_enumeration_expression_hint),
                    )
                    Spacer(Modifier.height(15.dp))
                    EnumerationScreenTimes(viewModel = viewModel)
                    Spacer(Modifier.height(15.dp))
                    EnumerationScreenButton(viewModel = viewModel)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Spacer(Modifier.height(15.dp))
                    EnumerationScreenVariable(modifier = Modifier.weight(1f), viewModel = viewModel)
                }
            }
        }
    }
    viewModel.failure?.let { message ->
        IsConfirmDialog(onDismissRequest = { viewModel.dismissFailure() }, content = message)
    }
    if (viewModel.isShowPreviewDialog) {
        IsConfirmDialog(
            onDismissRequest = { viewModel.isShowPreviewDialog = false },
            title = "输出预览",
            content = viewModel.output,
            onConfirm = { onCopy(viewModel.output) },
        )
    }
}
