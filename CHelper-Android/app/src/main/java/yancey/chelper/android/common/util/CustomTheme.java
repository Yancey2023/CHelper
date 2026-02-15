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

package yancey.chelper.android.common.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import yancey.chelper.R;

public class CustomTheme {

    private static final String TAG = "CustomTheme";

    public static CustomTheme INSTANCE;

    @NonNull
    private final File file;
    @NotNull
    public final BitmapResizeCache backgroundBitmap = new BitmapResizeCache();

    public CustomTheme(@NonNull File file) {
        this.file = file;
        if (file.exists()) {
            try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
                this.backgroundBitmap.setBitmap(BitmapFactory.decodeStream(inputStream));
            } catch (IOException e) {
                Log.w(TAG, "fail to load background drawable", e);
                MonitorUtil.generateCustomLog(e, "IOException");
            }
        }
    }

    public int invokeBackground(@Nullable View view, int lastUpdateTimes) {
        int updateTimes = backgroundBitmap.getUpdateTimes();
        if (lastUpdateTimes == updateTimes) {
            return updateTimes;
        }
        if (view == null) {
            Log.w(TAG, "fail to draw background beacause view is null");
            return updateTimes;
        }
        if (backgroundBitmap.isSourceFileMiss()) {
            view.setBackgroundResource(R.color.background);
            return updateTimes;
        }
        if (view.getWidth() == 0 || view.getHeight() == 0) {
            view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    invokeBackground0(view);
                    view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
            return updateTimes;
        }
        invokeBackground0(view);
        return updateTimes;
    }

    public int invokeBackgroundForce(@Nullable View view) {
        return invokeBackground(view, 0);
    }

    private void invokeBackground0(@NonNull View view) {
        if (backgroundBitmap.isSourceFileMiss()) {
            view.setBackgroundResource(R.color.background);
            return;
        }
        int targetWidth = view.getWidth();
        int targetHeight = view.getHeight();
        if (targetWidth == 0 || targetHeight == 0) {
            Log.w(TAG, "fail to draw background beacause view is not ready");
            return;
        }
        view.setBackground(new BitmapDrawable(view.getResources(), backgroundBitmap.getBitmap(targetWidth, targetHeight)));
    }

    public void setBackGroundDrawableWithoutSave(@Nullable Bitmap bitmap) {
        this.backgroundBitmap.setBitmap(bitmap);
    }

    public void setBackGroundDrawable(@Nullable Bitmap bitmap) throws IOException {
        this.backgroundBitmap.setBitmap(bitmap);
        if (bitmap == null) {
            if (file.exists() && !file.delete()) {
                throw new IOException("fail to delete file");
            }
            return;
        }
        if (!FileUtil.createParentFile(file)) {
            throw new IOException("fail to create parent directory");
        }
        try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(file))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }

    public static void init(File file) {
        INSTANCE = new CustomTheme(new File(file, "background.png"));
    }

    public static void refreshTheme() {
        switch (Settings.INSTANCE.themeId) {
            case "MODE_NIGHT_NO" ->
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            case "MODE_NIGHT_YES" ->
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            case "MODE_NIGHT_FOLLOW_SYSTEM" ->
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            default -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                Settings.INSTANCE.themeId = "MODE_NIGHT_FOLLOW_SYSTEM";
                Settings.INSTANCE.save();
            }
        }
    }

}
