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
import java.util.zip.ZipInputStream

/**
 * 主包（内置补全包）提供器：首次使用时从 assets 惰性打开一次 `main-pack.chepack` 并常驻，
 * 供命令补全（compose）与 rawtext 数据（readFile）等共用，随版本/分支（启用段）切换。
 * 主包缺失 / JNI 未就绪时返回 null 并缓存失败，由调用方处理，绝不崩溃。
 */
object MainPackProvider {
    @Volatile
    private var pack: MainPack? = null

    @Volatile
    private var failed = false

    fun get(context: Context): MainPack? {
        if (failed) {
            return null
        }
        val p = pack
        if (p != null) {
            return p
        }
        return synchronized(this) {
            if (failed) {
                return@synchronized null
            }
            pack ?: try {
                open(context).also { pack = it }
            } catch (_: Throwable) {
                failed = true
                null
            }
        }
    }

    /** 关闭并重置（如重新导入主包） */
    fun reset() {
        synchronized(this) {
            pack?.close()
            pack = null
            failed = false
        }
    }

    private fun open(context: Context): MainPack {
        val relPaths = ArrayList<String>()
        val contents = ArrayList<ByteArray>()
        context.assets.open("main-pack.chepack").use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    // 过滤目录条目：zip 目录可能以 '\' 结尾（Windows 打包），
                    // ZipEntry.isDirectory 只认 '/'，空字节目录条目会破坏引擎装载。
                    if (entry.isDirectory ||
                        entry.name.isEmpty() ||
                        entry.name.endsWith('/') ||
                        entry.name.endsWith('\\')
                    ) {
                        continue
                    }
                    relPaths.add(entry.name)
                    contents.add(zip.readBytes())
                }
            }
        }
        return MainPack.open(relPaths.toTypedArray(), contents.toTypedArray())
    }
}
