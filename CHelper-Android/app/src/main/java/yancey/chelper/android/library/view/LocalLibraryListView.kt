/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2025  Yancey
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

package yancey.chelper.android.library.view

import android.annotation.SuppressLint
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hjq.toast.Toaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import yancey.chelper.R
import yancey.chelper.android.common.util.TextWatcherUtil
import yancey.chelper.android.common.view.BaseView
import yancey.chelper.android.library.adapter.LocalLibraryListAdapter
import yancey.chelper.android.library.util.LocalLibraryManager
import yancey.chelper.network.library.data.LibraryFunction
import java.util.stream.Collectors

/**
 * 命令库列表视图
 */
@SuppressLint("ViewConstructor")
class LocalLibraryListView @SuppressLint("NotifyDataSetChanged") constructor(fwsContext: FWSContext) :
    BaseView(fwsContext, R.layout.layout_library_list) {
    private val adapter: LocalLibraryListAdapter
    private var libraryFunctions: List<LibraryFunction?>? = null
    private val search: EditText
    private var isDirty = false
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        view.findViewById<View>(R.id.back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        val title = view.findViewById<TextView>(R.id.title)
        title.setText(R.string.layout_library_list_title_local)
        search = view.findViewById<EditText>(R.id.search)
        search.addTextChangedListener(TextWatcherUtil.onTextChanged {
            this.update(it)
        })
        val listView = view.findViewById<RecyclerView>(R.id.list_view)
        listView.addItemDecoration(
            DividerItemDecoration(
                context,
                DividerItemDecoration.VERTICAL
            )
        )
        adapter = LocalLibraryListAdapter(context) { libraryFunctions ->
            openView { fwsContext ->
                LocalLibraryShowView(fwsContext, libraryFunctions)
            }
        }
        listView.setLayoutManager(LinearLayoutManager(context))
        listView.setAdapter(adapter)
        update()
    }

    fun update(keyword: CharSequence?) {
        if (libraryFunctions == null) {
            return
        }
        if (TextUtils.isEmpty(keyword)) {
            adapter.setLibraryFunctions(libraryFunctions)
        } else {
            adapter.setLibraryFunctions(
                libraryFunctions!!.stream()
                    .filter { libraryFunction: LibraryFunction? ->
                        libraryFunction!!.name != null &&
                                libraryFunction.name!!.contains(search.getText())
                    }
                    .collect(Collectors.toList()))
        }
    }

    override fun onResume() {
        super.onResume()
        if (isDirty) {
            isDirty = false
            update()
        } else {
            update(search.getText())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    @SuppressLint("HardwareIds")
    fun update() {
        coroutineScope.launch {
            try {
                LocalLibraryManager.INSTANCE!!.ensureInit()
                libraryFunctions = LocalLibraryManager.INSTANCE!!.getFunctions().toList()
                update(search.text)
            } catch (e: Exception) {
                Toaster.show(e.message ?: "加载失败")
            }
        }
    }
}
