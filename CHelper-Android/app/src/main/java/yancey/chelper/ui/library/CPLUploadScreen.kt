package yancey.chelper.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import yancey.chelper.android.common.util.LocalLibraryManager
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.CaptchaDialog
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Button
import yancey.chelper.ui.common.widget.Switch
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.common.widget.TextField

@Composable
fun CPLUploadScreen(
    viewModel: CPLUploadViewModel = viewModel(),
    navController: NavHostController
) {
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

            // Public Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = viewModel.isPublic,
                    onCheckedChange = { viewModel.isPublic = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "公开到指令市场 (需审核)",
                    modifier = Modifier.clickable { viewModel.isPublic = !viewModel.isPublic },
                    style = TextStyle(color = CHelperTheme.colors.textMain)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            // Upload Button
            Button(
                text = if (viewModel.isLoading) "上传中..." else "确认上传",
                onClick = {
                    if (!viewModel.isLoading) {
                        if (viewModel.isPublic) {
                            captchaCallback = { code ->
                                viewModel.upload(code) {
                                    navController.popBackStack()
                                }
                            }
                            showCaptchaDialog = true
                        } else {
                            viewModel.upload(null) {
                                navController.popBackStack()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showImportDialog) {
        ImportLocalLibraryDialog(
            onDismiss = { showImportDialog = false },
            onSelect = { id ->
                viewModel.loadFromLocal(id)
                showImportDialog = false
            }
        )
    }
}

@Composable
fun ImportLocalLibraryDialog(onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val libraries = remember { LocalLibraryManager.INSTANCE?.getFunctions() ?: mutableListOf() }

    LaunchedEffect(Unit) {
        LocalLibraryManager.INSTANCE?.ensureInit()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(CHelperTheme.colors.backgroundComponentNoTranslate)
                .padding(16.dp)
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
