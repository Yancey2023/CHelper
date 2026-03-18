package yancey.chelper.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import yancey.chelper.data.LocalCommandLabDataStore
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.CaptchaDialog
import yancey.chelper.ui.common.dialog.CustomDialog
import yancey.chelper.ui.common.dialog.DialogContainer
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Button
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.common.widget.TextField

@Composable
fun CPLUploadScreen(
    viewModel: CPLUploadViewModel = viewModel(),
    navController: NavHostController
) {
    val context = LocalContext.current
    val localCommandLabDataStore = remember(context) { LocalCommandLabDataStore(context) }
    val libraries by localCommandLabDataStore.localLibraryFunctions()
        .collectAsState(initial = emptyList())

    var showImportDialog by remember { mutableStateOf(false) }
    var showCaptchaDialog by remember { mutableStateOf(false) }
    var captchaCallback by remember { mutableStateOf<(String) -> Unit>({}) }

    if (showCaptchaDialog) {
        CaptchaDialog(
            action = "publish",
            onDismissRequest = { showCaptchaDialog = false },
            onSuccess = { code -> captchaCallback(code) }
        )
    }

    RootViewWithHeaderAndCopyright(title = "上传指令库") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Import Button
            Button(
                text = "从本地导入",
                onClick = { showImportDialog = true },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Fields
            TextField(state = viewModel.name, hint = "名称", modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(10.dp))
            TextField(state = viewModel.version, hint = "版本", modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(10.dp))
            TextField(
                state = viewModel.description,
                hint = "简介",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            TextField(
                state = viewModel.tags,
                hint = "标签 (逗号分隔)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Content
            TextField(
                state = viewModel.commands,
                hint = "指令内容 (MCD格式)",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.TopStart
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Upload Button
            Button(
                text = if (viewModel.isLoading) "上传中..." else "确认上传",
                onClick = {
                    if (!viewModel.isLoading) {
                        captchaCallback = { code ->
                            viewModel.upload(code) {
                                navController.popBackStack()
                            }
                        }
                        showCaptchaDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showImportDialog) {
        ImportLocalLibraryDialog(
            libraries = libraries,
            onDismiss = { showImportDialog = false },
            onSelect = { id ->
                viewModel.loadFromLocal(libraries[id])
                showImportDialog = false
            }
        )
    }
}

@Composable
fun ImportLocalLibraryDialog(
    libraries: List<LibraryFunction>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    CustomDialog(onDismissRequest = onDismiss) {
        DialogContainer(backgroundNoTranslate = true) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "选择本地库",
                    style = TextStyle(
                        fontSize = 20.sp,
                        color = CHelperTheme.colors.textMain,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    libraries.forEachIndexed { index, lib ->
                        Text(
                            text = lib.name ?: "未命名",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(index) }
                                .padding(vertical = 12.dp),
                            style = TextStyle(color = CHelperTheme.colors.textMain)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(CHelperTheme.colors.line)
                        )
                    }
                    if (libraries.isEmpty()) {
                        Text(
                            text = "本地无指令库",
                            style = TextStyle(color = CHelperTheme.colors.textHint)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "取消",
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(8.dp),
                        style = TextStyle(color = CHelperTheme.colors.mainColor)
                    )
                }
            }
        }
    }
}
