package yancey.chelper.nativecore.command

import yancey.chelper.core.CHelperCore
import yancey.chelper.core.CommandContext
import yancey.chelper.domain.command.ClickSuggestionResult
import yancey.chelper.domain.command.CommandEngine
import yancey.chelper.domain.command.ErrorReason
import yancey.chelper.domain.command.ParsedCommand
import yancey.chelper.domain.command.Suggestion

class NativeCommandEngine(private val core: CHelperCore) : CommandEngine {
    override suspend fun parse(text: String): ParsedCommand =
        NativeParsedCommand(core.createContext(text))

    override fun close() = core.close()

    companion object {
        fun load(
            packFilenames: List<String>,
            openCore: (String) -> CHelperCore,
            branch: String,
        ): NativeCommandEngine {
            val filename =
                packFilenames.lastOrNull { it.startsWith(branch) } ?: error("找不到资源包：$branch")
            return NativeCommandEngine(openCore("cpack/$filename"))
        }
    }
}

private class NativeParsedCommand(private val context: CommandContext) : ParsedCommand {
    override val structure
        get() = context.structure

    override val errors
        get() =
            context.errorReasons
                ?.map { ErrorReason(it.errorReason, it.start, it.end) }
                ?.toTypedArray()

    override val tokens
        get() = context.syntaxToken

    override val nodeCount
        get() = context.nodeCount

    override fun hint(index: Int) = context.getParamHint(index)

    override fun suggestionCount(index: Int) = context.getSuggestionsSize(index)

    override fun suggestion(index: Int, which: Int) =
        context.getSuggestion(index, which)?.let { Suggestion(it.name, it.description) }

    override fun apply(index: Int, which: Int) =
        context.applySuggestion(index, which)?.let { ClickSuggestionResult(it.text, it.selection) }

    override fun close() = context.close()
}
