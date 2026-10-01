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

package yancey.chelper.android.extension

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import yancey.chelper.core.ExtensionPackSource
import yancey.chelper.data.SettingsDataStore

/**
 * 启用拓展包的应用层实现（注册到 [yancey.chelper.core.ExtensionPackBridge]）：
 *
 * - 配置源 = Settings 里的已装包列表（有序；仅 enabled 参与合成，顺序即叠加顺序，列表靠前优先）；
 * - 每个启用包 = `filesDir/packs/<fileName>.chepack`（导入时由管理页写入）；
 * - [load] 同步返回 (指纹, 启用包文件集)：指纹 = 启用包 id/version/文件名 按序拼接，
 *   任一包启停/删除/换版本/调序都会改变指纹 → 内核缓存自动按新配置重建；
 * - zip 解压结果按文件缓存，[invalidate] 在管理页任何变更后调用；
 * - 需在后台线程调用（KernelCache.acquire 已保证）。
 */
object AndroidExtensionPacks : ExtensionPackSource {

    @Volatile
    private var cachedFingerprint: String? = null

    @Volatile
    private var cachedPacks: List<Map<String, ByteArray>> = emptyList()

    /** fileName → 解压后的 { 相对路径 → 字节 }（目录条目已过滤） */
    @Volatile
    private var unpackedCache: Map<String, Map<String, ByteArray>>? = null

    /** 管理页任何变更（导入/启停/删除/排序）后调用，下次合成按新配置重新装载 */
    fun invalidate() {
        cachedFingerprint = null
        cachedPacks = emptyList()
        unpackedCache = null
    }

    override fun load(context: Context): Pair<String, List<Map<String, ByteArray>>> {
        val app = context.applicationContext
        val entries = runBlocking { SettingsDataStore(app).extensionPacks().first() }
        val enabled = entries.filter { it.enabled }
        val fingerprint = enabled.joinToString("|") { "${it.packId}@${it.version}@${it.fileName}" }
        if (fingerprint.isEmpty()) {
            cachedFingerprint = fingerprint
            cachedPacks = emptyList()
            return fingerprint to emptyList()
        }
        if (cachedFingerprint == fingerprint) {
            return fingerprint to cachedPacks
        }
        val packs = enabled.mapNotNull { unpack(app, it.fileName) }
        cachedFingerprint = fingerprint
        cachedPacks = packs
        return fingerprint to packs
    }

    private fun unpack(app: Context, fileName: String): Map<String, ByteArray>? {
        unpackedCache?.get(fileName)?.let { return it }
        val file = File(File(app.filesDir, "packs"), fileName)
        if (!file.exists()) {
            return null
        }
        val files = HashMap<String, ByteArray>()
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    // 过滤目录条目（zip 目录可能以 '\' 结尾，ZipEntry.isDirectory 只认 '/'）
                    if (entry.isDirectory ||
                        entry.name.isEmpty() ||
                        entry.name.endsWith('/') ||
                        entry.name.endsWith('\\')
                    ) {
                        continue
                    }
                    files[entry.name] = zip.readBytes()
                }
            }
        } catch (_: Exception) {
            return null
        }
        val cache = unpackedCache?.toMutableMap() ?: HashMap()
        cache[fileName] = files
        unpackedCache = cache
        return files
    }
}
