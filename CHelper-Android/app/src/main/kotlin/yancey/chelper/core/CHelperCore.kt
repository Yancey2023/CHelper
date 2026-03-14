/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.core

import android.content.Context
import android.content.res.AssetManager
import com.hjq.toast.Toaster
import java.io.Closeable

/**
 * 软件的内核，与c++代码交互
 * 支持为不同的资源包同时创建多个内核实例
 */
class CHelperCore private constructor(
    assetManager: AssetManager?,
    val path: String
) : Closeable {
    /**
     * 读取的资源包是否是软件内置的资源包
     */
    val isAssets: Boolean = assetManager != null

    /**
     * c++内核的内存地址
     */
    private var pointer: Long = 0

    /**
     * @param assetManager 软件内置资源管理器
     * @param path         资源包路径
     */
    init {
        pointer = try {
            create0(assetManager, path)
        } catch (_: Throwable) {
            0
        }
        if (pointer == 0L) {
            throw RuntimeException("fail to init CHelper Core: $path")
        }
    }

    /**
     * 文本改变时通知c++内核
     * 
     * @param text  文本内容
     * @param index 光标位置
     */
    fun onTextChanged(text: String, index: Int) {
        if (pointer == 0L) {
            return
        }
        onTextChanged0(pointer, text, index)
    }

    /**
     * 光标改变时通知c++内核
     * 
     * @param index 光标位置
     */
    fun onSelectionChanged(index: Int) {
        if (pointer == 0L) {
            return
        }
        onSelectionChanged0(pointer, index)
    }

    val paramHint: String?
        /**
         * 获取当前命令参数的介绍
         */
        get() {
            if (pointer == 0L) {
                return null
            }
            return getParamHint0(pointer)
        }

    val errorReasons: Array<ErrorReason>?
        /**
         * 获取当前命令的错误原因
         */
        get() {
            if (pointer == 0L) {
                return null
            }
            return getErrorReasons0(pointer)
        }

    val suggestionsSize: Int
        /**
         * 获取当前命令的补全提示数量
         */
        get() {
            if (pointer == 0L) {
                return 0
            }
            return getSuggestionsSize0(pointer)
        }

    /**
     * 获取当前命令其中一个补全提示
     * 
     * @param which 第几个补全提示，从0开始
     */
    fun getSuggestion(which: Int): Suggestion? {
        if (pointer == 0L) {
            return null
        }
        return getSuggestion0(pointer, which)
    }

    val suggestions: Array<Suggestion?>?
        /**
         * 获取当前命令的所有补全提示
         * 由于性能原因，不建议使用这个方法，建议按需获取
         * 
         * @return 所有补全提示
         */
        get() {
            if (pointer == 0L) {
                return null
            }
            return getSuggestions0(pointer)
        }

    val structure: String?
        /**
         * 获取当前命令的语法结构
         */
        get() {
            if (pointer == 0L) {
                return null
            }
            return getStructure0(pointer)
        }

    /**
     * 补全提示被使用时通知c++内核
     * 
     * @param which 第几个补全提示，从0开始
     */
    fun onSuggestionClick(which: Int): ClickSuggestionResult? {
        if (pointer == 0L) {
            return null
        }
        return onSuggestionClick0(pointer, which)
    }

    val syntaxToken: IntArray?
        /**
         * 获取文本颜色
         * 
         * @return 每个字符的类型
         */
        get() {
            if (pointer == 0L) {
                return null
            }
            return getColors0(pointer)
        }

    /**
     * 关闭内核，释放内存
     */
    override fun close() {
        if (pointer == 0L) {
            return
        }
        release0(pointer)
        pointer = 0
    }

    companion object {
        init {
            // 加载c++内核
            System.loadLibrary("CHelperAndroid")
        }

        /**
         * "旧命令转新命令"功能是否已经初始化
         */
        private var isOld2NewInit = false

        /**
         * 从软件内置资源包加载内核
         * 
         * @param assetManager 软件内置资源管理器
         * @param path         文件路径
         * @return 软件内核
         */
        fun fromAssets(assetManager: AssetManager, path: String): CHelperCore {
            return CHelperCore(assetManager, path)
        }

        /**
         * 从文件加载内核
         * 
         * @param path 文件路径
         * @return 软件内核
         */
        fun fromFile(path: String): CHelperCore {
            return CHelperCore(null, path)
        }

        /**
         * 是否是软件内置的资源包
         * 
         * @param context 上下文
         * @return old 旧命令
         */
        fun old2new(context: Context, old: String?): String {
            if (old == null) {
                return ""
            }
            if (!isOld2NewInit) {
                if (old2newInit0(context.assets, "old2new/old2new.dat")) {
                    isOld2NewInit = true
                }
                if (!isOld2NewInit) {
                    Toaster.show("旧版命令转新版命令初始化失败")
                    return old
                }
            }
            return old.split("\n")
                .map { old2new0(it) }
                .filter { !it.isNullOrEmpty() }
                .joinToString("\n")
        }

        /**
         * 调用c++创建内核
         * 
         * @param assetManager 软件内置资源管理器
         * @param cpackPath    资源包路径
         * @return 内核的内存地址
         */
        @JvmStatic
        private external fun create0(assetManager: AssetManager?, cpackPath: String): Long

        /**
         * 调用c++释放内核
         * 
         * @param pointer 内核的内存地址
         */
        @JvmStatic
        private external fun release0(pointer: Long)

        /**
         * 文本改变时通知c++内核
         * 
         * @param pointer 内核的内存地址
         * @param text    文本内容
         * @param index   光标位置
         */
        @JvmStatic
        private external fun onTextChanged0(pointer: Long, text: String, index: Int)

        /**
         * 光标改变时通知c++内核
         * 
         * @param pointer 内核的内存地址
         * @param index   光标位置
         */
        @JvmStatic
        private external fun onSelectionChanged0(pointer: Long, index: Int)

        /**
         * 获取当前命令参数的介绍
         * 
         * @param pointer 内核的内存地址
         */
        @JvmStatic
        private external fun getParamHint0(pointer: Long): String?

        /**
         * 获取当前命令的错误原因
         * 
         * @param pointer 内核的内存地址
         */
        @JvmStatic
        private external fun getErrorReasons0(pointer: Long): Array<ErrorReason>?

        /**
         * 获取当前命令的补全提示数量
         * 
         * @param pointer 内核的内存地址
         */
        @JvmStatic
        private external fun getSuggestionsSize0(pointer: Long): Int

        /**
         * 获取当前命令其中一个补全提示
         * 
         * @param pointer 内核的内存地址
         * @param which   第几个补全提示，从0开始
         */
        @JvmStatic
        private external fun getSuggestion0(pointer: Long, which: Int): Suggestion?

        /**
         * 获取当前命令的所有补全提示
         * 由于性能原因，不建议使用这个方法，建议按需获取
         * 
         * @param pointer 第几个补全提示，从0开始
         * @return 所有补全提示
         */
        @JvmStatic
        external fun getSuggestions0(pointer: Long): Array<Suggestion?>?

        /**
         * 获取当前命令的语法结构
         * 
         * @param pointer 内核的内存地址
         */
        @JvmStatic
        private external fun getStructure0(pointer: Long): String?

        /**
         * 补全提示被使用时通知c++内核
         * 
         * @param pointer 内核的内存地址
         * @param which   第几个补全提示，从0开始
         */
        @JvmStatic
        private external fun onSuggestionClick0(pointer: Long, which: Int): ClickSuggestionResult?

        /**
         * 获取文本颜色
         * 
         * @param pointer 内核的内存地址
         * @return 每个字符的颜色
         */
        @JvmStatic
        private external fun getColors0(pointer: Long): IntArray?

        /**
         * 初始化"旧命令转新命令"功能
         * 
         * @param assetManager 软件内置资源管理器
         * @param path         数据文件路径
         */
        @JvmStatic
        private external fun old2newInit0(assetManager: AssetManager, path: String): Boolean

        /**
         * 旧命令转新命令
         * 使用前记得先初始化
         * 
         * @param old 旧命令
         * @return 新命令
         */
        @JvmStatic
        private external fun old2new0(old: String): String?
    }
}
