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
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;

import java.util.Objects;

/**
 * 因为在界面中定义的宽度无效，所以在代码中把宽度设置为0.95倍屏幕宽度，并把背景设置透明
 */
public abstract class FixedDialog extends AppCompatDialog {

    @SuppressWarnings("unused")
    public FixedDialog(@NonNull Context context) {
        super(context);
    }

    @SuppressWarnings("unused")
    public FixedDialog(@NonNull Context context, int theme) {
        super(context, theme);
    }

    @SuppressWarnings("unused")
    public FixedDialog(@NonNull Context context, boolean cancelable, @Nullable OnCancelListener cancelListener) {
        super(context, cancelable, cancelListener);
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);
        fixWidth();
    }

    @Override
    public void setContentView(@NonNull View view) {
        super.setContentView(view);
        fixWidth();
    }

    @Override
    public void setContentView(@NonNull View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        fixWidth();
    }

    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    private void fixWidth() {
        Window window = Objects.requireNonNull(getWindow());
        window.setBackgroundDrawableResource(android.R.color.transparent);
        WindowManager.LayoutParams attributes = window.getAttributes();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect size = window.getWindowManager().getCurrentWindowMetrics().getBounds();
            attributes.width = (int) (size.width() * 0.9);
        } else {
            Point point = new Point();
            window.getWindowManager().getDefaultDisplay().getSize(point);
            attributes.width = (int) (((double) point.x) * 0.9d);
        }
        window.setAttributes(attributes);
    }

}
