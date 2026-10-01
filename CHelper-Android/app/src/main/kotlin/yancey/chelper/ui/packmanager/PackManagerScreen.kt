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

package yancey.chelper.ui.packmanager

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hjq.toast.Toaster
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import yancey.chelper.android.extension.AndroidExtensionPacks
import yancey.chelper.core.MainPackProvider
import yancey.chelper.data.ExtensionPackEntry
import yancey.chelper.data.SettingsDataStore
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.IsConfirmDialog
import yancey.chelper.ui.common.dialog.SelectionDialog
import yancey.chelper.ui.common.layout.Header
import yancey.chelper.ui.common.layout.RootView
import yancey.chelper.ui.common.widget.Switch
import yancey.chelper.ui.common.widget.Text

/**
 * 资源包管理：内置主资源包（置顶固定行，可切换命令分支）+ 第三方拓展包
 * （导入 / 启用 / 停用 / 删除 / 排序上移下移）。
 * 拓展包列表顺序即叠加顺序（列表靠上者优先）；任何变更都会使内核按新配置重建，
 * 命令页回到前台即生效（CompletionScreen 在 onResume 按配置指纹刷新）。
 */
@Composable
fun PackManagerScreen() {
    val context = LocalContext.current
    val dataStore = remember(context) { SettingsDataStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<ExtensionPackEntry>>(emptyList()) }
    var deleteTarget by remember { mutableStateOf<ExtensionPackEntry?>(null) }
    val cpackBranch by dataStore.cpackBranch().collectAsState(initial = null)
    var isShowChooseSegmentDialog by remember { mutableStateOf(false) }
    // 主包段清单（显示名 → id），供主资源包行的"当前分支"展示与切换选择
    var segmentChoices by remember { mutableStateOf(emptyArray<Pair<String, String>>()) }
    // 主资源包当前分支显示名：本地即时状态——选择后立刻更新，不依赖 DataStore 流回环
    var currentSegmentLabel by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            entries = withContext(Dispatchers.IO) { dataStore.extensionPacks().first() }
        }
    }

    fun persist(updated: List<ExtensionPackEntry>) {
        scope.launch {
            withContext(Dispatchers.IO) {
                dataStore.setExtensionPacks(updated)
                AndroidExtensionPacks.invalidate()
            }
            entries = updated
        }
    }

    LaunchedEffect(Unit) { reload() }
    // 进入页面时一次性装载段清单，并直接读一次当前分支（不依赖后续流推送）
    LaunchedEffect(context) {
        val (branch, choices) = withContext(Dispatchers.IO) {
            val branch = dataStore.cpackBranch().first()
            val choices = MainPackProvider.get(context)?.segments.orEmpty().map { segment ->
                val label = if (segment.version.isBlank()) {
                    segment.name.ifBlank { segment.id }
                } else {
                    "${segment.name.ifBlank { segment.id }}（${segment.version}）"
                }
                label to segment.id
            }.toTypedArray()
            branch to choices
        }
        segmentChoices = choices
        currentSegmentLabel = segmentLabelOf(branch, choices)
    }
    // 本页停留期间若分支被外部（设置页等）改动，同步副标题
    LaunchedEffect(cpackBranch, segmentChoices) {
        if (cpackBranch != null && segmentChoices.isNotEmpty()) {
            currentSegmentLabel = segmentLabelOf(cpackBranch, segmentChoices)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch { importPack(context, uri, dataStore) { reload() } }
        }
    }

    RootView {
        Column(Modifier.fillMaxSize()) {
            Header(
                title = "资源包管理",
                showBack = true,
                right = {
                    Text(
                        text = "导入 .chepack",
                        modifier = Modifier
                            .clickable { importLauncher.launch(arrayOf("*/*")) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = CHelperTheme.colors.mainColor,
                        ),
                    )
                },
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = "主资源包（内置）始终启用；下方第三方拓展包列表靠上者优先，改动后回到命令页即生效。",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = CHelperTheme.colors.textSecondary,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                MainPackRow(
                    currentSegmentLabel = currentSegmentLabel
                        .ifBlank { cpackBranch?.replace('-', '/').orEmpty() },
                    onClickChoose = { isShowChooseSegmentDialog = true },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "第三方拓展包",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = CHelperTheme.colors.textSecondary,
                    ),
                )
                Spacer(Modifier.height(2.dp))
                if (entries.isEmpty()) {
                    Text(
                        text = "还没有导入拓展包。点击右上角「导入 .chepack」选择包文件。",
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = CHelperTheme.colors.textSecondary,
                        ),
                    )
                } else {
                    entries.forEachIndexed { index, entry ->
                        PackRow(
                            entry = entry,
                            canUp = index > 0,
                            canDown = index < entries.size - 1,
                            onToggle = { enabled ->
                                persist(entries.mapIndexed { i, e ->
                                    if (i == index) e.copy(enabled = enabled) else e
                                })
                            },
                            onMoveUp = {
                                if (index > 0) {
                                    val list = entries.toMutableList()
                                    val tmp = list[index - 1]
                                    list[index - 1] = list[index]
                                    list[index] = tmp
                                    persist(list)
                                }
                            },
                            onMoveDown = {
                                if (index < entries.size - 1) {
                                    val list = entries.toMutableList()
                                    val tmp = list[index + 1]
                                    list[index + 1] = list[index]
                                    list[index] = tmp
                                    persist(list)
                                }
                            },
                            onDelete = { deleteTarget = entry },
                        )
                    }
                }
            }
        }
    }

    if (isShowChooseSegmentDialog) {
        SelectionDialog(
            title = "选择命令分支",
            initialValue = cpackBranch?.replace('-', '/'),
            onDismissRequest = { isShowChooseSegmentDialog = false },
            data = segmentChoices,
            onChoose = {
                isShowChooseSegmentDialog = false
                // 先本地即时刷新副标题，再持久化（不等 DataStore 流回环）
                currentSegmentLabel = segmentLabelOf(it, segmentChoices)
                scope.launch {
                    dataStore.setCpackBranch(it)
                }
            },
        )
    }
    deleteTarget?.let { target ->
        IsConfirmDialog(
            onDismissRequest = { deleteTarget = null },
            title = "删除资源包",
            content = "删除「${target.name}」？包文件也会被移除，删除后不可恢复。",
            onCancel = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        File(File(context.applicationContext.filesDir, "packs"), target.fileName)
                            .delete()
                        dataStore.setExtensionPacks(entries.filterNot { it.fileName == target.fileName })
                        AndroidExtensionPacks.invalidate()
                    }
                    reload()
                }
            },
        )
    }
}

