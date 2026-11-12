package com.abhishek.ame.music.playback

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global audio effects state store shared across UI and PlaybackService.
 * Synchronously persisted to survive activity or process recreation.
 */
object AudioEffects {

    private var prefs: SharedPreferences? = null

    private val _reverb = MutableStateFlow(0f)
    val reverb: StateFlow<Float> = _reverb.asStateFlow()

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _pitch = MutableStateFlow(1f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    fun load(context: Context) {
        if (prefs != null) return
        val store = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        prefs = store
        _reverb.value = store.getFloat(KEY_REVERB, 0f)
        _speed.value = store.getFloat(KEY_SPEED, 1f)
        _pitch.value = store.getFloat(KEY_PITCH, 1f)
    }

    fun setReverb(amount: Float) {
        val wanted = amount.coerceIn(0f, 1f)
        if (wanted == _reverb.value) return
        _reverb.value = wanted
        prefs?.edit()?.putFloat(KEY_REVERB, wanted)?.apply()
    }

    fun rememberSpeedAndPitch(speed: Float, pitch: Float) {
        val store = prefs ?: return
        val clampedSpeed = speed.coerceIn(0.25f, 3.0f)
        val clampedPitch = pitch.coerceIn(0.25f, 3.0f)
        if (clampedSpeed == _speed.value && clampedPitch == _pitch.value) return
        _speed.value = clampedSpeed
        _pitch.value = clampedPitch
        store.edit()
            .putFloat(KEY_SPEED, clampedSpeed)
            .putFloat(KEY_PITCH, clampedPitch)
            .apply()
    }

    private const val FILE_NAME = "audio_effects_prefs"
    private const val KEY_REVERB = "reverb"
    private const val KEY_SPEED = "speed"
    private const val KEY_PITCH = "pitch"
}
