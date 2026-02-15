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

package yancey.chelper.android.window.completion.adater;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hjq.toast.Toaster;

import java.util.List;

import yancey.chelper.R;
import yancey.chelper.android.common.util.ClipboardUtil;

/**
 * 历史列表适配器
 */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private final Context context;
    private final List<String> historyList;

    public HistoryAdapter(Context context, List<String> historyList) {
        this.context = context;
        this.historyList = historyList;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? 0 : 1;
    }

    @NonNull
    @Override
    public HistoryAdapter.HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new HistoryAdapter.HistoryViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_history_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryAdapter.HistoryViewHolder holder, int position) {
        String item = historyList.get(position);
        holder.mTv_content.setText(item);
        holder.mBtn_copy.setOnClickListener(view -> {
            if (ClipboardUtil.setText(context, item)) {
                Toaster.show("已复制");
            } else {
                Toaster.show("复制失败");
            }
        });
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    public static class HistoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView mTv_content;
        private final View mBtn_copy;

        public HistoryViewHolder(View itemView) {
            super(itemView);
            mTv_content = itemView.findViewById(R.id.content);
            mBtn_copy = itemView.findViewById(R.id.btn_copy);
        }
    }
}
