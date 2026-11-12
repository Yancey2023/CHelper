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

package yancey.chelper.ui.feature.rawtext

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import yancey.chelper.ui.platform.ClientToast as Toaster
import yancey.chelper.ui.platform.LocalClientServices as LocalContext

@Composable
fun RawtextScreen(viewModel: RawtextViewModel = rememberRawtextViewModel()) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(emptyList<NamedEntry>() to emptyList<NamedEntry>()) }
    LaunchedEffect(context) {
        entries =
            RawtextDataRepository.loadItems(context) to RawtextDataRepository.loadSlots(context)
    }
    val output by viewModel.output.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(viewModel, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushDraft()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushDraft()
        }
    }
    val clipboard = yancey.chelper.ui.platform.LocalClientServices.current.platform
    val scope = rememberCoroutineScope()

    RawtextScreenContent(
        items = entries.first,
        slots = entries.second,
        onMessage = { Toaster.show(it) },
        segments = viewModel.segments,
        activeTextSegmentId = viewModel.activeTextSegmentId,
        onUpdateActiveTextSegmentId = { viewModel.activeTextSegmentId = it },
        copyFormat = viewModel.copyFormat,
        onUpdateCopyFormat = viewModel::updateCopyFormat,
        autoSaveEnabled = viewModel.autoSaveEnabled,
        onSetAutoSave = viewModel::setAutoSave,
        showAutoSavePrompt = viewModel.showAutoSavePrompt,
        onConfirmAutoSave = viewModel::confirmAutoSave,
        mockPlayerP = viewModel.mockPlayerP,
        onUpdateMockPlayerP = { viewModel.mockPlayerP = it },
        mockPlayerR = viewModel.mockPlayerR,
        onUpdateMockPlayerR = { viewModel.mockPlayerR = it },
        mockPlayerA = viewModel.mockPlayerA,
        onUpdateMockPlayerA = { viewModel.mockPlayerA = it },
        mockPlayerS = viewModel.mockPlayerS,
        onUpdateMockPlayerS = { viewModel.mockPlayerS = it },
        mockScore = viewModel.mockScore,
        onUpdateMockScore = { viewModel.mockScore = it },
        mockTranslate = viewModel.mockTranslate,
        onUpdateMockTranslate = { viewModel.mockTranslate = it },
        mockCondition = viewModel.mockCondition,
        onUpdateMockCondition = { viewModel.mockCondition = it },
        generatedJson = output.formattedJson,
        previewText = output.preview,
        validationMessages = output.validation + listOfNotNull(viewModel.draftError),
        isPreviewEmpty = output.isEmpty,
        onUpdateTextSegment = viewModel::updateTextSegment,
        onBackspaceAtStart = viewModel::onBackspaceAtStart,
        onMoveSegment = viewModel::moveSegment,
        onRemoveSegment = viewModel::removeSegment,
        onAddFeature = viewModel::addFeature,
        onApplyFormat = viewModel::applyFormat,
        onClearAll = viewModel::clearAll,
        onPersistMockSettings = viewModel::persistMockSettings,
        onDecodeRawJson = viewModel::decodeRawJson,
        onCommitSegmentEdit = viewModel::commitSegmentEdit,
        onFindSegment = viewModel::findSegment,
        onCopyByPreference = {
            val compressed = viewModel.copyFormat == "compressed"
            scope.launch {
                clipboard.copyText(viewModel.copyJson(pretty = !compressed))
                Toaster.show(if (compressed) "已复制压缩 JSON" else "已复制格式化 JSON")
            }
        },
        onPasteFromClipboard = { setText ->
            scope.launch {
                val text = clipboard.clipboardText()
                if (text.isNullOrBlank()) Toaster.show("剪贴板没有可解析文本") else setText(text)
            }
        },
    )
}

@Composable
private fun rememberRawtextViewModel(): RawtextViewModel {
    val services = yancey.chelper.ui.platform.LocalClientServices.current
    val model = viewModel { RawtextViewModel(services.rawtext) }
    androidx.compose.runtime.SideEffect {
        services.session.register(services.rawtext, model::flushAndAwait)
    }
    return model
}
