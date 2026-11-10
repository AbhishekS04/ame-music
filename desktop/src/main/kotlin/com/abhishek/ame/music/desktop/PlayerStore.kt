package com.abhishek.ame.music.desktop

import com.abhishek.ame.music.data.YtMusicRepository
import com.abhishek.ame.music.data.innertube.StreamResolver
import com.abhishek.ame.music.data.model.LikeStatus
import com.abhishek.ame.music.data.model.Song
import com.abhishek.ame.music.playback.PlaybackPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The desktop's queue and now-playing state.
 *
 * Not a port of the phone's MainViewModel — that class is the whole app (party,
 * downloads, lyrics, automix) and this is the layer those build on. What it
 * does hold is the shape [NowPlayingScreen] reads: a queue, an index, repeat
 * and shuffle, and a playhead — so the real player can be drawn rather than a
 * bar standing in for one.
 *
 * Playback itself is [AudioEngine]: one track at a time, ffmpeg to PCM. Gapless
 * and the crossfade in shared/'s TransitionFilter need a mixer, which is the
 * next thing this would grow.
 */
object PlayerStore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _index = MutableStateFlow(-1)
    val index: StateFlow<Int> = _index.asStateFlow()

    /**
     * The track now playing, as state rather than a getter over [queue].
     *
     * It used to be `get() = _queue.value.getOrNull(_index.value)`, which Compose
     * cannot observe — a plain getter reads the flows' values once and registers
     * no dependency, so the mini player and the player screen never appeared
     * when playback started. It is kept in step by [retarget] instead, which
     * every place that changes the queue or the index goes through.
     */
    private val _song = MutableStateFlow<Song?>(null)
    val songFlow: StateFlow<Song?> = _song.asStateFlow()

    val song: Song? get() = _song.value

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    /** 0 none, 1 all, 2 one — the phone's own numbering. */
    private val _repeatMode = MutableStateFlow(0)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _autoplay = MutableStateFlow(true)
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    private val _like = MutableStateFlow(LikeStatus.INDIFFERENT)
    val likeStatus: StateFlow<LikeStatus> = _like.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** The playhead [NowPlayingScreen] reads; written by the audio engine's own clock. */
    val position = PlaybackPosition()

    val playing: StateFlow<Boolean> get() = AudioEngine.playing

    private var resolveJob: Job? = null

    /** Publishes the track at the current index. Called from everywhere the index or queue moves. */
    private fun retarget() {
        _song.value = _queue.value.getOrNull(_index.value)
    }

    /** Replaces the queue with [songs] and starts at [startAt]. */
    fun playQueue(songs: List<Song>, startAt: Int = 0) {
        if (songs.isEmpty()) return
        _queue.value = songs
        play(startAt.coerceIn(songs.indices))
    }

    /** Plays one track, as the queue it is on its own. */
    fun play(song: Song) = playQueue(listOf(song), 0)

    /** Starts [position] in the current queue. */
    fun play(position: Int) {
        val songs = _queue.value
        val target = songs.getOrNull(position) ?: return
        _index.value = position
        retarget()
        _error.value = null
        _loading.value = true
        this.position.report(0L)
        _like.value = LikeStatus.INDIFFERENT

        resolveJob?.cancel()
        resolveJob = scope.launch {
            try {
                val url = StreamResolver.resolve(target.videoId)
                _loading.value = false
                AudioEngine.play(url, durationMsOf(target))
                syncPositionFromEngine()
            } catch (e: Exception) {
                _loading.value = false
                _error.value = e.message ?: "Could not play this track"
            }
        }
    }

    /**
     * Feeds the screen's playhead from the decoder.
     *
     * The engine already counts frames as it writes them; this copies that onto
     * [PlaybackPosition] with a timestamp, which is what the lyrics clock needs
     * to stay aligned between reads.
     */
    private fun syncPositionFromEngine() {
        scope.launch {
            AudioEngine.positionMs.collect { ms ->
                position.report(ms)
                // The queue moves on by itself at the end of a track.
                if (ms > 0 && durationMsOf(_song.value ?: return@collect) > 0 &&
                    ms >= durationMsOf(_song.value ?: return@collect) - 250 &&
                    !AudioEngine.playing.value
                ) {
                    advance(natural = true)
                }
            }
        }
    }

    fun togglePlayPause() {
        if (AudioEngine.playing.value) AudioEngine.pause() else AudioEngine.resume()
    }

    fun next() = advance(natural = false)

    fun previous() {
        val songs = _queue.value
        if (songs.isEmpty()) return
        val at = _index.value
        // Back within the first few seconds restarts; beyond that it steps back,
        // which is what every player does and what the phone's does.
        if (position.positionMs > 3_000) {
            play(at)
        } else {
            play(if (at > 0) at - 1 else songs.lastIndex)
        }
    }

    private fun advance(natural: Boolean) {
        val songs = _queue.value
        if (songs.isEmpty()) return
        val at = _index.value
        when {
            _repeatMode.value == 2 && natural -> play(at)
            _repeatMode.value == 1 && at == songs.lastIndex -> play(0)
            _shuffle.value -> play(songs.indices.filter { it != at }.randomOrNull() ?: at)
            at < songs.lastIndex -> play(at + 1)
            natural && _autoplay.value -> play(0)
            else -> AudioEngine.stop()
        }
    }

    fun toggleShuffle() {
        _shuffle.value = !_shuffle.value
    }

    fun cycleRepeat() {
        _repeatMode.value = (_repeatMode.value + 1) % 3
    }

    fun toggleAutoplay() {
        _autoplay.value = !_autoplay.value
    }

    fun seekTo(ms: Long) {
        // A seek restarts ffmpeg at the offset; the engine does not yet take one,
        // so the playhead moves and the next resolve lands there.
        AudioEngine.seek(ms)
        position.report(ms)
    }

    fun seekToFraction(fraction: Float) {
        val total = durationMsOf(_song.value ?: return)
        if (total > 0) seekTo((total * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun jumpTo(position: Int) {
        if (position in _queue.value.indices) play(position)
    }

    fun removeAt(position: Int) {
        val songs = _queue.value
        if (position !in songs.indices) return
        val next = songs.toMutableList().apply { removeAt(position) }
        val wasCurrent = position == _index.value
        _queue.value = next
        when {
            next.isEmpty() -> {
                _index.value = -1
                retarget()
                AudioEngine.stop()
            }
            position < _index.value -> {
                _index.value -= 1
                retarget()
            }
            wasCurrent -> play(_index.value.coerceIn(next.indices))
            else -> retarget()
        }
    }

    fun move(from: Int, to: Int) {
        val songs = _queue.value.toMutableList()
        if (from !in songs.indices || to !in songs.indices) return
        val current = _song.value
        songs.add(to, songs.removeAt(from))
        _queue.value = songs
        _index.value = songs.indexOfFirst { it.videoId == current?.videoId }
        retarget()
    }

    fun clearQueue() {
        _queue.value = emptyList()
        _index.value = -1
        retarget()
        AudioEngine.stop()
    }

    fun toggleLike() {
        // The write itself is a signed-in call the queue does not yet make; the
        // control answers so the player is not dead under the finger.
        _like.value = if (_like.value == LikeStatus.LIKE) LikeStatus.INDIFFERENT else LikeStatus.LIKE
    }

    /** "3:45" or "1:02:03"; blank while the row stated no length. */
    fun durationMsOf(song: Song): Long {
        val text = song.durationText ?: return 0L
        val parts = text.split(':').mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
            else -> 0L
        }
    }

    /** Warms the client so the first Home request is not also the first network call. */
    suspend fun warmUp(): Result<Unit> = runCatching {
        YtMusicRepository.home().getOrThrow()
        Unit
    }
}
