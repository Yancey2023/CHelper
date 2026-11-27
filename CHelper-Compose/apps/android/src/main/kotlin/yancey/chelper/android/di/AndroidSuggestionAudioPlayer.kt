package yancey.chelper.android.di

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import yancey.chelper.domain.command.SuggestionAudioPlayer
import yancey.chelper.domain.command.SuggestionAudioSession
import yancey.chelper.domain.command.isWikiSuggestionAudioUrl

internal class AndroidSuggestionAudioPlayer : SuggestionAudioPlayer {
    override suspend fun open(url: String): SuggestionAudioSession {
        require(isWikiSuggestionAudioUrl(url))
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
            )
            player.setDataSource(url)
            suspendCancellableCoroutine { preparation ->
                player.setOnPreparedListener {
                    if (preparation.isActive) preparation.resume(Unit)
                }
                player.setOnErrorListener { _, what, extra ->
                    if (preparation.isActive) {
                        preparation.resumeWithException(
                            IllegalStateException("MediaPlayer $what/$extra")
                        )
                    }
                    true
                }
                player.prepareAsync()
            }
            return AndroidSuggestionAudioSession(player)
        } catch (failure: Throwable) {
            player.release()
            throw failure
        }
    }
}

private class AndroidSuggestionAudioSession(private val player: MediaPlayer) :
    SuggestionAudioSession {
    private var ended = false
    private var error: String? = null
    private var pendingSeek: Long? = null
    private var activeSeek: Long? = null

    override val durationMillis = player.duration.toLong().coerceAtLeast(0L)
    override val positionMillis: Long
        get() {
            check(error == null) { error.orEmpty() }
            return pendingSeek ?: if (ended) durationMillis else player.currentPosition.toLong()
        }

    override val finished: Boolean
        get() = ended

    init {
        player.setOnPreparedListener(null)
        player.setOnCompletionListener { ended = true }
        player.setOnErrorListener { _, what, extra ->
            error = "MediaPlayer $what/$extra"
            true
        }
        player.setOnSeekCompleteListener {
            val target = pendingSeek
            if (target != null && target != activeSeek) {
                requestSeek(target)
            } else {
                activeSeek = null
                pendingSeek = null
            }
        }
    }

    override fun play() {
        check(error == null) { error.orEmpty() }
        ended = false
        player.start()
    }

    override fun pause() {
        if (!ended && player.isPlaying) player.pause()
    }

    override fun seekTo(positionMillis: Long) {
        check(error == null) { error.orEmpty() }
        val position = positionMillis.coerceIn(0L, durationMillis)
        if (position < durationMillis) ended = false
        pendingSeek = position
        if (activeSeek == null) requestSeek(position)
    }

    @Suppress("DEPRECATION")
    private fun requestSeek(position: Long) {
        activeSeek = position
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            player.seekTo(position, MediaPlayer.SEEK_CLOSEST)
        } else {
            player.seekTo(position.toInt())
        }
    }

    override fun close() {
        player.release()
    }
}
