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

import android.app.Application;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 软件设置
 */
public class Settings {

    /**
     * 默认命令分支
     */
    public static final String DEFAULT_CPACK = "release-experiment";
    /**
     * 配置文件路径
     */
    private static File file;
    /**
     * 设置实例
     */
    public static Settings INSTANCE;
    /**
     * 是否启用更新提示
     */
    public Boolean isEnableUpdateNotifications;
    /**
     * 当前主题
     */
    public String themeId;
    /**
     * 悬浮窗透明度
     */
    public Float floatingWindowAlpha;
    /**
     * 悬浮窗大小
     */
    public Integer floatingWindowSize;
    /**
     * 根据光标位置提供补全提示
     */
    public Boolean isCheckingBySelection;
    /**
     * 复制后隐藏悬浮窗
     */
    public Boolean isHideWindowWhenCopying;
    /**
     * 是否在进入界面时保留上次输入的内容
     */
    public Boolean isSavingWhenPausing;
    /**
     * 紧凑模式
     */
    public Boolean isCrowed;
    /**
     * 是否显示命令错误原因
     */
    public Boolean isShowErrorReason;
    /**
     * 语法高亮
     */
    public Boolean isSyntaxHighlight;
    /**
     * 选择的资源包分支
     */
    private String cpackPath;
    /**
     * 资源包的信息
     */
    public static String pathReleaseVanilla;
    public static String pathReleaseExperiment;
    public static String pathBetaVanilla;
    public static String pathBetaExperiment;
    public static String pathNeteaseVanilla;
    public static String pathNeteaseExperiment;
    public static String versionReleaseVanilla;
    public static String versionReleaseExperiment;
    public static String versionBetaVanilla;
    public static String versionBetaExperiment;
    public static String versionNeteaseVanilla;

    public static String versionNeteaseExperiment;

    /**
     * 自定义命令库地址（测试用）
     */

    public String apiUrl;

    /**
     * 是否显示公有命令库入口
     */
    public Boolean isShowPublicLibrary;

    /**
     * 请求速率限制（次/秒）
     */
    public Integer requestRateLimit;

    public void setCpackBranch(String cpackBranch) {
        this.cpackPath = cpackBranch;
    }

    public String getCpackPath() {
        return switch (cpackPath) {
            case "release-vanilla" -> pathReleaseVanilla;
            case "release-experiment" -> pathReleaseExperiment;
            case "beta-vanilla" -> pathBetaVanilla;
            case "beta-experiment" -> pathBetaExperiment;
            case "netease-vanilla" -> pathNeteaseVanilla;
            case "netease-experiment" -> pathNeteaseExperiment;
            default -> throw new RuntimeException("Invalid cpack branch");
        };
    }

    public String getCpackBranch() {
        return cpackPath;
    }

    /**
     * 保存设置到文件
     */
    public void save() {
        FileUtil.writeString(file, new Gson().toJson(this));
    }

