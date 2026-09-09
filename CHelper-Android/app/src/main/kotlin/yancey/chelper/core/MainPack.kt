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
 * 主包（内置补全包）只读装载器。
 * 由平台层解压 .chepack（zip）后，把文件集合（relPath → 字节）传入 [open]。
 * 打开一次长期驻留；版本/分支切换只重跑 [readFile]/compose，不重新 open。
 */
class MainPack private constructor() : Closeable {
    data class Segment(val id: String, val version: String, val packId: String, val name: String)

    private var pointer: Long = 0

    /** 供 CHelperCore.compose 使用（同模块可见） */
    internal val pointerValue: Long get() = pointer

    var segments: List<Segment> = emptyList()
        private set

    override fun close() {
        if (pointer == 0L) {
            return
        }
        release0(pointer)
        pointer = 0
    }

    /**
     * 读取启用段视图中的单个文件（含分层合并），用于 rawtext 等数据获取。
     * @return 文件字节；不存在返回 null
     */
    fun readFile(versionType: String, branch: String, relPath: String): ByteArray? {
        if (pointer == 0L) {
            return null
        }
        return readFile0(pointer, versionType, branch, relPath)
    }

    companion object {
        init {
            System.loadLibrary("CHelperAndroid")
        }

        @JvmStatic
        private external fun open0(relPaths: Array<String>, contents: Array<ByteArray>): Long

        @JvmStatic
        private external fun release0(pointer: Long)

        @JvmStatic
        private external fun listSegments0(pointer: Long): Array<String>?

        @JvmStatic
        private external fun readFile0(pointer: Long, versionType: String, branch: String, relPath: String): ByteArray?

        /**
         * 打开主包。
         * @param relPaths 文件相对路径（如 "manifest.json"、"shared/command/give.json"）
         * @param contents 与 relPaths 一一对应的文件字节
         */
        fun open(relPaths: Array<String>, contents: Array<ByteArray>): MainPack {
            val ptr = open0(relPaths, contents)
            if (ptr == 0L) {
                throw RuntimeException("fail to open MainPack")
            }
            val pack = MainPack()
            pack.pointer = ptr
            pack.segments = (listSegments0(ptr) ?: emptyArray()).map { line ->
                val parts = line.split('\t')
                Segment(
                    id = parts.getOrElse(0) { "" },
                    version = parts.getOrElse(1) { "" },
                    packId = parts.getOrElse(2) { "" },
                    name = parts.getOrElse(3) { "" },
                )
            }
            return pack
        }
    }
}
