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

package yancey.chelper.android.common.dialog;

import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import yancey.chelper.R;

/**
 * 是否确认的对话框
 */
public class IsConfirmDialog extends FixedDialog {

    // 是否为大型对话框
    private final boolean isBig;
    // 标题，内容，取消按钮文本，确认按钮文本
    @Nullable
    private String title, message, confirm, cancel;
    // 确认按钮按下后的事件，取消按钮按下后的事件，对话框消失的事件
    @Nullable
    private Runnable onConfirm, onCancel, onDismiss;

    /**
     * @param context 上下文
     */
    public IsConfirmDialog(@NonNull Context context) {
        this(context, false);
    }

    /**
     * @param context 上下文
     * @param isBig   是否为大型对话框
     */
    public IsConfirmDialog(@NonNull Context context, boolean isBig) {
        super(context);
        this.isBig = isBig;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isBig) {
            setContentView(R.layout.dialog_is_confirm_big);
        } else {
            setContentView(R.layout.dialog_is_confirm);
        }
        TextView tv_title = findViewById(R.id.title);
        TextView tv_message = findViewById(R.id.message);
        TextView btn_confirm = findViewById(R.id.confirm);
        TextView btn_cancel = findViewById(R.id.cancel);
        Objects.requireNonNull(tv_title);
        Objects.requireNonNull(tv_message);
        Objects.requireNonNull(btn_confirm);
        Objects.requireNonNull(btn_cancel);
        tv_title.setText(Objects.requireNonNullElse(title, getContext().getString(R.string.dialog_is_confirm_title)));
        tv_message.setText(message);
        btn_confirm.setText(Objects.requireNonNullElse(confirm, getContext().getString(R.string.dialog_is_confirm_confirm)));
        btn_cancel.setText(Objects.requireNonNullElse(cancel, getContext().getString(R.string.dialog_is_confirm_cancel)));
        btn_confirm.setOnClickListener(view -> {
            if (onConfirm != null) {
                onConfirm.run();
            }
            dismiss();
        });
        btn_cancel.setOnClickListener(view -> {
            if (onCancel != null) {
                onCancel.run();
            }
            dismiss();
        });
        setOnDismissListener(dialog -> {
            if (onDismiss != null) {
                onDismiss.run();
            }
        });
    }

    public IsConfirmDialog title(String title) {
        this.title = title;
        return this;
    }

    public IsConfirmDialog message(String message) {
        this.message = message;
        return this;
    }

    public IsConfirmDialog onConfirm(String confirm) {
        this.confirm = confirm;
        return this;
    }

    public IsConfirmDialog onConfirm(Runnable onConfirm) {
        this.onConfirm = onConfirm;
        return this;
    }

    public IsConfirmDialog onConfirm(String confirm, Runnable onConfirm) {
        return onConfirm(confirm).onConfirm(onConfirm);
    }

    public IsConfirmDialog onCancel(String cancel) {
        this.cancel = cancel;
        return this;
    }

    public IsConfirmDialog onCancel(Runnable onCancel) {
        this.onCancel = onCancel;
        return this;
    }

    public IsConfirmDialog onCancel(String cancel, Runnable onCancel) {
        return onCancel(cancel).onCancel(onCancel);
    }

    public IsConfirmDialog onDismiss(Runnable onDismiss) {
        this.onDismiss = onDismiss;
        return this;
    }

}
