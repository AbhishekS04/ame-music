package com.abhishek.ame.music.ui.graphics

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
actual fun rememberSkyShader(
    tonesHigh: Color,
    tonesMain: Color,
    tonesMid: Color,
    tonesLow: Color,
    scale: Float,
    distortion: Float,
    swirl: Float,
): SkyShader? = null

@Composable
actual fun rememberPrismShader(): SkyShader? = null
