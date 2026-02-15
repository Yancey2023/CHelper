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

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;

import java.util.function.Function;

@SuppressLint("ViewConstructor")
public class FWSMainView<T extends FWSView> extends FrameLayout {

    private final @NonNull FWSView.Environment environment;
    private final @NonNull OnBackPressedCallback onBackPressedCallback;

    public FWSMainView(
            @NonNull Context context,
            @NonNull FWSView.Environment environment,
            @NonNull Function<FWSView.FWSContext, T> createRootView,
            @NonNull OnBackPressedDispatcher onBackPressedDispatcher
    ) {
        super(context);
        this.environment = environment;
        onBackPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                backView();
                if (!onBackPressedCallback.isEnabled()) {
                    onBackPressedDispatcher.onBackPressed();
                }
            }
        };
        onBackPressedDispatcher.addCallback(onBackPressedCallback);
        openView(createRootView.apply(new FWSView.FWSContext(context, this::openView, onBackPressedDispatcher, environment)));
    }

    public FWSView getChildAt(int index) {
        return (FWSView) super.getChildAt(index);
    }

    public void openView(@NonNull FWSView view) {
        int childCount = getChildCount();
        if (!onBackPressedCallback.isEnabled() && childCount > 0) {
            throw new RuntimeException("you can't open a view when floating window is pause");
        }
        ((InputMethodManager) getContext().getSystemService(Service.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(getWindowToken(), 0);
        addView(view);
        if (childCount >= 1) {
            getChildAt(childCount - 1).onPause();
            view.onResume();
        }
        if (childCount > 0) {
            if (environment == FWSView.Environment.FLOATING_WINDOW) {
                view.requestFocus();
            } else {
                clearFocus();
            }
        }
    }

    private void backView() {
        ((InputMethodManager) getContext().getSystemService(Service.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(getWindowToken(), 0);
        int childCount = getChildCount();
        FWSView currentView = getChildAt(childCount - 1);
        OnBackPressedDispatcher onBackPressedDispatcher = currentView.getOnBackPressedDispatcher();
        if (onBackPressedDispatcher.hasEnabledCallbacks()) {
            onBackPressedDispatcher.onBackPressed();
            return;
        }
        if (childCount <= 1) {
            onBackPressedCallback.setEnabled(false);
            return;
        }
        currentView.onPause();
        currentView.onDestroy();
        FWSView backView = getChildAt(childCount - 2);
        backView.onResume();
        removeViewAt(childCount - 1);
    }

    public void onPause() {
        onBackPressedCallback.setEnabled(false);
        getChildAt(getChildCount() - 1).onPause();
        clearFocus();
    }

    public void onResume() {
        onBackPressedCallback.setEnabled(true);
        getChildAt(getChildCount() - 1).onResume();
        if (environment == FWSView.Environment.FLOATING_WINDOW) {
            getChildAt(getChildCount() - 1).requestFocus();
        } else {
            clearFocus();
        }
    }

    public void onDestroy() {
        clearFocus();
        int childCount = getChildCount();
        if (childCount == 0) {
            return;
        }
        FWSView lastView = getChildAt(childCount - 1);
        lastView.onDestroy();
        for (int i = childCount - 2; i >= 0; i--) {
            if (i != childCount - 1) {
                getChildAt(i).onPause();
            }
            getChildAt(i).onDestroy();
        }
        removeAllViews();
    }

}
