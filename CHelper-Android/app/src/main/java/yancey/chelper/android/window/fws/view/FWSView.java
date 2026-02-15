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

package yancey.chelper.android.window.fws.view;

import android.content.Context;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 使用单个View组成的界面
 * 悬浮窗只能使用单个View显示界面，为了方便在悬浮窗模式和应用模式共享界面代码，所以设计了FWSView
 * 相比于普通的View，它有完整的声明周期监听事件
 */
public abstract class FWSView extends FrameLayout {

    /**
     * 运行环境：应用 / 悬浮窗
     */
    public enum Environment {
        APPLICATION, FLOATING_WINDOW
    }


    public static class FWSContext {

        private final @NonNull Context context;
        private final @NonNull Consumer<FWSView> openView;
        private final @NonNull OnBackPressedDispatcher onBackPressedDispatcher;
        private final @NonNull Environment environment;

        /**
         * 自定义上下文
         *
         * @param context                 上下文
         * @param openView                打开新界面的相关代码
         * @param onBackPressedDispatcher 返回事件分发器
         * @param environment             运行环境：应用 / 悬浮窗
         */
        public FWSContext(
                @NonNull Context context,
                @NonNull Consumer<FWSView> openView,
                @NonNull OnBackPressedDispatcher onBackPressedDispatcher,
                @NonNull Environment environment
        ) {
            this.context = context;
            this.openView = openView;
            this.onBackPressedDispatcher = onBackPressedDispatcher;
            this.environment = environment;
        }
    }

    private final @NonNull FWSContext fwsContext;
    protected final @NonNull Context context;
    protected final @NonNull View view;
    private final @NonNull OnBackPressedDispatcher onBackPressedDispatcher;
    protected int backgroundUpdateTimes = 0;
    private final @LayoutRes int layoutId;

    /**
     * @param fwsContext 自定义上下文
     * @param layoutId   视图界面ID
     */
    public FWSView(
            @NonNull FWSContext fwsContext,
            @LayoutRes int layoutId
    ) {
        super(fwsContext.context);
        this.fwsContext = fwsContext;
        this.context = fwsContext.context;
        this.layoutId = layoutId;
        this.view = getView();
        addView(view);
        onBackPressedDispatcher = new OnBackPressedDispatcher(fwsContext.onBackPressedDispatcher::onBackPressed);
    }

    protected View getView() {
        return LayoutInflater.from(fwsContext.context).inflate(layoutId, this, false);
    }

    /**
     * 界面切换后台事件
     */
    public void onPause() {

    }

    /**
     * 界面恢复前台事件
     */
    public void onResume() {

    }

    /**
     * 界面关闭事件
     */
    public void onDestroy() {

    }

    /**
     * 打开新界面
     *
     * @param createView 新界面的创建方法，可以使用lambda表达式提供
     */
    protected void openView(@NonNull Function<FWSContext, FWSView> createView) {
        // 打开界面
        fwsContext.openView.accept(createView.apply(fwsContext));
    }

    /**
     * 获取返回事件分发器
     *
     * @return 获取返回事件分发器
     */
    @NonNull
    public OnBackPressedDispatcher getOnBackPressedDispatcher() {
        return onBackPressedDispatcher;
    }

    /**
     * 获取运行环境
     *
     * @return 运行环境：应用 / 悬浮窗
     */
    public Environment getEnvironment() {
        return fwsContext.environment;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        super.dispatchTouchEvent(motionEvent);
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (getEnvironment() == Environment.FLOATING_WINDOW && event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            onBackPressedDispatcher.onBackPressed();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

}
