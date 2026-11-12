package com.abhishek.ame.music.playback

import android.media.audiofx.EnvironmentalReverb

/**
 * Mathematical calibration for Android's [EnvironmentalReverb].
 * Translates a normalized 0f..1f slider into a natural acoustic room.
 */
object ReverbTuning {

    /**
     * Configures the room acoustics for [amount] (0.0 = Dry, 1.0 = Cathedral).
     */
    fun tune(effect: EnvironmentalReverb, amount: Float) {
        val level = amount.coerceIn(0f, 1f)

        // 1. Decay Time: 500ms (small room) to 4500ms (large cathedral)
        effect.decayTime = (MIN_DECAY_MS + level * (MAX_DECAY_MS - MIN_DECAY_MS)).toInt()

        // 2. Reverb reflection level in millibels
        effect.reverbLevel = (MIN_LEVEL_MB + level * (MAX_LEVEL_MB - MIN_LEVEL_MB)).toInt().toShort()

        // 3. MASTER ROOM LEVEL: CRITICAL FIX!
        // Android initializes roomLevel to -9000 mB (-90 dB = total silence).
        // We lift this from -2000 mB (-20 dB) up to 0 mB (unity gain).
        effect.roomLevel = (MIN_ROOM_MB + level * (MAX_ROOM_MB - MIN_ROOM_MB)).toInt().toShort()

        // 4. Roll off harsh high-frequency ringing to avoid a metallic wash
        effect.roomHFLevel = (-400).toShort()

        // 5. Density and diffusion: maximum richness
        effect.decayHFRatio = 700
        effect.density = 1000
        effect.diffusion = 1000
    }

    /**
     * Calculates the non-linear auxiliary send level for [amount].
     * Uses a quadratic curve because the human ear hears volume logarithmically.
     */
    fun sendLevel(amount: Float): Float {
        val level = amount.coerceIn(0f, 1f)
        return level * level * 0.85f + level * 0.15f
    }

    private const val MIN_DECAY_MS = 500f
    private const val MAX_DECAY_MS = 4500f

    private const val MIN_LEVEL_MB = -2600f
    private const val MAX_LEVEL_MB = -200f

    private const val MIN_ROOM_MB = -2000f
    private const val MAX_ROOM_MB = 0f
}
