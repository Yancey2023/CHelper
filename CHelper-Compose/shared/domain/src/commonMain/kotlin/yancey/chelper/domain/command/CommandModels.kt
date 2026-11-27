package yancey.chelper.domain.command

data class CommandProblem(val message: String, val start: Int, val end: Int)

data class CommandSuggestion(
    val name: String,
    val description: String?,
    val preview: SuggestionPreview? = null,
)

sealed interface SuggestionPreview {
    data class Icon(val url: String) : SuggestionPreview

    data class Audio(val url: String) : SuggestionPreview

    data class Easing(val id: String) : SuggestionPreview

    data class DyeColor(val id: String) : SuggestionPreview
}

fun interface SuggestionAudioPlayer {
    /** Opens a paused preview. Cancellation during loading releases the platform player. */
    suspend fun open(url: String): SuggestionAudioSession
}

/** A single preview, controlled and closed on the UI thread. */
interface SuggestionAudioSession : AutoCloseable {
    val durationMillis: Long
    val positionMillis: Long
    val finished: Boolean

    fun play()

    fun pause()

    fun seekTo(positionMillis: Long)
}

private val wikiMediaUrl = Regex("https://zh\\.minecraft\\.wiki/images/[A-Za-z0-9_%.()'!+,~-]+")

fun isWikiSuggestionMediaUrl(url: String, extension: String): Boolean =
    url.endsWith(extension) && wikiMediaUrl.matches(url)

fun isWikiSuggestionIconUrl(url: String): Boolean =
    wikiMediaUrl.matches(url) &&
        (url.endsWith(".png") || url.endsWith(".gif") || url.endsWith(".webp"))

fun isWikiSuggestionAudioUrl(url: String): Boolean =
    isWikiSuggestionMediaUrl(url, ".ogg") || isWikiSuggestionMediaUrl(url, ".wav")

data class CommandResult(
    val structure: String = "",
    val hint: String = "",
    val problems: List<CommandProblem> = emptyList(),
    val tokens: List<Int> = emptyList(),
    val suggestions: List<CommandSuggestion> = emptyList(),
    val totalSuggestions: Int = suggestions.size,
)
