package com.abhishek.ame.music.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.abhishek.ame.music.data.canvas.CanvasArtwork
import com.abhishek.ame.music.data.lyrics.LyricLine
import com.abhishek.ame.music.data.model.Song
import com.abhishek.ame.music.ui.player.AudioOutputDevice
import com.abhishek.ame.music.ui.player.AudioOutputKind
import com.abhishek.ame.music.ui.player.CanvasVideoSpec
import com.abhishek.ame.music.ui.player.LocalPlayerFrost
import com.abhishek.ame.music.ui.player.LyricsRomanizationResult
import com.abhishek.ame.music.ui.player.LyricsShareRequest
import com.abhishek.ame.music.ui.player.LyricsTranslationResult
import com.abhishek.ame.music.ui.player.OutputFormatUi
import com.abhishek.ame.music.ui.player.PartyUi
import com.abhishek.ame.music.ui.player.PlayerFrost
import com.abhishek.ame.music.ui.player.PlayerHost
import com.abhishek.ame.music.ui.player.PlayerSettingsSource
import com.abhishek.ame.music.ui.player.SystemVolume
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the shared player reads from the desktop.
 *
 * Everything the platform can't do is left at the interface's own default: no
 * music video, no casting, no lyrics share card. Those defaults exist precisely
 * so a platform can decline, and declining is more honest than shipping a
 * control that does nothing.
 */
class DesktopPlayerHost : PlayerHost {

    override val settings: PlayerSettingsSource = DesktopSettings

    override fun cachedCanvas(song: Song): CanvasArtwork? = null

    override suspend fun canvasFor(song: Song): CanvasArtwork? = null

    /** No video decoder wired up yet, so no animated canvas either. */
    @Composable
    override fun CanvasVideo(spec: CanvasVideoSpec, modifier: Modifier) = Unit

    @Composable
    override fun rememberRemoteArtworkUrl(song: Song?): String? = song?.thumbnailUrl

    override val volume: SystemVolume = object : SystemVolume {
        private val _level = MutableStateFlow(1f)
        override val level: StateFlow<Float> = _level

        override fun set(level: Float) {
            _level.value = level.coerceIn(0f, 1f)
            AudioEngine.setVolume(_level.value)
        }

        override fun refresh() = Unit
    }

    @Composable
    override fun rememberAudioOutputs(): List<AudioOutputDevice> = remember {
        // The default speaker. Enumerating a Linux mixer's real outputs means
        // PulseAudio/PipeWire bindings; until then this states the truth.
        listOf(
            AudioOutputDevice(
                id = 0,
                name = "System output",
                kind = AudioOutputKind.OTHER,
                isActive = true,
            ),
        )
    }

    @Composable
    override fun rememberOutputPicker(onOpen: () -> Unit): () -> Unit = remember(onOpen) { onOpen }

    override fun selectAudioOutput(id: Int?) = Unit

    override val outputFormat: StateFlow<OutputFormatUi> = MutableStateFlow(
        OutputFormatUi(summary = "16-bit PCM · 48 kHz"),
    )

    override val party: StateFlow<PartyUi> = MutableStateFlow(PartyUi())

    /** Offline until the lyrics pipeline is wired to a translator; says so rather than failing quietly. */
    override suspend fun translateLyrics(
        trackId: String,
        lines: List<LyricLine>,
        targetLanguageTag: String,
    ): LyricsTranslationResult = LyricsTranslationResult.Unavailable

    override suspend fun romanizeLyrics(
        trackId: String,
        lines: List<LyricLine>,
        targetLanguageTag: String,
    ): LyricsRomanizationResult = LyricsRomanizationResult.Unavailable

    override fun showMessage(message: String) {
        MessageBus.show(message)
    }

    @Composable
    override fun AudioPipelineDialog(hazeState: HazeState, isPlaying: Boolean, onDismiss: () -> Unit) = Unit

    @Composable
    override fun rememberPlayerFrost(): PlayerFrost? = null

    override val lyricsShareAvailable: Boolean = false

    @Composable
    override fun LyricsShareSheet(hazeState: HazeState, request: LyricsShareRequest, onDismiss: () -> Unit) = Unit
}
