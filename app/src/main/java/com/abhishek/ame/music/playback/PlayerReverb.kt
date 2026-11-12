package com.abhishek.ame.music.playback

import android.media.audiofx.EnvironmentalReverb
import androidx.annotation.OptIn
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Manages the lifecycle, volume ducking synchronization, and ExoPlayer auxiliary
 * effect attachment of Android's [EnvironmentalReverb].
 */
@OptIn(UnstableApi::class)
class PlayerReverb {

    private var effect: EnvironmentalReverb? = null
    private var amount = 0f
    private var watched: ExoPlayer? = null

    private val levelListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val player = watched ?: return
            if (playWhenReady) {
                // Re-attach room when resuming playback
                if (amount > 0f && effect == null) apply(player, amount)
            } else {
                // Flush reverb tail immediately on pause so it doesn't ring over next play
                drop(player)
            }
        }

        override fun onVolumeChanged(volume: Float) {
            val player = watched ?: return
            val room = amount
            if (room <= 0f) return
            runCatching {
                player.setAuxEffectInfo(
                    AuxEffectInfo(
                        effect?.id ?: return,
                        ReverbTuning.sendLevel(room) * volume.coerceIn(0f, 1f),
                    ),
                )
            }
        }
    }

    /**
     * Applies [amount] (0f to 1f) of reverb to [player].
     */
    fun apply(player: ExoPlayer, amount: Float) {
        val level = amount.coerceIn(0f, 1f)
        this.amount = level
        if (level <= 0f) {
            detach(player)
            return
        }

        if (watched !== player) {
            watched?.runCatching { removeListener(levelListener) }
            watched = player
            runCatching { player.addListener(levelListener) }
        }

        // If paused, wait until playback starts before creating native effect
        if (!player.playWhenReady) return

        val existing = effect
        val reverb = existing ?: runCatching {
            // AudioSession 0 binds to output mix
            EnvironmentalReverb(0, 0)
        }.onFailure {
            android.util.Log.w(TAG, "Reverb effect unavailable on this hardware: ${it.message}")
        }.getOrNull() ?: return

        effect = reverb

        runCatching {
            ReverbTuning.tune(reverb, level)
            reverb.enabled = true
            player.setAuxEffectInfo(
                AuxEffectInfo(
                    reverb.id,
                    ReverbTuning.sendLevel(level) * player.volume.coerceIn(0f, 1f),
                ),
            )
        }.onFailure {
            android.util.Log.w(TAG, "Failed to apply reverb: ${it.message}")
        }
    }

    /** Releases the native reverb and frees system audio DSP hardware. */
    fun release(player: ExoPlayer?) {
        player?.let(::detach)
        effect?.runCatching {
            enabled = false
            release()
        }
        effect = null
    }

    private fun detach(player: ExoPlayer) {
        if (watched === player) {
            runCatching { player.removeListener(levelListener) }
            watched = null
        }
        drop(player)
    }

    private fun drop(player: ExoPlayer) {
        runCatching {
            player.setAuxEffectInfo(AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, 0f))
        }
        effect?.runCatching {
            enabled = false
            release()
        }
        effect = null
    }

    private companion object {
        const val TAG = "PlayerReverb"
    }
}
