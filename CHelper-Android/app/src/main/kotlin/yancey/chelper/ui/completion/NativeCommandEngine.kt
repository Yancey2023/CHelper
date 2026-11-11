package yancey.chelper.ui.completion

import android.content.res.AssetManager
import yancey.chelper.core.CHelperCore
import yancey.chelper.core.CommandContext

internal class NativeCommandEngine(private val core: CHelperCore) : CommandEngine {
    override fun parse(text: String): ParsedCommand = NativeParsedCommand(core.createContext(text))

    override fun close() = core.close()

    companion object {
        fun load(assets: AssetManager, branch: String): NativeCommandEngine {
            val filename =
                assets.list("cpack")?.lastOrNull { it.startsWith(branch) }
                    ?: error("找不到资源包：$branch")
            return NativeCommandEngine(CHelperCore.fromAssets(assets, "cpack/$filename"))
        }
    }
}

private class NativeParsedCommand(private val context: CommandContext) : ParsedCommand {
    override val structure
        get() = context.structure

    override val errors
        get() = context.errorReasons

    override val tokens
        get() = context.syntaxToken

    override val nodeCount
        get() = context.nodeCount

    override fun hint(index: Int) = context.getParamHint(index)

    override fun suggestionCount(index: Int) = context.getSuggestionsSize(index)

    override fun suggestion(index: Int, which: Int) = context.getSuggestion(index, which)

    override fun apply(index: Int, which: Int) = context.applySuggestion(index, which)

    override fun close() = context.close()
}
