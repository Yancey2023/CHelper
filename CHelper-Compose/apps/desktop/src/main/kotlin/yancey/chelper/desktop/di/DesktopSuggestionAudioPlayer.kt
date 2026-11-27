package yancey.chelper.desktop.di

import java.net.HttpURLConnection
import java.net.URI
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import yancey.chelper.domain.command.SuggestionAudioPlayer
import yancey.chelper.domain.command.SuggestionAudioSession
import yancey.chelper.domain.command.isWikiSuggestionAudioUrl

internal class DesktopSuggestionAudioPlayer : SuggestionAudioPlayer {
    override suspend fun open(url: String): SuggestionAudioSession {
        require(isWikiSuggestionAudioUrl(url))
        var output: SourceDataLine? = null
        try {
            val samples =
                runInterruptible(Dispatchers.IO) {
                    val connection = URI(url).toURL().openConnection() as HttpURLConnection
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 5_000
                    connection.instanceFollowRedirects = false
                    try {
                        check(connection.responseCode == 200)
                        val audio =
                            connection.inputStream.use { it.readNBytes(MAX_AUDIO_BYTES + 1) }
                        check(audio.size <= MAX_AUDIO_BYTES)
                        AudioSystem.getAudioInputStream(audio.inputStream()).use { input ->
                            val format = input.format
                            val pcm =
                                AudioFormat(format.sampleRate, 16, format.channels, true, false)
                            AudioSystem.getAudioInputStream(pcm, input).use { decoded ->
                                val limit = MAX_PCM_BYTES / pcm.frameSize * pcm.frameSize
                                val data = decoded.readNBytes(limit + pcm.frameSize)
                                check(data.isNotEmpty() && data.size <= limit)
                                val line = AudioSystem.getSourceDataLine(pcm)
                                output = line
                                val bufferFrames = (pcm.frameRate / 10).toInt().coerceAtLeast(1)
                                line.open(pcm, bufferFrames * pcm.frameSize)
                                data
                            }
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
            return DesktopSuggestionAudioSession(checkNotNull(output), samples)
        } catch (failure: Throwable) {
            output?.close()
            throw failure
        }
    }

    private companion object {
        const val MAX_AUDIO_BYTES = 8 * 1024 * 1024
        const val MAX_PCM_BYTES = 32 * 1024 * 1024
    }
}

private class DesktopSuggestionAudioSession(
    private val line: SourceDataLine,
    private val samples: ByteArray,
) : SuggestionAudioSession {
    private val frameSize = line.format.frameSize
    private val frameRate = line.format.frameRate.toDouble()
    private val totalFrames = samples.size / frameSize
    private val lock = Any()
    private var startFrame = 0
    private var nextFrame = 0
    private var deviceStartFrame = 0L
    private var playing = false
    private var closed = false
    private var failure: Exception? = null
    private val feeder =
        CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                synchronized(lock) {
                    if (playing) {
                        try {
                            feed()
                        } catch (error: Exception) {
                            failure = error
                            playing = false
                        }
                    }
                }
                delay(10L)
            }
        }

    override val durationMillis = frameMillis(totalFrames)
    override val positionMillis: Long
        get() =
            synchronized(lock) {
                failure?.let { throw it }
                frameMillis(positionFrame())
            }

    override val finished: Boolean
        get() = synchronized(lock) { positionFrame() >= totalFrames }

    override fun play() =
        synchronized(lock) {
            failure?.let { throw it }
            if (!playing && startFrame < totalFrames) start()
        }

    override fun pause() =
        synchronized(lock) {
            if (playing) stopAt(positionFrame())
        }

    override fun seekTo(positionMillis: Long) =
        synchronized(lock) {
            failure?.let { throw it }
            val remaining = playing
            val target =
                if (positionMillis >= durationMillis) totalFrames
                else (positionMillis.coerceAtLeast(0L) * frameRate / 1_000L).toInt()
            stopAt(target)
            if (remaining && target < totalFrames) start()
        }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            playing = false
            feeder.cancel()
            line.close()
        }
    }

    private fun start() {
        deviceStartFrame = line.longFramePosition
        line.start()
        playing = true
    }

    private fun stopAt(frame: Int) {
        line.stop()
        line.flush()
        playing = false
        startFrame = frame
        nextFrame = frame
    }

    private fun positionFrame(): Int {
        if (!playing) return startFrame
        val consumed = (line.longFramePosition - deviceStartFrame).coerceAtLeast(0L)
        return (startFrame + consumed).coerceAtMost(nextFrame.toLong()).toInt()
    }

    private fun feed() {
        if (positionFrame() >= totalFrames) {
            stopAt(totalFrames)
            return
        }
        // SourceDataLine accepts available bytes without blocking. Keep seeks and pause atomic
        // with each write so a flush cannot be followed by stale samples from an earlier position.
        val frames = minOf(totalFrames - nextFrame, line.available() / frameSize)
        if (frames > 0) {
            nextFrame += line.write(samples, nextFrame * frameSize, frames * frameSize) / frameSize
        }
    }

    private fun frameMillis(frame: Int): Long = (frame * 1_000L / frameRate).toLong()
}
