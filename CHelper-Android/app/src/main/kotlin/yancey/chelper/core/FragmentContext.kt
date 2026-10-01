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
 * 片段补全上下文：不依赖完整命令，直接对一段语法单元（目标选择器 / ID 键表）做补全。
 * 与命令补全共用同一内核（Parser/AutoSuggestion/Linter）与同一数据源（合成主包）。
 * 创建后与 core 的生命周期独立（native 侧持有资源包引用），core 关闭后仍可用。
 */
class FragmentContext internal constructor(
    private var pointer: Long
) : Closeable {

    /**
     * 获取错误原因
     */
    val errorReasons: Array<ErrorReason>?
        get() {
            if (pointer == 0L) {
                return null
            }
            return getErrorReasons0(pointer)
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
     * 把指定位置的其中一个补全提示应用到片段文本
     *
     * @param index 计算补全提示时的光标位置
     * @param which 第几个补全提示，从0开始
     * @return 应用后的新片段文本和新的光标位置
     */
    fun applySuggestion(index: Int, which: Int): ClickSuggestionResult? {
        if (pointer == 0L) {
            return null
        }
        return applySuggestion0(pointer, index, which)
    }

    /**
     * 关闭并释放内存
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
            System.loadLibrary("CHelperAndroid")
        }

        /**
         * 打开目标选择器片段（rawtext 选择器字段）
         *
         * @param core    当前内核（用于共享资源包）
         * @param content 选择器文本（如 "@p[tag=a,x="）
         */
        fun openSelector(core: CHelperCore, content: String): FragmentContext? {
            val ptr = openSelector0(core.pointerValue, content)
            return if (ptr == 0L) null else FragmentContext(ptr)
        }

        /**
         * 打开 ID 键表片段（如翻译键，key="translate"）
         */
        fun openId(core: CHelperCore, key: String, content: String): FragmentContext? {
            val ptr = openId0(core.pointerValue, key, content)
            return if (ptr == 0L) null else FragmentContext(ptr)
        }

        @JvmStatic
        private external fun openSelector0(corePtr: Long, content: String): Long

        @JvmStatic
        private external fun openId0(corePtr: Long, key: String, content: String): Long

        @JvmStatic
        private external fun release0(pointer: Long)

        @JvmStatic
        private external fun getErrorReasons0(pointer: Long): Array<ErrorReason>?

        @JvmStatic
        private external fun getSuggestionsSize0(pointer: Long, index: Int): Int

        @JvmStatic
        private external fun getSuggestion0(pointer: Long, index: Int, which: Int): Suggestion?

        @JvmStatic
        private external fun applySuggestion0(pointer: Long, index: Int, which: Int): ClickSuggestionResult?
    }
}
