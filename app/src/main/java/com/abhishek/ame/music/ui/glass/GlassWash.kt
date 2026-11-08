package com.abhishek.ame.music.ui.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
@Composable
fun chipWash(selected: Boolean): Color {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onBackground
    // Most of the way to the ink: the hue is still there, the saturation
    // mostly is not.
    val quiet = lerp(accent, ink, 0.55f)
    return quiet.copy(alpha = if (selected) 0.32f else 0.20f)
}
