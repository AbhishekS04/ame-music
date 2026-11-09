package com.abhishek.ame.music.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/**
 * Plays a resolved stream URL by decoding it with ffmpeg and writing the PCM
 * straight into the sound card.
 *
 * ffmpeg is the decoder because it is the one thing on a Linux box that already
 * reads every format this app can be handed — Opus and AAC from YouTube, FLAC
 * from an addon, a local file — behind one command line. The alternative was a
 * native binding (gst1-java is not on Maven Central; vlcj drags in libvlc), for
 * no gain at this stage: the shared UI has no reference to a decoder at all, so
 * the only requirement here is "PCM comes out".
 *
 * Format is fixed at 48 kHz stereo signed 16-bit little-endian, which is what
 * ffmpeg is asked for and what the line is opened as. Sample-accurate crossfade
 * and EQ would need the PCM stream in this process (they do), which is why the
 * bytes are read here rather than handed to a player object.
 */
object AudioEngine {

    private const val SAMPLE_RATE = 48_000
    private const val CHANNELS = 2
    private const val BYTES_PER_FRAME = 2 * CHANNELS // s16le

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val format = AudioFormat(
        SAMPLE_RATE.toFloat(),
        16,
        CHANNELS,
        true, // signed
        false, // little-endian
    )

    /** 250 ms, so a slow machine never underruns between reads. */
    private const val BUFFER_BYTES = SAMPLE_RATE / 4 * BYTES_PER_FRAME

    /** An open track: its ffmpeg process, the line it writes to, and the reader. */
    private class Playing(
        val process: Process,
        val line: SourceDataLine,
        val job: Job,
    )

    private var current: Playing? = null

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Length of the track now playing, for the position readout. */
    @Volatile
    var durationMs: Long = 0L
        private set

    /**
     * Starts [url] from the beginning, stopping whatever played before.
     *
     * The line is opened here, before the process starts, because
     * `AudioSystem.getLine` is what throws on a machine with no sound card, and
     * a failure there should not look like a decode failure.
     */
    fun play(url: String, duration: Long) {
        stop()
        durationMs = duration
        val process = startFfmpeg(url) ?: return
        val line = try {
            val info = DataLine.Info(SourceDataLine::class.java, format)
            (AudioSystem.getLine(info) as SourceDataLine).apply {
                open(format, BUFFER_BYTES)
                start()
            }
        } catch (e: Exception) {
            process.destroyForcibly()
            _error.value = "No audio output: ${e.message}"
            return
        }

        _error.value = null
        _playing.value = true
        _positionMs.value = 0L

        val job = scope.launch { pump(process, line) }
        current = Playing(process, line, job)
    }

    fun pause() {
        current?.line?.stop()
        _playing.value = false
    }

    fun resume() {
        current?.line?.start()
        _playing.value = true
    }

    fun stop() {
        current?.let {
            it.job.cancel()
            it.process.destroyForcibly()
            it.line.stop()
            it.line.close()
        }
        current = null
        _playing.value = false
        _positionMs.value = 0L
    }

    fun seek(ms: Long) {
        // A seek restarts ffmpeg at the new offset; the URL and duration are
        // held by the caller, so this is done in the player store instead.
        _positionMs.value = ms
    }

    val volume: Float
        get() = current?.line?.let { line ->
            runCatching { AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, format)) }
                .isSuccess
        }?.let { volumeValue } ?: volumeValue

    private var volumeValue = 1f

    fun setVolume(level: Float) {
        volumeValue = level.coerceIn(0f, 1f)
        // Java Sound's own control, where the mixer has one. Best effort: a
        // mixer without MASTER_GAIN still plays, just at its own level.
        current?.line?.let { line ->
            runCatching {
                line.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN) as javax.sound.sampled.FloatControl
            }.onSuccess { gain ->
                val db = if (volumeValue <= 0f) gain.minimum else
                    (20.0 * kotlin.math.log10(volumeValue.toDouble())).coerceIn(
                        gain.minimum.toDouble(),
                        gain.maximum.toDouble(),
                    ).toFloat()
                runCatching { gain.value = db }
            }
        }
    }

    private fun startFfmpeg(url: String): Process? = try {
        ProcessBuilder(
            "ffmpeg",
            "-hide_banner",
            "-loglevel", "error",
            "-i", url,
            "-vn",
            "-f", "s16le",
            "-acodec", "pcm_s16le",
            "-ar", SAMPLE_RATE.toString(),
            "-ac", CHANNELS.toString(),
            "-",
        )
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    } catch (e: Exception) {
        _error.value = "ffmpeg is not available: ${e.message}"
        null
    }

    /** Copies decoded PCM into the line, counting frames so the position tracks playback. */
    private suspend fun pump(process: Process, line: SourceDataLine) {
        val buffer = ByteArray(BUFFER_BYTES)
        BufferedInputStream(process.inputStream, BUFFER_BYTES).use { input ->
            var frames = 0L
            try {
                while (current != null) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    line.write(buffer, 0, read)
                    frames += (read / BYTES_PER_FRAME).toLong()
                    _positionMs.value = frames * 1000 / SAMPLE_RATE
                }
            } catch (_: Exception) {
                // stop() closes the line under us; that is the normal way out.
            }
        }
        // Natural end of track, not a stop(): let the caller advance the queue.
        line.drain()
        runCatching { line.stop(); line.close() }
        process.waitFor(1, TimeUnit.SECONDS)
        process.destroyForcibly()
        if (current?.process === process) {
            current = null
            _playing.value = false
        }
    }
}
