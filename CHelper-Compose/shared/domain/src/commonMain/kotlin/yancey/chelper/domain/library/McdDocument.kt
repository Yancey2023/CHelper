/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * MCD (Minecraft Command Data) 可视化渲染器
 * 支持 v1 (纯指令列表) 和 v2 (带状态行/链分隔符的结构化格式) 两种格式
 */

package yancey.chelper.domain.library

private val mcdVersion2LineRegex = Regex("""^@mcd_version\s*=\s*2$""", RegexOption.IGNORE_CASE)

// 数据模型

/** 元数据行，例如 @author = xxx */
data class MCDMeta(val key: String, val value: String)

/** 命令方块的类型标识 */
enum class BlockType {
    IMPULSE,
    CHAIN,
    REPEAT,
    CHAT,
}

/** v2 格式的命令方块 */
data class MCDBlock(
    val type: BlockType = BlockType.CHAIN,
    val conditional: Boolean = false,
    val alwaysActive: Boolean = true,
    val needsRedstone: Boolean = false,
    val tickDelay: Int = 0,
    val command: String = "",
    var syntaxHighlightTokens: IntArray? = null,
)

/** 链中的一个元素：可能是注释、v1 原始指令、或 v2 命令方块 */
sealed class ChainItem {
    data class Comment(val text: String) : ChainItem()

    data class RawCommand(val command: String, var syntaxHighlightTokens: IntArray? = null) :
        ChainItem()

    data class Block(val block: MCDBlock) : ChainItem()
}

/** 一条命令链 */
data class MCDChain(val name: String, val items: MutableList<ChainItem> = mutableListOf())

/** 解析后的完整 MCD 结构 */
data class ParsedMCD(
    val metaInfo: List<MCDMeta> = emptyList(),
    val rootComments: List<String> = emptyList(),
    val chains: List<MCDChain> = emptyList(),
    val isV2: Boolean = false,
)

