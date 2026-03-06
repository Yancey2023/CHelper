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

package yancey.chelper.android.window.completion.view

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hjq.toast.Toaster
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import yancey.chelper.R
import yancey.chelper.android.common.util.ClipboardUtil
import yancey.chelper.android.common.util.FileUtil
import yancey.chelper.android.common.util.HistoryManager
import yancey.chelper.android.common.util.MonitorUtil
import yancey.chelper.android.common.util.SettingsDataStore
import yancey.chelper.android.common.widget.CommandEditText
import yancey.chelper.android.window.completion.adater.SuggestionListAdapter
import yancey.chelper.android.window.library.view.LocalLibraryListView
import yancey.chelper.android.window.view.BaseView
import yancey.chelper.core.CHelperCore
import yancey.chelper.core.CHelperGuiCore
import yancey.chelper.core.CommandGuiCoreInterface
import yancey.chelper.core.ErrorReason
import yancey.chelper.core.SelectedString
import yancey.chelper.core.Theme
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.properties.Delegates

@SuppressLint("ViewConstructor")
class CompletionView(
    fwsContext: FWSContext,
    var shutdown: Runnable,
    var hideView: Runnable?
) : BaseView(fwsContext) {
    private lateinit var fl_action_container: FrameLayout
    private lateinit var fl_actions: FrameLayout
    private lateinit var btn_action: View
    private lateinit var commandEditText: CommandEditText
    private var isShowActions = false
    var isGuiLoaded: Boolean = false
    private lateinit var core: CHelperGuiCore
    private lateinit var historyManager: HistoryManager
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var cpackBranch: String

    override fun onCreateView() {
        super.onCreateView()
        settingsDataStore = SettingsDataStore(getContext())
        var isCrowded by Delegates.notNull<Boolean>()
        var isShowErrorReason by Delegates.notNull<Boolean>()
        var isCheckingBySelection by Delegates.notNull<Boolean>()
        var isSyntaxHighlight by Delegates.notNull<Boolean>()
        var isHideWindowWhenCopying by Delegates.notNull<Boolean>()
        var isSavingWhenPausing by Delegates.notNull<Boolean>()
        runBlocking {
            isCrowded = settingsDataStore.isCrowded().first()
            isShowErrorReason = settingsDataStore.isShowErrorReason().first()
            isCheckingBySelection = settingsDataStore.isCheckingBySelection().first()
            isSyntaxHighlight = settingsDataStore.isSyntaxHighlight().first()
            isHideWindowWhenCopying = settingsDataStore.isHideWindowWhenCopying().first()
            isSavingWhenPausing = settingsDataStore.isSavingWhenPausing().first()
            cpackBranch = settingsDataStore.cpackBranch().first()
        }
        setContentView(if (isCrowded) R.layout.layout_completion_crowded else R.layout.layout_completion)
        historyManager = HistoryManager.getInstance(context)
        isGuiLoaded = false
        val isDarkMode = (context.resources
            .configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        core = CHelperGuiCore()
        val tv_structure = contentView.findViewById<TextView?>(R.id.structure)
        val tv_paramHint = contentView.findViewById<TextView?>(R.id.param_hint)
        val tv_errorReasons = contentView.findViewById<TextView?>(R.id.error_reasons)
        commandEditText = contentView.findViewById(R.id.expression)
        commandEditText.setListener(
            { core.onSelectionChanged() },
            { core.onSelectionChanged() },
            { isGuiLoaded })
        commandEditText.setTheme(if (isDarkMode) Theme.THEME_NIGHT else Theme.THEME_DAY)
        val adapter = SuggestionListAdapter(context, core, isCrowded)
        val recyclerView = contentView.findViewById<RecyclerView>(R.id.rv_command_list)
        recyclerView.setLayoutManager(LinearLayoutManager(context))
        recyclerView.setAdapter(adapter)
        core.setCommandGuiCoreInterface(object : CommandGuiCoreInterface {
            override fun isUpdateStructure(): Boolean {
                return true
            }

            override fun isUpdateParamHint(): Boolean {
                return true
            }

            override fun isUpdateErrorReason(): Boolean {
                return isShowErrorReason || isSyntaxHighlight()
            }

            override fun isCheckingBySelection(): Boolean {
                return isCheckingBySelection
            }

            override fun isSyntaxHighlight(): Boolean {
                if (!isSyntaxHighlight) {
                    return false
                }
                val text = commandEditText.getText()
                return text != null && text.length < 200
            }

            override fun updateStructure(structure: String?) {
                if (tv_structure != null) {
                    tv_structure.text = structure
                }
                adapter.setStructure(structure)
            }

            override fun updateParamHint(paramHint: String?) {
                if (tv_paramHint != null) {
                    tv_paramHint.text = paramHint
                }
                adapter.setParamHint(paramHint)
            }

            override fun updateErrorReason(errorReasons: Array<ErrorReason?>?) {
                if (isShowErrorReason) {
                    val errorReasonStr: String?
                    if (errorReasons.isNullOrEmpty()) {
                        errorReasonStr = null
                    } else {
                        if (errorReasons.size == 1) {
                            errorReasonStr = errorReasons[0]!!.errorReason
                        } else {
                            val errorReasonStrBuilder = StringBuilder("可能的错误原因：")
                            for (i in errorReasons.indices) {
                                errorReasonStrBuilder.append("\n").append(i + 1).append(". ")
                                    .append(errorReasons[i]!!.errorReason)
                            }
                            errorReasonStr = errorReasonStrBuilder.toString()
                        }
                    }
                    if (tv_errorReasons != null) {
                        if (errorReasonStr == null) {
                            tv_errorReasons.visibility = GONE
                        } else {
                            tv_errorReasons.visibility = VISIBLE
                            tv_errorReasons.text = errorReasonStr
                        }
                    }
                    adapter.setErrorReasons(errorReasonStr)
                }
                commandEditText.setErrorReasons(if (isSyntaxHighlight()) errorReasons else null)
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun updateSuggestions() {
                adapter.notifyDataSetChanged()
            }

            override fun getSelectedString(): SelectedString {
                return commandEditText.getSelectedString()
            }

            override fun setSelectedString(selectedString: SelectedString) {
                commandEditText.setSelectedString(selectedString)
            }

            override fun updateSyntaxHighlight(tokens: IntArray?) {
                commandEditText.setColors(tokens)
            }
        })
        btn_action = contentView.findViewById(R.id.btn_show_menu)
        btn_action.setOnClickListener {
            isShowActions = !isShowActions
            updateActions()
        }
        contentView.findViewById<View>(R.id.btn_copy).setOnClickListener {
            val text = commandEditText.getText() ?: return@setOnClickListener
            historyManager.add(text.toString())
            if (ClipboardUtil.setText(getContext(), text)) {
                Toaster.show("已复制")
                if (isHideWindowWhenCopying) {
                    hideView?.run()
                }
            } else {
                Toaster.show("复制失败")
            }
        }
        contentView.findViewById<View>(R.id.btn_undo)
            .setOnClickListener { commandEditText.undo() }
        contentView.findViewById<View>(R.id.btn_redo)
            .setOnClickListener { commandEditText.redo() }
        contentView.findViewById<View>(R.id.btn_clear)
            .setOnClickListener { commandEditText.clear() }
        contentView.findViewById<View>(R.id.btn_history)
            .setOnClickListener {
                openView {
                    HistoryView(it, historyManager)
                }
            }
        contentView.findViewById<View>(R.id.btn_local_library)
            .setOnClickListener {
                openView {
                    LocalLibraryListView(
                        it
                    )
                }
            }
        contentView.findViewById<View>(R.id.btn_shut_down)
            .setOnClickListener { shutdown.run() }
        // 加载上次的输入内容
        var selectedString: SelectedString? = null
        if (isSavingWhenPausing) {
            val file =
                FileUtil.getFile(context.filesDir.absolutePath, "cache", "lastInput.dat")
            if (file.exists()) {
                try {
                    DataInputStream(BufferedInputStream(FileInputStream(file))).use { dataInputStream ->
                        selectedString = SelectedString(
                            dataInputStream.readUTF(),
                            dataInputStream.readInt(),
                            dataInputStream.readInt()
                        )
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "fail to save file : " + file.absolutePath, e)
                    MonitorUtil.generateCustomLog(e, "IOException")
                }
            }
        }
        if (selectedString == null) {
            selectedString = SelectedString("", 0)
        }
        val finalSelectedString: SelectedString = selectedString
        contentView.getViewTreeObserver()
            .addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    isGuiLoaded = true
                    contentView.getViewTreeObserver().removeOnGlobalLayoutListener(this)
                    commandEditText.setSelectedString(finalSelectedString)
                    core.onSelectionChanged()
                }
            })
        fl_action_container = contentView.findViewById(R.id.fl_action_container)
        fl_actions = contentView.findViewById(R.id.fl_actions)
        if (!isShowActions) {
            updateActions()
        }
    }

    override fun onPause() {
        super.onPause()
        isGuiLoaded = false
        historyManager.save()
        // 保存上次的输入内容
        val file =
            FileUtil.getFile(getContext().filesDir.absolutePath, "cache", "lastInput.dat")
        if (!FileUtil.createParentFile(file)) {
            Log.e(TAG, "fail to create parent file : " + file.absolutePath)
            return
        }
        val selectedString = commandEditText.getSelectedString()
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { dataOutputStream ->
                dataOutputStream.writeUTF(selectedString.text)
                dataOutputStream.writeInt(selectedString.selectionStart)
                dataOutputStream.writeInt(selectedString.selectionEnd)
            }
        } catch (e: IOException) {
            Log.e(TAG, "fail to save file : " + file.absolutePath, e)
            MonitorUtil.generateCustomLog(e, "IOException")
        }
    }

    private fun updateActions() {
        if (isShowActions) {
            fl_action_container.addView(fl_actions)
            btn_action.setBackgroundResource(R.drawable.chevron_down)
        } else {
            fl_action_container.removeView(fl_actions)
            btn_action.setBackgroundResource(R.drawable.chevron_up)
        }
    }

    override fun onResume() {
        super.onResume()
        isGuiLoaded = true
        var cpackPath: String? = null
        for (filename in context.assets.list("cpack")!!) {
            if (filename!!.startsWith(cpackBranch)) {
                cpackPath = "cpack/$filename"
            }
        }
        if (core.core == null || core.core!!.path != cpackPath) {
            var core1: CHelperCore? = null
            try {
                core1 = CHelperCore.fromAssets(getContext().assets, cpackPath)
            } catch (throwable: Throwable) {
                Toaster.show("资源包加载失败")
                Log.w(TAG, "fail to load resource pack", throwable)
                MonitorUtil.generateCustomLog(throwable, "LoadResourcePackException")
            }
            core.setCore(core1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        core.close()
    }

    companion object {
        private const val TAG = "WritingCommandView"
    }
}
