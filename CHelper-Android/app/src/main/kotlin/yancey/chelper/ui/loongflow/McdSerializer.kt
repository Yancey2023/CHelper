/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * MCD v2 序列化器。
 * 将录入的命令方块数据反向输出为标准 MCD v2 文本格式，
 * 同时支持将已有的 ParsedMCD AST（含 v1 源）升级序列化为 v2。
 */

package yancey.chelper.ui.loongflow

import yancey.chelper.ui.library.mcd.BlockType
import yancey.chelper.ui.library.mcd.ChainItem
import yancey.chelper.ui.library.mcd.MCDBlock
import yancey.chelper.ui.library.mcd.ParsedMCD

/**
 * 将一组手动录入的命令方块序列化为 MCD v2 格式文本。
 *
 * @param chainName 链名称，会写入 ---链名--- 分隔符
 * @param blocks 录入的命令方块列表
 * @param meta 额外的元信息键值对（自动补 @mcd_version=2）
 */
fun serializeBlocksToMCDv2(
    chainName: String,
    blocks: List<MCDBlock>,
    meta: Map<String, String> = emptyMap()
): String {
    val sb = StringBuilder()

    // 元信息头
    sb.appendLine("@mcd_version=2")
    meta.forEach { (k, v) ->
        if (k != "mcd_version") sb.appendLine("@$k=$v")
    }

    // 链分隔符
    sb.appendLine("---$chainName---")

    // 逐个方块输出状态行 + 命令行
    blocks.forEach { block ->
        sb.appendLine(buildStateLine(block))
        sb.appendLine(block.command)
    }

    return sb.toString().trimEnd()
}

/**
 * 将已解析的 ParsedMCD AST 重新序列化为 MCD v2 格式。
 * 如果原始源是 v1（纯命令列表），会自动为每条命令补上默认的连锁状态行。
 */
fun serializeParsedToMCDv2(parsed: ParsedMCD): String {
    val sb = StringBuilder()

    // 元信息：保留原有的，确保 mcd_version=2 排最前
    sb.appendLine("@mcd_version=2")
    parsed.metaInfo
        .filter { it.key != "mcd_version" }
        .forEach { meta -> sb.appendLine("@${meta.key}=${meta.value}") }

    // 链前游离注释
    parsed.rootComments.forEach { comment ->
        sb.appendLine("# $comment")
    }

    // 各命令链
    parsed.chains.forEach { chain ->
        sb.appendLine("---${chain.name}---")
        chain.items.forEach { item ->
            when (item) {
                is ChainItem.Comment -> sb.appendLine("# ${item.text}")
                is ChainItem.RawCommand -> {
                    // v1 裸命令升级：补默认连锁状态行
                    sb.appendLine("> C__")
                    sb.appendLine(item.command)
                }
                is ChainItem.Block -> {
                    sb.appendLine(buildStateLine(item.block))
                    sb.appendLine(item.block.command)
                }
            }
        }
    }

    return sb.toString().trimEnd()
}

/**
 * 根据 MCDBlock 属性构建 v2 状态行。
 * 格式: > [ICR][?_][!_][tN]
 * 省略全默认的尾部占位符保持简洁。
 */
fun buildStateLine(block: MCDBlock): String {
    if (block.type == BlockType.CHAT) {
        return "> H"
    }

    val typeChar = when (block.type) {
        BlockType.IMPULSE -> "I"
        BlockType.CHAIN -> "C"
        BlockType.REPEAT -> "R"
        BlockType.CHAT -> "H" // 这个分支理论上被上面拦截，写上做兜底消除警告
    }
    val cond = if (block.conditional) "?" else "_"
    val rs = if (block.needsRedstone) "!" else "_"
    val tick = if (block.tickDelay > 0) "t${block.tickDelay}" else ""

    return "> $typeChar$cond$rs$tick"
}
