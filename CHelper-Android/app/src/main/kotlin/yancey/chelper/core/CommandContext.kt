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

import java.io.Closeable

/**
 * 命令上下文，持有某条命令解析好的AST
 * 通过[CHelperCore.createContext]创建
 *
 * 和[CHelperCore]不同，CommandContext不保存光标等可变状态，
 * 所有操作都是只读的，位置信息通过方法参数传入，因此：
 * 1. 可以把同一个CommandContext交给多个线程同时读取
 * 2. 可以基于同一个CHelperCore创建多个CommandContext并行工作
 *
 * CommandContext持有资源包的共享引用，
 * 即使CHelperCore先被close，CommandContext依然可用
 */
class CommandContext internal constructor(
    /**
     * c++命令上下文的内存地址
     */
    private var pointer: Long
) : Closeable {

    /**
     * 获取这个上下文对应的命令文本
     */
    val command: String?
        get() {
            if (pointer == 0L) {
                return null
            }
            return command0(pointer)
        }

    /**
     * 获取命令结构
     */
    val structure: String?
        get() {
            if (pointer == 0L) {
                return null
            }
            return getStructure0(pointer)
        }

    /**
     * 获取命令的错误原因
     */
    val errorReasons: Array<ErrorReason>?
        get() {
            if (pointer == 0L) {
                return null
            }
            return getErrorReasons0(pointer)
        }

    /**
     * 获取最佳解析路径中已经匹配的命令语义节点数量
     */
    val nodeCount: Int
        get() {
            if (pointer == 0L) {
                return 0
            }
            return getNodeCount0(pointer)
        }

    /**
     * 获取文本颜色
     *
     * @return 每个字符的类型
     */
    val syntaxToken: IntArray?
        get() {
            if (pointer == 0L) {
                return null
            }
            return getColors0(pointer)
        }

    /**
     * 获取指定位置的参数注释
     *
     * @param index 光标位置
     */
    fun getParamHint(index: Int): String? {
        if (pointer == 0L) {
            return null
        }
        return getParamHint0(pointer, index)
    }

    /**
     * 获取指定位置的补全提示数量
     *
     * @param index 光标位置
     */
    fun getSuggestionsSize(index: Int): Int {
        if (pointer == 0L) {
            return 0
        }
        return getSuggestionsSize0(pointer, index)
    }

    /**
     * 获取指定位置的其中一个补全提示
     *
     * @param index 光标位置
     * @param which 第几个补全提示，从0开始
     */
    fun getSuggestion(index: Int, which: Int): Suggestion? {
        if (pointer == 0L) {
            return null
        }
        return getSuggestion0(pointer, index, which)
    }

    /**
     * 把指定位置的其中一个补全提示应用到命令文本
     * 该方法不修改 CommandContext 自身状态（无副作用）
     *
     * @param index 计算补全提示时的光标位置
     * @param which 第几个补全提示，从0开始
     * @return 应用后的新命令文本和新的光标位置
     */
    fun applySuggestion(index: Int, which: Int): ClickSuggestionResult? {
        if (pointer == 0L) {
            return null
        }
        return applySuggestion0(pointer, index, which)
    }

    /**
     * 关闭命令上下文，释放内存
     */
    override fun close() {
        if (pointer == 0L) {
            return
        }
        release0(pointer)
        pointer = 0
    }

    companion object {
        /**
         * 调用c++获取命令文本
         *
         * @param pointer 命令上下文的内存地址
         */
        @JvmStatic
        private external fun command0(pointer: Long): String?

        /**
         * 调用c++释放命令上下文
         *
         * @param pointer 命令上下文的内存地址
         */
        @JvmStatic
        private external fun release0(pointer: Long)

        /**
         * 调用c++获取命令结构
         *
         * @param pointer 命令上下文的内存地址
         */
        @JvmStatic
        private external fun getStructure0(pointer: Long): String?

        /**
         * 调用c++获取指定位置的参数注释
         *
         * @param pointer 命令上下文的内存地址
         * @param index   光标位置
         */
        @JvmStatic
        private external fun getParamHint0(pointer: Long, index: Int): String?

        /**
         * 调用c++获取命令的错误原因
         *
         * @param pointer 命令上下文的内存地址
         */
        @JvmStatic
        private external fun getErrorReasons0(pointer: Long): Array<ErrorReason>?

        /**
         * 调用c++获取指定位置的补全提示数量
         *
         * @param pointer 命令上下文的内存地址
         * @param index   光标位置
         */
        @JvmStatic
        private external fun getSuggestionsSize0(pointer: Long, index: Int): Int

        /**
         * 调用c++获取指定位置的其中一个补全提示
         *
         * @param pointer 命令上下文的内存地址
         * @param index   光标位置
         * @param which   第几个补全提示，从0开始
         */
        @JvmStatic
        private external fun getSuggestion0(pointer: Long, index: Int, which: Int): Suggestion?

        /**
         * 调用c++获取最佳解析路径中已经匹配的命令语义节点数量
         *
         * @param pointer 命令上下文的内存地址
         */
        @JvmStatic
        private external fun getNodeCount0(pointer: Long): Int

        /**
         * 调用c++把指定位置的其中一个补全提示应用到命令文本
         *
         * @param pointer 命令上下文的内存地址
         * @param index   计算补全提示时的光标位置
         * @param which   第几个补全提示，从0开始
         */
        @JvmStatic
        private external fun applySuggestion0(
            pointer: Long,
            index: Int,
            which: Int
        ): ClickSuggestionResult?

        /**
         * 调用c++获取文本颜色
         *
         * @param pointer 命令上下文的内存地址
         * @return 每个字符的类型
         */
        @JvmStatic
        private external fun getColors0(pointer: Long): IntArray?
    }
}
