package yancey.chelper.domain.command

data class CommandProblem(val message: String, val start: Int, val end: Int)

data class CommandSuggestion(val name: String, val description: String?)

data class CommandResult(
    val structure: String = "",
    val hint: String = "",
    val problems: List<CommandProblem> = emptyList(),
    val tokens: List<Int> = emptyList(),
    val suggestions: List<CommandSuggestion> = emptyList(),
    val totalSuggestions: Int = suggestions.size,
)
