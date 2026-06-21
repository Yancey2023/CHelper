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

package yancey.chelper.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import yancey.chelper.data.LocalCommandLabDataStore
import yancey.chelper.network.library.data.AuthorInfo
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.library.mcd.MCDContentView

@Composable
fun LocalLibraryShowScreen(library: LibraryFunction?) {
    RootViewWithHeaderAndCopyright(title = library?.name ?: "加载中") {
        // MCDContentView 现在不再自带 LazyColumn，需要外层提供滚动；
        // 这里用 verticalScroll 给整个内容区一个垂直滚动通道
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 15.dp, vertical = 10.dp)
        ) {
            MCDContentView(content = library?.content)
        }
    }
}

@Composable
fun LocalLibraryShowScreen(id: Int? = null) {
    val context = LocalContext.current
    val localCommandLabDataStore = remember(context) { LocalCommandLabDataStore(context) }
    val localLibraryFunction by localCommandLabDataStore.localLibraryFunction(id)
        .collectAsState(initial = null)
    LocalLibraryShowScreen(library = localLibraryFunction)
}

@Preview
@Composable
fun LibraryShowScreenLightThemePreview() {
    val library = LibraryFunction().apply {
        name = "Library"
        author = AuthorInfo(name = "Author")
        content = buildString {
            appendLine("@name=TestLibrary")
            appendLine("@version=1.0.0")
            appendLine("# This is a comment")
            appendLine("/say Hello")
            appendLine("/tp @s 0 0 0")
        }
    }
    CHelperTheme(theme = CHelperTheme.Theme.Light, backgroundBitmap = null) {
        LocalLibraryShowScreen(library = library)
    }
}

@Preview
@Composable
fun LibraryShowScreenDarkThemePreview() {
    val library = LibraryFunction().apply {
        name = "Library"
        author = AuthorInfo(name = "Author")
        content = buildString {
            appendLine("@name=TestLibrary")
            appendLine("@version=1.0.0")
            appendLine("# This is a comment")
            appendLine("/say Hello")
            appendLine("/tp @s 0 0 0")
        }
    }
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        LocalLibraryShowScreen(library = library)
    }
}