    /**
     * 初始化
     *
     * @param application 应用
     * @param file        配置文件路径
     * @param onError     错误处理
     */
    public static void init(Application application, File file, Consumer<Throwable> onError) {
        Settings.file = file;
        if (file.exists()) {
            try {
                INSTANCE = new Gson().fromJson(FileUtil.readString(file), Settings.class);
            } catch (Throwable throwable) {
                onError.accept(throwable);
            }
        }
        boolean isDirty = false;
        if (INSTANCE == null) {
            INSTANCE = new Settings();
            isDirty = true;
        }
        if (INSTANCE.isShowPublicLibrary == null) {
            INSTANCE.isShowPublicLibrary = false;
            isDirty = true;
        }
        if (INSTANCE.requestRateLimit == null) {
            INSTANCE.requestRateLimit = 2;
            isDirty = true;
        }
        if (INSTANCE.isEnableUpdateNotifications == null) {
            INSTANCE.isEnableUpdateNotifications = true;
            isDirty = true;
        }
        if (INSTANCE.themeId == null) {
            INSTANCE.themeId = "MODE_NIGHT_FOLLOW_SYSTEM";
            isDirty = true;
        }
        if (INSTANCE.floatingWindowAlpha == null) {
            INSTANCE.floatingWindowAlpha = 1.0f;
            isDirty = true;
        }
        if (INSTANCE.floatingWindowSize == null) {
            INSTANCE.floatingWindowSize = 40;
            isDirty = true;
        }
        if (INSTANCE.isCheckingBySelection == null) {
            INSTANCE.isCheckingBySelection = true;
            isDirty = true;
        }
        if (INSTANCE.isHideWindowWhenCopying == null) {
            INSTANCE.isHideWindowWhenCopying = false;
            isDirty = true;
        }
        if (INSTANCE.isSavingWhenPausing == null) {
            INSTANCE.isSavingWhenPausing = true;
            isDirty = true;
        }
        if (INSTANCE.isCrowed == null) {
            INSTANCE.isCrowed = false;
            isDirty = true;
        }
        if (INSTANCE.isShowErrorReason == null) {
            INSTANCE.isShowErrorReason = true;
            isDirty = true;
        }
        if (INSTANCE.isSyntaxHighlight == null) {
            INSTANCE.isSyntaxHighlight = true;
            isDirty = true;
        }
        if (INSTANCE.cpackPath == null ||
            !(INSTANCE.cpackPath.equals("release-vanilla") ||
              INSTANCE.cpackPath.equals("release-experiment") ||
              INSTANCE.cpackPath.equals("beta-vanilla") ||
              INSTANCE.cpackPath.equals("beta-experiment") ||
              INSTANCE.cpackPath.equals("netease-vanilla") ||
              INSTANCE.cpackPath.equals("netease-experiment"))) {
            INSTANCE.cpackPath = DEFAULT_CPACK;
            isDirty = true;
        }
        if (isDirty) {
            INSTANCE.save();
        }
        try {
            for (String filename : Objects.requireNonNull(application.getAssets().list("cpack"))) {
                if (filename.startsWith("release-vanilla")) {
                    pathReleaseVanilla = "cpack/" + filename;
                    versionReleaseVanilla = filename.substring("release-vanilla-".length(),
                            filename.length() - ".cpack".length());
                }
                if (filename.startsWith("release-experiment")) {
                    pathReleaseExperiment = "cpack/" + filename;
                    versionReleaseExperiment = filename.substring("release-experiment-".length(),
                            filename.length() - ".cpack".length());
                }
                if (filename.startsWith("beta-vanilla")) {
                    pathBetaVanilla = "cpack/" + filename;
                    versionBetaVanilla = filename.substring("beta-vanilla-".length(),
                            filename.length() - ".cpack".length());
                }
                if (filename.startsWith("beta-experiment")) {
                    pathBetaExperiment = "cpack/" + filename;
                    versionBetaExperiment = filename.substring("beta-experiment-".length(),
                            filename.length() - ".cpack".length());
                }
                if (filename.startsWith("netease-vanilla")) {
                    pathNeteaseVanilla = "cpack/" + filename;
                    versionNeteaseVanilla = filename.substring("netease-vanilla-".length(),
                            filename.length() - ".cpack".length());
                }
                if (filename.startsWith("netease-experiment")) {
                    pathNeteaseExperiment = "cpack/" + filename;
                    versionNeteaseExperiment = filename.substring("netease-experiment-".length(),
                            filename.length() - ".cpack".length());
                }
            }
            Objects.requireNonNull(pathReleaseVanilla);
            Objects.requireNonNull(pathReleaseExperiment);
            Objects.requireNonNull(pathBetaVanilla);
            Objects.requireNonNull(pathBetaExperiment);
            Objects.requireNonNull(pathNeteaseVanilla);
            Objects.requireNonNull(pathNeteaseExperiment);
            Objects.requireNonNull(versionReleaseExperiment);
            Objects.requireNonNull(versionBetaVanilla);
            Objects.requireNonNull(versionBetaExperiment);
            Objects.requireNonNull(versionNeteaseVanilla);
            Objects.requireNonNull(versionReleaseVanilla);
            Objects.requireNonNull(versionNeteaseExperiment);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        CustomTheme.refreshTheme();
    }

}