/** 纯结构解析：行扫一遍，不碰 CHelperCore */
fun parseMCDStructure(content: String?, ambiguousDefault: String = "comment"): ParsedMCD {
    if (content.isNullOrBlank()) return ParsedMCD()

    return try {
        val metaInfo = mutableListOf<MCDMeta>()
        val rootComments = mutableListOf<String>()
        val chains = mutableListOf<MCDChain>()
        var currentChain: MCDChain? = null
        val functionMarkerIndex =
            content.lineSequence().indexOfFirst { it.trim() == "###Function###" }

        val isV2 =
            if (functionMarkerIndex >= 0) {
                content.lineSequence().take(functionMarkerIndex).any {
                    mcdVersion2LineRegex.matches(it.trim())
                }
            } else {
                var detected = false
                for (line in content.lineSequence()) {
                    val t = line.trim()
                    if (mcdVersion2LineRegex.matches(t)) {
                        detected = true
                        break
                    }
                    if (t.isNotEmpty() && !t.startsWith("@") && !t.startsWith("###")) break
                }
                detected
            }

        var pendingBlockType = BlockType.CHAIN
        var pendingConditional = false
        var pendingAlwaysActive = true
        var pendingNeedsRedstone = false
        var pendingTickDelay = 0
        var hasPendingState = false
        var lastHeaderMetaIndex: Int? = null

        for ((lineIndex, line) in content.lineSequence().withIndex()) {
            val tline = line.trim()

            if (functionMarkerIndex >= 0 && lineIndex < functionMarkerIndex) {
                when {
                    tline.isEmpty() -> lastHeaderMetaIndex = null
                    tline.startsWith("@") -> {
                        val splitIdx = tline.indexOf('=')
                        if (splitIdx > 0) {
                            metaInfo.add(
                                MCDMeta(
                                    key = tline.substring(1, splitIdx).trim(),
                                    value = tline.substring(splitIdx + 1).trim(),
                                )
                            )
                            lastHeaderMetaIndex = metaInfo.lastIndex
                        }
                    }

                    tline.startsWith("#") -> {
                        rootComments.add(tline.substring(1).trim())
                        lastHeaderMetaIndex = null
                    }

                    tline.startsWith("//") -> lastHeaderMetaIndex = null
                    else -> {
                        val metaIndex = lastHeaderMetaIndex
                        if (
                            metaIndex != null &&
                                metaInfo[metaIndex].key.equals("note", ignoreCase = true)
                        ) {
                            val note = metaInfo[metaIndex]
                            metaInfo[metaIndex] = note.copy(value = "${note.value}\n$tline")
                        } else {
                            rootComments.add(tline)
                        }
                    }
                }
                continue
            }

            if (tline.isEmpty()) continue

            if (lineIndex == functionMarkerIndex) continue
            if (tline == "###End###") break

            // 杂项标记 ###Function### / ###End###
            if (tline.startsWith("###") && tline.endsWith("###")) continue

            // 若当前等待的是 CHAT 状态，则无论下面是什么前缀，都当成指令文本
            if (isV2 && hasPendingState && pendingBlockType == BlockType.CHAT) {
                if (currentChain == null) {
                    currentChain = MCDChain(name = "分离的指令")
                    chains.add(currentChain)
                }
                val block =
                    MCDBlock(
                        type = pendingBlockType,
                        conditional = false,
                        alwaysActive = true,
                        needsRedstone = false,
                        tickDelay = 0,
                        command = tline,
                    )
                currentChain.items.add(ChainItem.Block(block))
                hasPendingState = false
                continue
            }

            // 元数据行
            if (tline.startsWith("@")) {
                val splitIdx = tline.indexOf('=')
                if (splitIdx > 0) {
                    metaInfo.add(
                        MCDMeta(
                            key = tline.substring(1, splitIdx).trim(),
                            value = tline.substring(splitIdx + 1).trim(),
                        )
                    )
                }
                continue
            }

            // v2 链分割符 ---链名---
            if (tline.startsWith("---") && tline.endsWith("---")) {
                val chainName = tline.replace("---", "").trim().ifEmpty { "未命名命令链" }
                currentChain = MCDChain(name = chainName)
                chains.add(currentChain)
                hasPendingState = false
                continue
            }

            // 注释行（# 和 // 两种格式）
            if (tline.startsWith("#")) {
                val commentText = tline.substring(1).trim()
                if (currentChain != null) {
                    currentChain.items.add(ChainItem.Comment(commentText))
                } else {
                    rootComments.add(commentText)
                }
                continue
            }
            if (tline.startsWith("//")) continue

            // v2 状态行 > 开头
            if (isV2 && tline.startsWith(">")) {
                val stateRegex =
                    Regex(
                        """^>\s*([ICRH_])?([?_])?([!_])?(?:t(\d+|_))?\s*$""",
                        RegexOption.IGNORE_CASE,
                    )
                val match = stateRegex.matchEntire(tline)
                if (match != null) {
                    val rawType = (match.groupValues[1].ifEmpty { "C" }).uppercase()
                    val effectiveType = if (rawType == "_") "C" else rawType
                    pendingBlockType =
                        when (effectiveType) {
                            "I" -> BlockType.IMPULSE
                            "R" -> BlockType.REPEAT
                            "H" -> BlockType.CHAT
                            else -> BlockType.CHAIN
                        }

                    if (pendingBlockType == BlockType.CHAT) {
                        pendingConditional = false
                        pendingAlwaysActive = true
                        pendingNeedsRedstone = false
                        pendingTickDelay = 0
                    } else {
                        val cond = match.groupValues[2]
                        val rs = match.groupValues[3]
                        val tick = match.groupValues[4]
                        pendingConditional = cond == "?"
                        pendingAlwaysActive = rs != "!"
                        pendingNeedsRedstone = rs == "!"
                        pendingTickDelay =
                            if (tick.isNotEmpty() && tick != "_") tick.toIntOrNull() ?: 0 else 0
                    }
                } else {
                    pendingBlockType = BlockType.CHAIN
                    pendingConditional = false
                    pendingAlwaysActive = true
                    pendingNeedsRedstone = false
                    pendingTickDelay = 0
                }
                hasPendingState = true
                continue
            }

            if (currentChain == null) {
                currentChain = MCDChain(name = "分离的指令")
                chains.add(currentChain)
            }

            if (isV2) {
                val block =
                    if (hasPendingState) {
                        MCDBlock(
                            type = pendingBlockType,
                            conditional = pendingConditional,
                            alwaysActive = pendingAlwaysActive,
                            needsRedstone = pendingNeedsRedstone,
                            tickDelay = pendingTickDelay,
                            command = tline,
                        )
                    } else {
                        MCDBlock(command = tline)
                    }
                currentChain.items.add(ChainItem.Block(block))
                hasPendingState = false
            } else {
                val firstChar = tline.firstOrNull()
                if (
                    firstChar != null &&
                        (firstChar.isLetter() && firstChar.code < 128 || firstChar == '/')
                ) {
                    currentChain.items.add(ChainItem.RawCommand(tline))
                } else {
                    if (ambiguousDefault == "command") {
                        currentChain.items.add(ChainItem.RawCommand(tline))
                    } else {
                        currentChain.items.add(ChainItem.Comment(tline))
                    }
                }
            }
        }

        ParsedMCD(metaInfo = metaInfo, rootComments = rootComments, chains = chains, isV2 = isV2)
    } catch (e: Exception) {

        ParsedMCD(
            metaInfo = listOf(MCDMeta(key = "error", value = "解析失败: ${e.message}")),
            rootComments = emptyList(),
            chains = emptyList(),
            isV2 = false,
        )
    }
}