/** 段 id → 显示名（去掉段名里自带的 "[内置]" 前缀，避免与主资源包标题重复）；找不到时回退显示 id */
private fun segmentLabelOf(branch: String?, choices: Array<Pair<String, String>>): String {
    val normalized = branch?.replace('-', '/')
    return choices.firstOrNull { it.second == normalized }?.first
        ?.removePrefix("[内置]")?.trim()
        ?: normalized.orEmpty()
}

/** 主资源包（内置）固定行：始终启用、置顶不可删除；点击可切换命令分支（与设置页同一设置项） */
@Composable
private fun MainPackRow(
    currentSegmentLabel: String,
    onClickChoose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClickChoose)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "主资源包（内置）",
                style = TextStyle(fontSize = 15.sp, color = CHelperTheme.colors.textMain),
            )
            Text(
                text = "始终启用 · 当前分支：$currentSegmentLabel",
                style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary),
            )
        }
        PackSmallButton("切换", onClick = onClickChoose)
        // 开关恒开不可关闭（主资源包是基础包）
        Switch(checked = true, onCheckedChange = {})
    }
}

@Composable
private fun PackRow(
    entry: ExtensionPackEntry,
    canUp: Boolean,
    canDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = TextStyle(fontSize = 15.sp, color = CHelperTheme.colors.textMain),
            )
            Text(
                text = "v${entry.version} · ${entry.fileName}",
                style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary),
            )
        }
        // 排序/删除用文字小按钮，避免依赖图标资源
        PackSmallButton("上移", enabled = canUp, onClick = onMoveUp)
        PackSmallButton("下移", enabled = canDown, onClick = onMoveDown)
        PackSmallButton("删除", onClick = onDelete)
        Switch(
            checked = entry.enabled,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun PackSmallButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        style = TextStyle(
            fontSize = 12.sp,
            color = if (enabled) CHelperTheme.colors.mainColor else CHelperTheme.colors.textSecondary,
        ),
    )
}

