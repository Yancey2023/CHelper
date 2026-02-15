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

package yancey.chelper.android.window.library.view

import android.annotation.SuppressLint
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import yancey.chelper.R
import yancey.chelper.android.window.library.adapter.LibraryShowAdapter
import yancey.chelper.android.window.view.BaseView
import yancey.chelper.network.library.data.LibraryFunction

/**
 * 命令库显示视图
 */
@SuppressLint("ViewConstructor")
class LocalLibraryShowView @SuppressLint("HardwareIds") constructor(
    fwsContext: FWSContext,
    before: LibraryFunction
) : BaseView(fwsContext, R.layout.layout_library_show) {
    init {
        view.findViewById<View>(R.id.back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        val likeButton = view.findViewById<View>(R.id.btn_like)
        val likeCount = view.findViewById<TextView>(R.id.like_count)
        val name = view.findViewById<TextView>(R.id.name)
        val adapter = LibraryShowAdapter(context, before)
        val listView = view.findViewById<RecyclerView>(R.id.list_view)
        listView.addItemDecoration(
            DividerItemDecoration(
                context,
                DividerItemDecoration.VERTICAL
            )
        )
        listView.setLayoutManager(LinearLayoutManager(context))
        listView.setAdapter(adapter)
        likeButton.visibility = GONE
        likeCount.visibility = GONE
        name.text = before.name
    }
}
