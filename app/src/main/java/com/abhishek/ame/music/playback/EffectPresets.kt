package com.abhishek.ame.music.playback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.abs

@Serializable
data class EffectPreset(
    val id: String,
    val name: String,
    val speed: Float,
    val pitch: Float,
    val reverbAmount: Float = 0f,
    val builtIn: Boolean = false,
) {
    fun matches(speed: Float, pitch: Float, reverb: Float): Boolean =
        abs(this.speed - speed) < 0.01f &&
            abs(this.pitch - pitch) < 0.01f &&
            abs(this.reverbAmount - reverb) < 0.01f
}

/** Built-in audio presets */
val BuiltInPresets = listOf(
    EffectPreset(
        id = "original",
        name = "Original",
        speed = 1.0f,
        pitch = 1.0f,
        reverbAmount = 0.0f,
        builtIn = true,
    ),
    EffectPreset(
        id = "slowed",
        name = "Slowed + Reverb",
        speed = 0.85f,
        pitch = 0.92f,
        reverbAmount = 0.55f,
        builtIn = true,
    ),
    EffectPreset(
        id = "sped_up",
        name = "Sped Up",
        speed = 1.25f,
        pitch = 1.25f,
        reverbAmount = 0.0f,
        builtIn = true,
    ),
)

class EffectPresetStore(context: Context) {
    private val prefs = context.getSharedPreferences("effect_presets", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _presets = MutableStateFlow(BuiltInPresets + loadCustom())
    val presets: StateFlow<List<EffectPreset>> = _presets.asStateFlow()

    fun save(name: String, speed: Float, pitch: Float, reverb: Float): EffectPreset {
        val preset = EffectPreset(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Custom Preset" },
            speed = speed,
            pitch = pitch,
            reverbAmount = reverb,
            builtIn = false,
        )
        _presets.value = _presets.value + preset
        persist()
        return preset
    }

    fun delete(id: String) {
        _presets.value = _presets.value.filterNot { it.id == id && !it.builtIn }
        persist()
    }

    private fun persist() {
        val custom = _presets.value.filter { !it.builtIn }
        prefs.edit().putString("custom_presets", json.encodeToString(serializer, custom)).apply()
    }

    private fun loadCustom(): List<EffectPreset> {
        val raw = prefs.getString("custom_presets", null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    private companion object {
        val serializer = ListSerializer(EffectPreset.serializer())
    }
}