/** 剥离 JSON 注释（单行 // 与多行块注释，字符串内不处理），与核心 JsonUtil::stripJsonComments 同规则 */
private fun stripJsonComments(text: String): String {
    val out = StringBuilder(text.length)
    var inString = false
    var i = 0
    val n = text.length
    while (i < n) {
        val c = text[i]
        if (inString) {
            out.append(c)
            if (c == '\\' && i + 1 < n) {
                out.append(text[i + 1])
                i += 2
                continue
            }
            if (c == '"') {
                inString = false
            }
            i++
            continue
        }
        if (c == '"') {
            inString = true
            out.append(c)
            i++
            continue
        }
        if (c == '/' && i + 1 < n) {
            val d = text[i + 1]
            if (d == '/') {
                i += 2
                while (i < n && text[i] != '\n' && text[i] != '\r') {
                    i++
                }
                out.append(' ')
                continue
            }
            if (d == '*') {
                i += 2
                while (i + 1 < n && !(text[i] == '*' && text[i + 1] == '/')) {
                    i++
                }
                i = if (i + 1 < n) i + 2 else n
                out.append(' ')
                continue
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}

/** 读取 .chepack：校验根 manifest，写入 filesDir/packs/，登记到设置列表（同名 packId 视为更新） */
private suspend fun importPack(
    context: android.content.Context,
    uri: Uri,
    dataStore: SettingsDataStore,
    onDone: () -> Unit,
) {
    try {
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: return
        val manifest = withContext(Dispatchers.IO) {
            val json = Json { ignoreUnknownKeys = true }
            var textFound: String? = null
            java.util.zip.ZipInputStream(bytes.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "manifest.json") {
                        textFound = zip.readBytes().decodeToString()
                        break
                    }
                    zip.closeEntry()
                }
            }
            if (textFound == null) {
                throw IllegalArgumentException("不是有效的 CHelper 资源包（包内找不到 manifest.json，请用校验脚本 --pack 重新打包）")
            }
            runCatching { json.parseToJsonElement(stripJsonComments(textFound)).jsonObject }
                .getOrElse { e ->
                    throw IllegalArgumentException(
                        "不是有效的 CHelper 资源包（manifest 解析失败：" + (e.message ?: "未知错误") + "）"
                    )
                }
        }
        val packId = (manifest["packId"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        val name = (manifest["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        val version = (manifest["version"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: "0.0.0"
        val versionCode = (manifest["versionCode"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        if (packId == null || name == null) {
            throw IllegalArgumentException("不是有效的 CHelper 资源包（manifest 缺少 packId/name）")
        }
        val safePackId = packId.replace(Regex("[^A-Za-z0-9._-]"), "-")
        val fileName = "${safePackId}-$versionCode.chepack"
        withContext(Dispatchers.IO) {
            val dir = File(context.applicationContext.filesDir, "packs")
            dir.mkdirs()
            File(dir, fileName).writeBytes(bytes)
            val entries = dataStore.extensionPacks().first()
            val updated = if (entries.any { it.packId == packId }) {
                entries.map { if (it.packId == packId) it.copy(fileName = fileName, name = name, version = version) else it }
            } else {
                entries + ExtensionPackEntry(packId = packId, name = name, version = version, fileName = fileName)
            }
            dataStore.setExtensionPacks(updated)
            AndroidExtensionPacks.invalidate()
        }
        withContext(Dispatchers.Main) { Toaster.show("已导入 $name") }
        onDone()
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toaster.show("导入失败：" + (e.message ?: "未知错误"))
        }
    }
}
