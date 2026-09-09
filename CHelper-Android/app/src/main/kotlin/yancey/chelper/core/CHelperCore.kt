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
 * 软件的内核，与c++代码交互，负责持有合成后的资源包数据
 * 支持为不同的主包段/拓展包组合同时创建多个内核实例
 *
 * 所有和命令相关的功能都在[CommandContext]上执行：
 * 通过[createContext]把命令文本解析成AST生成命令上下文，
 * 然后在CommandContext上获取命令结构、参数注释、补全提示、语法高亮等
 * 内核本身没有可变状态，可以被多个线程同时使用
 *
 * 内核只通过 [compose]（主包段 + 拓展包）创建；旧的 assets/文件直接加载通道
 * （fromAssets/fromFile）已随主路径切换删除。
 */
class CHelperCore private constructor() : Closeable {
    /**
     * c++内核的内存地址
     */
    private var pointer: Long = 0

    /** 供 FragmentContext 等打开（同模块可见） */
    internal val pointerValue: Long get() = pointer

    /**
     * 把命令文本解析成AST，生成独立的命令上下文
     * 适用于多线程并行的场景：
     * 可以创建任意多个CommandContext，把它们交给不同的线程同时使用
     *
     * @param command 命令文本
     * @return 命令上下文，用完后记得调用close()释放内存
     */
    fun createContext(command: String): CommandContext {
        if (pointer == 0L) {
            throw RuntimeException("fail to create CommandContext because core is closed")
        }
        val contextPointer = createContext0(pointer, command)
        if (contextPointer == 0L) {
            throw RuntimeException("fail to create CommandContext: $command")
        }
        return CommandContext(contextPointer)
    }

    /**
     * 关闭内核，释放内存
     * 内核关闭后已经创建的CommandContext依然可用
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
         * compose 串行锁：C++ 合成复用全局构造阶段，任意两个线程同时合成会互相踩状态，
         * 所有调用方（命令补全 / 库 MCD 高亮）都必须经过它。
         */
        private val composeLock = Any()

        /**
         * "旧命令转新命令"功能是否已经初始化
         */
        private var isOld2NewInit = false

        /**
         * 合成主包启用段 + 已启用拓展包为内核（P0）。
         *
         * @param mainPack       已打开的[MainPack]
         * @param enabledSegments 启用段，如 arrayOf("beta/vanilla")（当前取第一个）
         * @param extensionPacks 拓展包列表：每项为"包内相对路径 → 文件字节"映射（平台层已解压 .chepack）
         * @return 合成后的内核
         */
        fun compose(mainPack: MainPack, enabledSegments: Array<String>, extensionPacks: List<Map<String, ByteArray>>): CHelperCore =
            synchronized(composeLock) {
                val relPaths = ArrayList<String>()
                val contents = ArrayList<ByteArray>()
                val counts = IntArray(extensionPacks.size)
                extensionPacks.forEachIndexed { index, files ->
                    counts[index] = files.size
                    files.forEach { (rel, bytes) ->
                        relPaths.add(rel)
                        contents.add(bytes)
                    }
                }
                val core = CHelperCore()
                var failure: String? = null
                core.pointer = try {
                    compose0(mainPack.pointerValue, enabledSegments, relPaths.toTypedArray(), contents.toTypedArray(), counts)
                } catch (throwable: Throwable) {
                    failure = throwable.message ?: throwable.javaClass.simpleName
                    0
                }
                if (core.pointer == 0L) {
                    throw RuntimeException(failure ?: "fail to compose core")
                }
                core
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
         * 调用c++释放内核
         *
         * @param pointer 内核的内存地址
         */
        @JvmStatic
        private external fun release0(pointer: Long)

        /**
         * 调用c++把命令文本解析成AST，创建命令上下文
         *
         * @param pointer 内核的内存地址
         * @param command 命令文本
         * @return 命令上下文的内存地址
         */
        @JvmStatic
        private external fun createContext0(pointer: Long, command: String): Long

        /**
         * 调用c++合成主包段 + 拓展包
         */
        @JvmStatic
        private external fun compose0(
            mainPackPointer: Long,
            segments: Array<String>,
            packRelPaths: Array<String>,
            packContents: Array<ByteArray>,
            packCounts: IntArray
        ): Long

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
