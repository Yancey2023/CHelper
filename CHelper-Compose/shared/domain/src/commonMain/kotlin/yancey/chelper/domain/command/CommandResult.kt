package yancey.chelper.domain.command

/** Value objects independent of the native ABI. */
data class ErrorReason(var errorReason: String? = null, var start: Int = 0, var end: Int = 0)

data class Suggestion(var name: String? = null, var description: String? = null)

data class ClickSuggestionResult(var text: String = "", var selection: Int = 0)
