package com.abhishek.ame.music.desktop

import com.abhishek.ame.music.data.lyrics.LyricsSource
import com.abhishek.ame.music.data.settings.AudioQuality
import com.abhishek.ame.music.data.settings.LastPlayerScreen
import com.abhishek.ame.music.data.settings.MixBlend
import com.abhishek.ame.music.data.settings.MixTransitionView
import com.abhishek.ame.music.data.settings.SmartAnalysis
import com.abhishek.ame.music.data.settings.TransitionWindow
import com.abhishek.ame.music.ui.player.PlayerSettingsSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.prefs.Preferences

/**
 * The desktop's settings store, standing in for the phone's AppSettings.
 *
 * AppSettings is 2,299 lines and leans on SharedPreferences, ConnectivityManager
 * and media3 — none of which exist here. What the shared UI actually reads is
 * [PlayerSettingsSource], a named, typed view of the handful of values it needs,
 * so this implements that interface directly and persists the two the player
 * writes back.
 *
 * Values are the phone's defaults; a desktop settings screen is a later phase.
 * The rest are in memory only, which is honest: nothing here claims to survive a
 * restart except what is actually written to disk.
 */
object DesktopSettings : PlayerSettingsSource {

    private val prefs: Preferences = Preferences.userRoot().node("com/abhishek/ame/music/desktop")

    private fun bool(key: String, default: Boolean) =
        MutableStateFlow(prefs.getBoolean(key, default))

    private fun int(key: String, default: Int) =
        MutableStateFlow(prefs.getInt(key, default))

    override val animatedCanvas = bool("animatedCanvas", true)
    override val audioQualityCellular = MutableStateFlow(AudioQuality.MEDIUM)
    override val audioQualityWifi = MutableStateFlow(AudioQuality.HIGH)
    override val canvasOverCellular = bool("canvasOverCellular", false)
    override val fullBleedArtwork = bool("fullBleedArtwork", false)
    override val hideSongStatus = bool("hideSongStatus", false)
    override val showQueueHistory = bool("showQueueHistory", true)
    override val hideVolumeBar = bool("hideVolumeBar", false)

    /** There is no status bar on a desktop window. */
    override val hideStatusBar: StateFlow<Boolean> = MutableStateFlow(false)

    private val _lastPlayerScreen = MutableStateFlow(
        runCatching { LastPlayerScreen.valueOf(prefs.get("lastPlayerScreen", "MAIN")) }
            .getOrDefault(LastPlayerScreen.MAIN),
    )
    override val lastPlayerScreen: StateFlow<LastPlayerScreen> = _lastPlayerScreen

    override val legacyMeshGradient = bool("legacyMeshGradient", false)
    override val lyricsBlur = bool("lyricsBlur", false)
    private val _lyricsOffsetMs = int("lyricsOffsetMs", 0)
    override val lyricsOffsetMs: StateFlow<Int> = _lyricsOffsetMs
    override val lyricsSourceOrder = MutableStateFlow(LyricsSource.entries.toList())

    /** A wired desktop is never on a metered connection. */
    override val meteredConnection: StateFlow<Boolean?> = MutableStateFlow(false)
    override val streamCeilingOverride: StateFlow<AudioQuality?> = MutableStateFlow(null)
    override val preferUsbDac = bool("preferUsbDac", false)
    override val prioritizeSpotifyCanvas = bool("prioritizeSpotifyCanvas", false)
    override val reduceAnimation = bool("reduceAnimation", false)
    override val reduceDynamicBlur = bool("reduceDynamicBlur", false)
    override val showNerdStats = bool("showNerdStats", false)
    override val showListenTogetherButton = bool("showListenTogetherButton", true)
    override val smartAnalysis = MutableStateFlow(SmartAnalysis())
    override val automixDetail: StateFlow<String?> = MutableStateFlow(null)
    override val smartFadeEnabled = bool("smartFadeEnabled", true)
    override val smartMixInProgress = MutableStateFlow(false)
    override val smartMixBlend: StateFlow<MixBlend?> = MutableStateFlow(null)
    override val smartMixView: StateFlow<MixTransitionView?> = MutableStateFlow(null)
    override val smartMixProgress = MutableStateFlow(0f)
    override val smartTransitionWindow: StateFlow<TransitionWindow?> = MutableStateFlow(null)
    override val spotifyCanvasAutoHide = bool("spotifyCanvasAutoHide", true)
    override val syncedLyrics = bool("syncedLyrics", true)
    override val translationLanguage = MutableStateFlow("en")
    override val versionAlignmentInProgress = MutableStateFlow(false)
    override val musicFirst = bool("musicFirst", true)

    override fun setLastPlayerScreen(value: LastPlayerScreen) {
        _lastPlayerScreen.value = value
        prefs.put("lastPlayerScreen", value.name)
    }

    override fun setLyricsOffsetMs(value: Int) {
        _lyricsOffsetMs.value = value
        prefs.putInt("lyricsOffsetMs", value)
    }
}
