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

package yancey.chelper.android.completion.adater;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import yancey.chelper.R;
import yancey.chelper.core.CHelperGuiCore;
import yancey.chelper.core.Suggestion;

/**
 * 补全提示列表适配器
 */
public class SuggestionListAdapter extends RecyclerView.Adapter<SuggestionListAdapter.CommandListViewHolder> {

    private final Context context;
    private final CHelperGuiCore core;
    private final boolean isCrowed;
    private String structure, paramHint, errorReasons;

    public SuggestionListAdapter(Context context, CHelperGuiCore core, boolean isCrowed) {
        this.context = context;
        this.core = core;
        this.isCrowed = isCrowed;
        this.structure = context.getString(R.string.layout_completion_welcome);
        this.paramHint = context.getString(R.string.layout_completion_author);
    }

    public void setStructure(String structure) {
        this.structure = structure;
    }

    public void setParamHint(String paramHint) {
        this.paramHint = paramHint;
    }

    public void setErrorReasons(String errorReasons) {
        this.errorReasons = errorReasons;
    }

    @Override
    public int getItemViewType(int position) {
        if (!isCrowed) {
            return 0;
        } else if (position > 0) {
            return 1;
        } else {
            return 2;
        }
    }

    @NonNull
    @Override
    public CommandListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CommandListViewHolder(LayoutInflater.from(context).inflate(switch (viewType) {
            case 0 -> R.layout.layout_completion_suggestion;
            case 1 -> R.layout.layout_completion_crowded_suggestion;
            case 2 -> R.layout.layout_completion_crowded_header;
            default -> throw new IllegalStateException("Unexpected value: " + viewType);
        }, parent, false));
    }

    @Override
    @SuppressLint("SetTextI18n")
    public void onBindViewHolder(@NonNull CommandListViewHolder holder, int position) {
        if (isCrowed && position == 0) {
            holder.structure.setText(structure);
            holder.paramHint.setText(paramHint);
            if (errorReasons == null) {
                holder.errorReasons.setVisibility(View.GONE);
            } else {
                holder.errorReasons.setVisibility(View.VISIBLE);
                holder.errorReasons.setText(errorReasons);
            }
            return;
        }
        Suggestion data = core.getSuggestion(isCrowed ? position - 1 : position);
        if (data == null) {
            holder.content.setText(null);
            if (holder.description != null) {
                holder.description.setText(null);
            }
            holder.itemView.setOnClickListener(null);
        } else {
            if (holder.description == null) {
                holder.content.setText(data.description != null ? data.name + " - " + data.description : data.name);
            } else {
                holder.content.setText(data.name);
                holder.description.setText(data.description);
            }
            holder.itemView.setOnClickListener(view -> core.onItemClick(isCrowed ? position - 1 : position));
        }
    }

    @Override
    public int getItemCount() {
        return isCrowed ? core.getSuggestionsSize() + 1 : core.getSuggestionsSize();
    }

    public static class CommandListViewHolder extends RecyclerView.ViewHolder {
        private final View itemView;
        private final TextView content, description, structure, paramHint, errorReasons;

        public CommandListViewHolder(View itemView) {
            super(itemView);
            this.itemView = itemView;
            this.content = itemView.findViewById(R.id.content);
            this.description = itemView.findViewById(R.id.description);
            this.structure = itemView.findViewById(R.id.structure);
            this.paramHint = itemView.findViewById(R.id.param_hint);
            this.errorReasons = itemView.findViewById(R.id.error_reasons);
        }
    }
}
