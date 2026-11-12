package com.abhishek.ame.music.ui.graphics

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

interface SkyShader {
    val brush: Brush
    fun updateTimeAndSize(time: Float, width: Float, height: Float)
}

@Composable
expect fun rememberSkyShader(
    tonesHigh: Color,
    tonesMain: Color,
    tonesMid: Color,
    tonesLow: Color,
    scale: Float = 42f,
    distortion: Float = 46f,
    swirl: Float = 28f,
): SkyShader?

@Composable
expect fun rememberPrismShader(): SkyShader?
