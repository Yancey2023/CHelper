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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import yancey.chelper.R
import yancey.chelper.ui.common.CHelperTheme

@Composable
fun RootView(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CHelperTheme.colors.background)
    ) {
        CHelperTheme.backgroundBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(insets = WindowInsets.systemBars.union(WindowInsets.ime))
        ) {
            content()
        }
    }
}

@Composable
fun RootViewWithHeaderAndCopyright(
    title: String,
    headerRight: @Composable () -> Unit = {},
    copyright: String = stringResource(R.string.common_copyright_yancey),
    content: @Composable () -> Unit
) {
    RootView {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(title = title, right = headerRight)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                content()
            }
            Copyright(Modifier.align(Alignment.CenterHorizontally), copyright)
        }
    }
}