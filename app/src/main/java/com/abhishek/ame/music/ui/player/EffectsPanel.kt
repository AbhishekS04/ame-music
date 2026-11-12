package com.abhishek.ame.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abhishek.ame.music.playback.EffectPreset
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Real-time Slowed + Reverb Audio Effect Control Panel.
 *
 * Faithfully matches Ame's studio aesthetic with preset pills, logarithmic
 * speed & semitone pitch ratios, calibrated environmental reverb, and white capsule thumbs.
 */
@Composable
fun EffectsPanel(
    speed: Float,
    pitch: Float,
    reverb: Float,
    presets: List<EffectPreset>,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onReverb: (Float) -> Unit,
    onApplyPreset: (EffectPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        // 1. Presets Header
        Text(
            text = "PRESETS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                fontSize = 12.sp,
            ),
            color = Color(0xFF8E8E93),
        )

        Spacer(Modifier.height(14.dp))

        // 2. Presets Row (Original, Slowed + Reverb, Sped Up)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            presets.forEach { preset ->
                val selected = preset.matches(speed, pitch, reverb)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (selected) Color(0xFF3A3A3C)
                            else Color.Transparent,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onApplyPreset(preset)
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 15.sp,
                        ),
                        color = if (selected) Color.White else Color(0xFF8E8E93),
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // 3. Speed Slider (Logarithmic Ratio scale)
        AmeEffectSlider(
            label = "SPEED",
            value = speed,
            scale = RatioScale,
            reading = { "%.2f×".format(it) },
            onChange = onSpeed,
            onReset = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onSpeed(1.0f)
            },
        )

        Spacer(Modifier.height(24.dp))

        // 4. Pitch Slider (Formatted in Semitones)
        AmeEffectSlider(
            label = "PITCH",
            value = pitch,
            scale = RatioScale,
            reading = { formatSemitones(it) },
            onChange = onPitch,
            onReset = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onPitch(1.0f)
            },
        )

        Spacer(Modifier.height(24.dp))

        // 5. Reverb Slider (Linear percentage)
        AmeEffectSlider(
            label = "REVERB",
            value = reverb,
            scale = LinearScale,
            reading = { if (it <= 0.01f) "Off" else "${(it * 100).roundToInt()}%" },
            onChange = onReverb,
            onReset = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onReverb(0.0f)
            },
        )

        Spacer(Modifier.height(16.dp))
    }
}

// Logarithmic scaling so 1.0x sits exactly in the middle of 0.5x .. 2.0x
interface SliderScale {
    val positions: ClosedFloatingPointRange<Float>
    fun position(value: Float): Float
    fun value(position: Float): Float
}

object RatioScale : SliderScale {
    override val positions = -1f..1f
    override fun position(value: Float) = (ln(value.coerceIn(0.5f, 2.0f).toDouble()) / ln(2.0)).toFloat()
    override fun value(position: Float) = 2f.pow(position.coerceIn(-1f, 1f))
}

object LinearScale : SliderScale {
    override val positions = 0f..1f
    override fun position(value: Float) = value.coerceIn(0f, 1f)
    override fun value(position: Float) = position.coerceIn(0f, 1f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmeEffectSlider(
    label: String,
    value: Float,
    scale: SliderScale,
    reading: (Float) -> String,
    onChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    val shown = dragged ?: value

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 12.sp,
                ),
                color = Color(0xFF8E8E93),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = reading(shown),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
                color = Color.White,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = "Reset",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                ),
                color = Color(0xFF8E8E93),
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onReset,
                    )
                    .padding(vertical = 4.dp, horizontal = 2.dp),
            )
        }

        Spacer(Modifier.height(6.dp))

        // Custom Slider with rounded white pill thumb
        Slider(
            value = scale.position(shown),
            onValueChange = { pos ->
                val v = scale.value(pos)
                dragged = v
                onChange(v)
            },
            onValueChangeFinished = { dragged = null },
            valueRange = scale.positions,
            thumb = {
                Box(
                    modifier = Modifier
                        .size(width = 30.dp, height = 18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFF636366),
                        inactiveTrackColor = Color(0xFF3A3A3C),
                    ),
                )
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF636366),
                inactiveTrackColor = Color(0xFF3A3A3C),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun formatSemitones(pitch: Float): String {
    val semitones = 12.0 * (ln(pitch.toDouble()) / ln(2.0))
    val rounded = (semitones * 10).roundToInt() / 10.0
    return if (rounded >= 0) "+%.1f st".format(rounded) else "%.1f st".format(rounded)
}
