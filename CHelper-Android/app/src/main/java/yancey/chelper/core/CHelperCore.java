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

package yancey.chelper.core;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hjq.toast.Toaster;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 软件的内核，与c++代码交互
 * 支持为不同的资源包同时创建多个内核实例
 */
public class CHelperCore implements Closeable {

    static {
        // 加载c++内核
        System.loadLibrary("CHelperAndroid");
    }

    /**
     * "旧命令转新命令"功能是否已经初始化
     */
    private static boolean isOld2NewInit = false;
    /**
     * 读取的资源包是否是
     */
    private final boolean isAssets;
    /**
     * 读取的资源包路径
     */
    private final String path;
    /**
     * c++内核的内存地址
     */
    private long pointer;

    /**
     * @param assetManager 软件内置资源管理器
     * @param path         资源包路径
     */
    private CHelperCore(@Nullable AssetManager assetManager, @NotNull String path) {
        this.isAssets = assetManager != null;
        this.path = path;
        try {
            pointer = create0(assetManager, path);
        } catch (Throwable e) {
            pointer = 0;
        }
        if (pointer == 0) {
            throw new RuntimeException("fail to init CHelper Core: " + path);
        }
    }

    /**
     * 从软件内置资源包加载内核
     *
     * @param assetManager 软件内置资源管理器
     * @param path         文件路径
     * @return 软件内核
     */
    public static CHelperCore fromAssets(@NotNull AssetManager assetManager, String path) {
        return new CHelperCore(Objects.requireNonNull(assetManager), path);
    }

    /**
     * 从文件加载内核
     *
     * @param path 文件路径
     * @return 软件内核
     */
    public static CHelperCore fromFile(String path) {
        return new CHelperCore(null, path);
    }

    /**
     * 文本改变时通知c++内核
     *
     * @param text  文本内容
     * @param index 光标位置
     */
    public void onTextChanged(@NonNull String text, int index) {
        if (pointer == 0) {
            return;
        }
        onTextChanged0(pointer, text, index);
    }

    /**
     * 光标改变时通知c++内核
     *
     * @param index 光标位置
     */
    public void onSelectionChanged(int index) {
        if (pointer == 0) {
            return;
        }
        onSelectionChanged0(pointer, index);
    }

    /**
     * 获取当前命令参数的介绍
     */
    public String getParamHint() {
        if (pointer == 0) {
            return null;
        }
        return getParamHint0(pointer);
    }

    /**
     * 获取当前命令的错误原因
     */
    public ErrorReason[] getErrorReasons() {
        if (pointer == 0) {
            return null;
        }
        return getErrorReasons0(pointer);
    }

    /**
     * 获取当前命令的补全提示数量
     */
    public int getSuggestionsSize() {
        if (pointer == 0) {
            return 0;
        }
        return getSuggestionsSize0(pointer);
    }

    /**
     * 获取当前命令其中一个补全提示
     *
     * @param which 第几个补全提示，从0开始
     */
    public Suggestion getSuggestion(int which) {
        if (pointer == 0) {
            return null;
        }
        return getSuggestion0(pointer, which);
    }

    /**
     * 获取当前命令的所有补全提示
     * 由于性能原因，不建议使用这个方法，建议按需获取
     *
     * @return 所有补全提示
     */
    public Suggestion[] getSuggestions() {
        if (pointer == 0) {
            return null;
        }
        return getSuggestions0(pointer);
    }

    /**
     * 获取当前命令的语法结构
     */
    public String getStructure() {
        if (pointer == 0) {
            return null;
        }
        return getStructure0(pointer);
    }

    /**
     * 补全提示被使用时通知c++内核
     *
     * @param which 第几个补全提示，从0开始
     */
    public ClickSuggestionResult onSuggestionClick(int which) {
        if (pointer == 0) {
            return null;
        }
        return onSuggestionClick0(pointer, which);
    }

    /**
     * 获取文本颜色
     *
     * @return 每个字符的类型
     */
    public int[] getSyntaxToken() {
        if (pointer == 0) {
            return null;
        }
        return getColors0(pointer);
    }

