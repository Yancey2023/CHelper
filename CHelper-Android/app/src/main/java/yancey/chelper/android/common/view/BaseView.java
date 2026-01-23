/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2025  Yancey
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

package yancey.chelper.android.common.view;

import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import yancey.chelper.R;
import yancey.chelper.android.common.style.CustomTheme;
import yancey.chelper.fws.view.FWSView;

/**
 * 作为FWSView的基类，提供通用的代码
 */
public abstract class BaseView extends FWSView {

    /**
     * @param fwsContext 自定义上下文
     * @param layoutId   视图界面ID
     */
    public BaseView(
            @NonNull FWSContext fwsContext,
            @LayoutRes int layoutId
    ) {
        super(fwsContext, layoutId);
        View mainView = view.findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets stateBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
                v.setPadding(stateBars.left, stateBars.top, stateBars.right, stateBars.bottom);
                return insets;
            });
            mainView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    backgroundUpdateTimes = CustomTheme.INSTANCE.invokeBackgroundForce(mainView);
                }
            });
        }
    }

    /**
     * 界面恢复前台事件
     */
    public void onResume() {
        super.onResume();
        // 支持自定义背景
        View mainView = view.findViewById(R.id.main);
        backgroundUpdateTimes = CustomTheme.INSTANCE.invokeBackground(mainView, backgroundUpdateTimes);
        WindowInsetsCompat rootWindowInsets = ViewCompat.getRootWindowInsets(mainView);
        if (rootWindowInsets != null) {
            Insets stateBars = rootWindowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            mainView.setPadding(stateBars.left, stateBars.top, stateBars.right, stateBars.bottom);
        }
        mainView.post(() -> {
            WindowInsetsCompat rootWindowInsets1 = ViewCompat.getRootWindowInsets(mainView);
            if (rootWindowInsets1 != null) {
                Insets stateBars = rootWindowInsets1.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
                mainView.setPadding(stateBars.left, stateBars.top, stateBars.right, stateBars.bottom);
            }
        });
    }

}
