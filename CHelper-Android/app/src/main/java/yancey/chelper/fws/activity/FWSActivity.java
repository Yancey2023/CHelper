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

package yancey.chelper.fws.activity;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import yancey.chelper.fws.view.FWSMainView;
import yancey.chelper.fws.view.FWSView;

/**
 * 使用单个View组成的界面
 * 悬浮窗只能使用单个View显示界面，为了方便在悬浮窗模式和应用模式共享界面代码，所以设计了FWSActivity
 *
 * @param <T> View的内容
 */
public abstract class FWSActivity<T extends FWSView> extends AppCompatActivity {

    private FWSMainView<T> fwsMainView;

    protected abstract T createView(@NonNull FWSView.FWSContext fwsContext);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        fwsMainView = new FWSMainView<>(
                this,
                FWSView.Environment.APPLICATION,
                this::createView,
                getOnBackPressedDispatcher()
        );
        setContentView(fwsMainView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        fwsMainView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fwsMainView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        fwsMainView.onDestroy();
    }

}