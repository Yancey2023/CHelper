/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.android.completion.view;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import yancey.chelper.R;
import yancey.chelper.android.common.util.HistoryManager;
import yancey.chelper.android.common.view.BaseView;
import yancey.chelper.android.completion.adater.HistoryAdapter;

/**
 * 历史列表视图
 */
@SuppressLint("ViewConstructor")
public class HistoryView extends BaseView {

    @SuppressLint("NotifyDataSetChanged")
    public HistoryView(@NonNull FWSContext fwsContext, HistoryManager historyManager) {
        super(fwsContext, R.layout.layout_history);
        view.findViewById(R.id.back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        HistoryAdapter adapter = new HistoryAdapter(context, historyManager.getAll());
        RecyclerView rv_favoriteList = view.findViewById(R.id.list_view);
        rv_favoriteList.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));
        rv_favoriteList.setLayoutManager(new LinearLayoutManager(context));
        rv_favoriteList.setAdapter(adapter);
    }

}
