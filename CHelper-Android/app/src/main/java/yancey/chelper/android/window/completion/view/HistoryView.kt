/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 * 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https:></https:>//www.gnu.org/licenses/>.
 */
package yancey.chelper.android.window.completion.view

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import yancey.chelper.R
import yancey.chelper.android.common.util.HistoryManager
import yancey.chelper.android.window.completion.adater.HistoryAdapter
import yancey.chelper.android.window.view.BaseView

/**
 * 历史列表视图
 */
@SuppressLint("ViewConstructor")
class HistoryView(fwsContext: FWSContext, var historyManager: HistoryManager?) :
    BaseView(fwsContext) {

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView() {
        super.onCreateView()
        setContentView(R.layout.layout_history)
        contentView.findViewById<View>(R.id.back)
            .setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        val adapter = HistoryAdapter(context, historyManager?.all)
        val favoriteList = contentView.findViewById<RecyclerView>(R.id.list_view)
        favoriteList.addItemDecoration(
            DividerItemDecoration(
                context,
                DividerItemDecoration.VERTICAL
            )
        )
        favoriteList.setLayoutManager(LinearLayoutManager(context))
        favoriteList.setAdapter(adapter)
    }

}