    /**
     * 关闭内核，释放内存
     */
    @Override
    public void close() {
        if (pointer == 0) {
            return;
        }
        release0(pointer);
        pointer = 0;
    }

    /**
     * 是否是软件内置的资源包
     *
     * @param context 上下文
     * @return old 旧命令
     */
    public static @NonNull String old2new(Context context, String old) {
        if (old == null) {
            return "";
        }
        if (!isOld2NewInit) {
            if (old2newInit0(context.getAssets(), "old2new/old2new.dat")) {
                isOld2NewInit = true;
            }
            if (!isOld2NewInit) {
                Toaster.show("旧版命令转新版命令初始化失败");
                return old;
            }
        }
        return Arrays.stream(old.split("\n"))
                .map(CHelperCore::old2new0)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 是否是软件内置的资源包
     *
     * @return 是否是软件内置的资源包
     */
    public boolean isAssets() {
        return isAssets;
    }

    /**
     * 获取当前使用的资源包路径
     *
     * @return 当前使用的资源包路径
     */
    public String getPath() {
        return path;
    }

    /**
     * 调用c++创建内核
     *
     * @param assetManager 软件内置资源管理器
     * @param cpackPath    资源包路径
     * @return 内核的内存地址
     */
    private static native long create0(@Nullable AssetManager assetManager, @NonNull String cpackPath);

    /**
     * 调用c++释放内核
     *
     * @param pointer 内核的内存地址
     */
    private static native void release0(long pointer);

    /**
     * 文本改变时通知c++内核
     *
     * @param pointer 内核的内存地址
     * @param text    文本内容
     * @param index   光标位置
     */
    private static native void onTextChanged0(long pointer, @NonNull String text, int index);

    /**
     * 光标改变时通知c++内核
     *
     * @param pointer 内核的内存地址
     * @param index   光标位置
     */
    private static native void onSelectionChanged0(long pointer, int index);

    /**
     * 获取当前命令参数的介绍
     *
     * @param pointer 内核的内存地址
     */
    private static native String getParamHint0(long pointer);

    /**
     * 获取当前命令的错误原因
     *
     * @param pointer 内核的内存地址
     */
    private static native ErrorReason[] getErrorReasons0(long pointer);

    /**
     * 获取当前命令的补全提示数量
     *
     * @param pointer 内核的内存地址
     */
    private static native int getSuggestionsSize0(long pointer);

    /**
     * 获取当前命令其中一个补全提示
     *
     * @param pointer 内核的内存地址
     * @param which   第几个补全提示，从0开始
     */
    private static native Suggestion getSuggestion0(long pointer, int which);

    /**
     * 获取当前命令的所有补全提示
     * 由于性能原因，不建议使用这个方法，建议按需获取
     *
     * @param pointer 第几个补全提示，从0开始
     * @return 所有补全提示
     */
    public static native Suggestion[] getSuggestions0(long pointer);

    /**
     * 获取当前命令的语法结构
     *
     * @param pointer 内核的内存地址
     */
    private static native String getStructure0(long pointer);

    /**
     * 补全提示被使用时通知c++内核
     *
     * @param pointer 内核的内存地址
     * @param which   第几个补全提示，从0开始
     */
    private static native ClickSuggestionResult onSuggestionClick0(long pointer, int which);

    /**
     * 获取文本颜色
     *
     * @param pointer 内核的内存地址
     * @return 每个字符的颜色
     */
    private static native int[] getColors0(long pointer);

    /**
     * 初始化"旧命令转新命令"功能
     *
     * @param assetManager 软件内置资源管理器
     * @param path         数据文件路径
     */
    private static native boolean old2newInit0(@NonNull AssetManager assetManager, @NonNull String path);

    /**
     * 旧命令转新命令
     * 使用前记得先初始化
     *
     * @param old 旧命令
     * @return 新命令
     */
    private static native String old2new0(@NonNull String old);


}
