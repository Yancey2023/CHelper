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

package yancey.chelper.android.library.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.function.Consumer;

import yancey.chelper.R;
import yancey.chelper.network.library.data.LibraryFunction;

/**
 * 命令库列表适配器
 */
public class LocalLibraryListAdapter extends RecyclerView.Adapter<LocalLibraryListAdapter.CommandListViewHolder> {

    private final @NonNull Context context;
    private List<LibraryFunction> libraries;
    private final @NonNull Consumer<LibraryFunction> onLibraryShow;

    public LocalLibraryListAdapter(@NonNull Context context, @NonNull Consumer<LibraryFunction> onLibraryShow) {
        this.context = context;
        this.onLibraryShow = onLibraryShow;
    }

    @NonNull
    @Override
    public CommandListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CommandListViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_library_list_item_local, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull CommandListViewHolder holder, int position) {
        LibraryFunction libraryFunction = libraries.get(position);
        holder.mTv_name.setText(libraryFunction.getName());
        holder.mTv_description.setText(libraryFunction.getNote());
        holder.itemView.setOnClickListener(v -> onLibraryShow.accept(libraryFunction));
    }

    @Override
    public int getItemCount() {
        if (libraries == null) {
            return 0;
        }
        return libraries.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setLibraryFunctions(List<LibraryFunction> libraryNames) {
        this.libraries = libraryNames;
        notifyDataSetChanged();
    }

    public static class CommandListViewHolder extends RecyclerView.ViewHolder {
        private final View itemView;
        private final TextView mTv_name;
        private final TextView mTv_description;

        public CommandListViewHolder(View itemView) {
            super(itemView);
            this.itemView = itemView;
            mTv_name = itemView.findViewById(R.id.name);
            mTv_description = itemView.findViewById(R.id.description);
        }
    }
}
