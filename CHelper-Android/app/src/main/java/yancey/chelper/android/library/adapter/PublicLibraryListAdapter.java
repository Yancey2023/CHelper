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
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.reactivex.rxjava3.disposables.Disposable;
import yancey.chelper.R;
import yancey.chelper.network.library.data.LibraryFunction;
import yancey.chelper.network.library.service.CommandLabPublicService;

/**
 * 命令库列表适配器
 */
public class PublicLibraryListAdapter extends RecyclerView.Adapter<PublicLibraryListAdapter.CommandListViewHolder> {

    private final @NonNull Context context;
    private List<LibraryFunction> libraries;
    private final @NonNull Consumer<LibraryFunction> onLibraryShow;
    private final @NonNull AtomicReference<Disposable> doLike;

    public PublicLibraryListAdapter(@NonNull Context context, @NonNull AtomicReference<Disposable> doLike, @NonNull Consumer<LibraryFunction> onLibraryShow) {
        this.context = context;
        this.doLike = doLike;
        this.onLibraryShow = onLibraryShow;
    }

    @NonNull
    @Override
    public CommandListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CommandListViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_library_list_item_public, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull CommandListViewHolder holder, int position) {
        LibraryFunction libraryFunction = libraries.get(position);
        holder.mTv_name.setText(libraryFunction.getName());
        holder.mTv_author.setText(context.getString(R.string.layout_library_list_library_author_formatter, libraryFunction.getAuthor()));
        if (Boolean.TRUE.equals(libraryFunction.is_liked())) {
            holder.mBtn_like.setBackgroundResource(R.drawable.heart_filled);
        } else {
            holder.mBtn_like.setBackgroundResource(R.drawable.heart);
        }
        holder.mBtn_like.setContentDescription(context.getString(R.string.common_icon_like_content_description));
        if (libraryFunction.getId() != null) {
            holder.mBtn_like.setOnClickListener(v -> doLike(position));
        }
        holder.mTv_likeCount.setVisibility(View.VISIBLE);
        holder.mTv_likeCount.setText(String.valueOf(Objects.requireNonNullElse(libraryFunction.getLike_count(), 0)));
        holder.itemView.setOnClickListener(v -> onLibraryShow.accept(libraryFunction));
    }

    @SuppressLint("HardwareIds")
    void doLike(int position) {
        Disposable disposable = doLike.get();
        if (disposable != null) {
            disposable.dispose();
        }
        LibraryFunction libraryFunction = libraries.get(position);
        CommandLabPublicService.LikeFunctionRequest request = new CommandLabPublicService.LikeFunctionRequest();
        request.setAndroid_id(Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID));
//        disposable = ServiceManager.COMMAND_LAB_PUBLIC_SERVICE
//                .like(Objects.requireNonNull(libraryFunction.getId()), request)
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(result2 -> {
//                    if (!Objects.equals(result2.status, "success") || result2.data == null) {
//                        Toaster.show(result2.message);
//                        return;
//                    }
//                    CommandLabPublicService.LibraryLikeResponse libraryLikeState = result2.data;
//                    libraryFunction.is_liked = !Objects.equals(libraryLikeState.action, "unlike");
//                    libraryFunction.like_count = libraryLikeState.like_count;
//                    notifyItemChanged(position);
//                }, throwable -> Toaster.show(throwable.getMessage()));
        doLike.set(disposable);
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
        private final TextView mTv_author;
        private final View mBtn_like;
        private final TextView mTv_likeCount;

        public CommandListViewHolder(View itemView) {
            super(itemView);
            this.itemView = itemView;
            mTv_name = itemView.findViewById(R.id.name);
            mTv_author = itemView.findViewById(R.id.author);
            mBtn_like = itemView.findViewById(R.id.btn_like);
            mTv_likeCount = itemView.findViewById(R.id.like_count);
        }
    }
}
