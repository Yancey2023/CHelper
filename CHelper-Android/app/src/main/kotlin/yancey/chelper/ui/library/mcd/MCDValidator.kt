/**
 * MCD 内容验证器。
 * 逐行分析 MCD 内容，推断每行的类型，并标记无法确定类型的"问题行"。
 * 用于上传预览界面，在用户提交前展示解析结果。
 */

package yancey.chelper.ui.library.mcd

import androidx.compose.ui.graphics.Color

/**
 * 行的推断类型：解析器能自信地归入的类别。
 * AMBIGUOUS 表示无法推断——既不是英文/斜杠开头的命令，也没有 # 注释标记，
 * 也不属于任何已知 MCD 语法要素。
 */
enum class LineType(val label: String, val color: Color) {
    META("元数据", Color(0xFF7B1FA2)),
    COMMENT("注释", Color(0xFF757575)),
    COMMAND("指令", Color(0xFF2E7D32)),
    CHAIN_SEPARATOR("链分隔符", Color(0xFF1565C0)),
    STATE_LINE("状态行", Color(0xFFE65100)),
    MARKER("标记行", Color(0xFF9E9E9E)),
    AMBIGUOUS("无法推断", Color(0xFFD32F2F))
}

/** 单行验证结果 */
data class MCDLineResult(
    val lineNumber: Int,
    val rawText: String,
    val type: LineType,
    val message: String? = null
)

/** 完整验证结果 */
data class MCDValidationResult(
    val lines: List<MCDLineResult>,
    val hasErrors: Boolean,
    val errorCount: Int
)

/**
 * 验证 MCD 内容，逐行推断类型。
 * @param rawCommands 用户输入的命令区内容（不含 @元数据头，只有 ###Function### 和 ###End### 之间的内容）
 * @return 验证结果
 */
fun validateMCDContent(rawCommands: String): MCDValidationResult {
    if (rawCommands.isBlank()) {
        return MCDValidationResult(emptyList(), hasErrors = false, errorCount = 0)
    }

    val lines = rawCommands.split(Regex("\\r?\\n"))
    val results = mutableListOf<MCDLineResult>()
    var pendingChat = false

    for ((idx, line) in lines.withIndex()) {
        val tline = line.trim()
        val lineNum = idx + 1

        // 空行跳过
        if (tline.isEmpty()) continue

        // ###标记行###
        if (tline.startsWith("###") && tline.endsWith("###")) {
            results.add(MCDLineResult(lineNum, tline, LineType.MARKER))
            pendingChat = false
            continue
        }

        // 处理 H 状态的强制文本
        if (pendingChat) {
            results.add(MCDLineResult(lineNum, tline, LineType.COMMAND))
            pendingChat = false
            continue
        }

        // @元数据行
        if (tline.startsWith("@")) {
            results.add(MCDLineResult(lineNum, tline, LineType.META))
            continue
        }

        // ---链分隔符---
        if (tline.startsWith("---") && tline.endsWith("---")) {
            results.add(MCDLineResult(lineNum, tline, LineType.CHAIN_SEPARATOR))
            continue
        }

        // #注释行（显式）
        if (tline.startsWith("#")) {
            results.add(MCDLineResult(lineNum, tline, LineType.COMMENT))
            continue
        }

        // v2 状态行 >
        if (tline.startsWith(">")) {
            if (Regex("""^>\s*H\s*$""", RegexOption.IGNORE_CASE).matches(tline)) {
                pendingChat = true
            }
            results.add(MCDLineResult(lineNum, tline, LineType.STATE_LINE))
            continue
        }

        // 尝试推断是否为合法指令：行首为 ASCII 字母或 /
        val firstChar = tline.firstOrNull()
        if (firstChar != null && (firstChar.isLetter() && firstChar.code < 128 || firstChar == '/')) {
            results.add(MCDLineResult(lineNum, tline, LineType.COMMAND))
            continue
        }

        // 到这里说明无法推断
        results.add(
            MCDLineResult(
                lineNum, tline, LineType.AMBIGUOUS,
                message = "此行以\"${firstChar}\"开头，既不是英文字母/斜杠(指令)，也没有 # 前缀(注释)，系统无法推断其类型。请添加 # 标记为注释，或修正为合法指令。"
            )
        )
    }

    val errorCount = results.count { it.type == LineType.AMBIGUOUS }
    return MCDValidationResult(
        lines = results,
        hasErrors = errorCount > 0,
        errorCount = errorCount
    )
}

/**
 * 低代码辅助功能：为未标记 MCDv2 状态的常规指令行自动加上默认的 `> C` (连锁) 标记。
 * 注释和空行不破坏先前存在的状态标记。
 */
fun autoPrefixMCDv2States(rawCommands: String): String {
    val results = validateMCDContent(rawCommands)
    val lines = rawCommands.split(Regex("\\r?\\n"))
    
    val lineTypeByNum = results.lines.associateBy { it.lineNumber }
    val output = mutableListOf<String>()
    
    var lastEffectiveType: LineType? = null
    
    for ((idx, line) in lines.withIndex()) {
        val lineNum = idx + 1
        val result = lineTypeByNum[lineNum]
        
        if (result == null) {
            // 空行
            output.add(line)
            continue
        }
        
        if (result.type == LineType.COMMAND) {
            if (lastEffectiveType != LineType.STATE_LINE) {
                // 如果当前是一个指令，且上面最近一个有效实体不是状态行，则补全
                output.add("> C")
            }
            output.add(line)
            lastEffectiveType = LineType.COMMAND
        } else {
            output.add(line)
            // 注释、空行、元数据不改变上下文的执行链路状态挂载判定
            if (result.type != LineType.COMMENT && result.type != LineType.META) {
                lastEffectiveType = result.type
            }
        }
    }
    
    return output.joinToString("\n")
}
