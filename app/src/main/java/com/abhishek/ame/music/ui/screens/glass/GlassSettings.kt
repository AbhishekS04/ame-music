package com.abhishek.ame.music.ui.screens.glass

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abhishek.ame.music.ui.glass.GlassEffectConfig
import com.abhishek.ame.music.ui.glass.GlassStyle
import com.abhishek.ame.music.data.GlassProfile
import com.abhishek.ame.music.data.GlassStore
import com.abhishek.ame.music.data.settings.AppSettings
import com.abhishek.ame.music.ui.icons.AmeIcons
import com.abhishek.ame.music.ui.glass.backdrop.Backdrop
import com.abhishek.ame.music.ui.glass.backdrop.backdrops.layerBackdrop
import com.abhishek.ame.music.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import com.abhishek.ame.music.ui.glass.LENS_MAX_DP
import com.abhishek.ame.music.ui.glass.isGlassSupported
import com.abhishek.ame.music.ui.glass.liquidGlass
import com.abhishek.ame.music.ui.glass.shapes.ContinuousCapsule
import com.abhishek.ame.music.ui.icons.SfIcons
import kotlin.math.roundToInt

val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val InkDim: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

fun Modifier.softShadow(shape: androidx.compose.ui.graphics.Shape, elevation: Dp = 6.dp): Modifier = shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = Color.Black.copy(alpha = 0.10f),
    spotColor = Color.Black.copy(alpha = 0.13f),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassPage(
    store: GlassStore,
    backdrop: Backdrop,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onBack: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Liquid Glass",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
            ),
        )

        if (!isGlassSupported()) {
            Text(
                "Hardware blur & AGSL runtime shaders require Android 12 (API 31)+. A translucent fallback is active.",
                style = MaterialTheme.typography.bodySmall,
                color = InkDim,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            item("glass_content") {
                GlassPageContent(store = store, backdrop = backdrop)
            }
        }
    }
}

@Composable
fun GlassPageContent(
    store: GlassStore,
    backdrop: Backdrop,
) {
    val profile by store.profile.collectAsStateWithLifecycle()
    val config by store.config.collectAsStateWithLifecycle()
    val liquidGlassEnabled by AppSettings.liquidGlass.collectAsStateWithLifecycle()
    val progressiveBlur by AppSettings.progressiveBlur.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val liquid = config.style != GlassStyle.TRANSPARENT && liquidGlassEnabled && !reduceDynamicBlur

    Column(Modifier.fillMaxWidth()) {
        GlassPreview(config = config, backdrop = backdrop)

        SettingsSection("Engine Controls") {
            GlassSwitch(
                label = "Liquid Glass",
                checked = liquidGlassEnabled,
                icon = SfIcons.AutoAwesome,
                onChange = { enabled ->
                    AppSettings.setLiquidGlass(enabled)
                    store.setGlobalEnabled(enabled)
                },
            )
            SettingsDivider()
            GlassSwitch(
                label = "Progressive Blur",
                checked = progressiveBlur,
                icon = SfIcons.BlurOn,
                onChange = { AppSettings.setProgressiveBlur(it) },
            )
            SettingsDivider()
            GlassSwitch(
                label = "Reduce Dynamic Blur",
                checked = reduceDynamicBlur,
                icon = SfIcons.BlurOff,
                onChange = { AppSettings.setReduceDynamicBlur(it) },
            )
        }

        SettingsSection("Material & Quality") {
            GlassProfile.entries.forEachIndexed { index, entry ->
                if (index > 0) SettingsDivider()
                SettingsChoiceRow(
                    label = entry.name,
                    selected = entry == profile,
                ) { store.setProfile(entry) }
            }
            SettingsDivider()
            GlassSlider(
                label = "Vibrancy",
                icon = SfIcons.BlurOn,
                value = config.vibrancy,
                range = 0f..2f,
                readout = { "${(it * 50).roundToInt()}%" },
                onChange = store::setVibrancy,
            )
            if (liquid) {
                SettingsDivider()
                GlassSlider(
                    label = "Blur Radius",
                    icon = SfIcons.BlurOff,
                    value = config.blurRadius,
                    range = 0f..24f,
                    readout = { "${it.roundToInt()} dp" },
                    onChange = store::setBlur,
                )
            }
        }

        SettingsSection("Colour & Film") {
            TintRow(
                hue = config.tintHue,
                strength = config.tintStrength,
                fromSystem = store.tintFromSystem,
                onHue = store::setTintHue,
                onSystem = store::setTintFromSystem,
                onStrength = store::setTintStrength,
            )
            SettingsDivider()
            GlassSlider(
                label = "Surface Opacity",
                icon = SfIcons.Gradient,
                value = config.surfaceOpacity,
                range = 0f..1f,
                readout = { "${(it * 100).roundToInt()}%" },
                onChange = store::setSurfaceOpacity,
            )
            SettingsDivider()
            GlassSlider(
                label = "Puck Indicator Opacity",
                icon = SfIcons.SmartButton,
                value = config.puckOpacity,
                range = 0f..1f,
                readout = { "${(it * 100).roundToInt()}%" },
                onChange = store::setPuckOpacity,
            )
        }

        if (liquid) {
            SettingsSection("Refraction & Edges") {
                GlassSlider(
                    label = "Specular Rim",
                    icon = SfIcons.AutoAwesome,
                    value = config.highlightOpacity,
                    range = 0f..1f,
                    readout = { "${(it * 100).roundToInt()}%" },
                    onChange = store::setHighlightOpacity,
                )
                SettingsDivider()
                GlassSlider(
                    label = "Refraction Depth",
                    icon = SfIcons.Waves,
                    value = config.lensHeight,
                    range = 0f..1f,
                    readout = { "${(it * LENS_MAX_DP).roundToInt()} dp" },
                    onChange = store::setLensHeight,
                )
                SettingsDivider()
                GlassSlider(
                    label = "Refraction Amount",
                    icon = SfIcons.Tune,
                    value = config.lensAmount,
                    range = 0f..1f,
                    readout = { "${(it * LENS_MAX_DP).roundToInt()} dp" },
                    onChange = store::setLensAmount,
                )
                SettingsDivider()
                GlassSwitch(
                    label = "Chromatic Dispersion",
                    icon = SfIcons.Brightness,
                    checked = config.chromaticAberration,
                    onChange = store::setChromaticAberration,
                )
                SettingsDivider()
                GlassSwitch(
                    label = "Depth Pinch",
                    icon = SfIcons.Album,
                    checked = config.depthEffect,
                    onChange = store::setDepthEffect,
                )
            }
        }

        SettingsSection(null) {
            Text(
                "Reset to Defaults",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { store.reset() }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            )
        }
    }
}

/**
 * Pinned preview stage: Creates a high-contrast synthetic backdrop of angled colors
 * and hard edge bars so refraction, chromatic dispersion, and lens curvature pop visually.
 */
@Composable
private fun GlassPreview(config: GlassEffectConfig, backdrop: Backdrop) {
    val barConfig = if (config.navBarEnabled) config else config.copy(style = GlassStyle.TRANSPARENT)
    val stage = rememberLayerBackdrop()
    val accent = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // High-contrast background canvas recorded into stage
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .layerBackdrop(stage)
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent,
                            Color(0xFFFF7A45),
                            Color(0xFF2ED3C6),
                            Color(0xFF1B1B1B),
                        ),
                    ),
                ),
        ) {
            Column(
                Modifier
                    .matchParentSize()
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(6) { index ->
                    Box(
                        Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.94f else 0.7f)
                            .height(5.dp)
                            .background(
                                if (index % 2 == 0) Color.White.copy(alpha = 0.85f)
                                else Color.Black.copy(alpha = 0.5f),
                            ),
                    )
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Glass Playing Pill Preview
            Row(
                Modifier
                    .fillMaxWidth(0.86f)
                    .height(46.dp)
                    .liquidGlass(
                        config = if (config.miniPlayerEnabled) config else config.copy(style = GlassStyle.TRANSPARENT),
                        shape = ContinuousCapsule(),
                        highlightAlpha = 0.3f,
                        ownBackdrop = stage,
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.25f)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth(0.55f).height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.85f)))
                    Box(Modifier.fillMaxWidth(0.35f).height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.55f)))
                }
                Icon(SfIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }

            // Glass Navigation Bar Preview
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(0.94f)
                    .liquidGlass(
                        config = barConfig,
                        shape = ContinuousCapsule(),
                        highlightAlpha = 0.3f,
                        ownBackdrop = stage,
                    )
                    .padding(vertical = 5.dp, horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewTab(AmeIcons.Home, "Home", selected = true, config = barConfig)
                PreviewTab(AmeIcons.TabExplore, "Explore", selected = false, config = barConfig)
                PreviewTab(AmeIcons.TabLibrary, "Library", selected = false, config = barConfig)
                PreviewTab(AmeIcons.TabSearch, "Search", selected = false, config = barConfig)
                PreviewTab(SfIcons.Settings, "Settings", selected = false, config = barConfig)
            }
        }
    }
}

@Composable
private fun RowScope.PreviewTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    config: GlassEffectConfig,
) {
    val wash = if (config.puckColor.isSpecified) config.puckColor else Color.White
    Box(
        Modifier
            .weight(1f, fill = true)
            .height(44.dp)
            .then(
                if (selected) {
                    Modifier
                        .clip(ContinuousCapsule())
                        .background(wash.copy(alpha = if (config.puckOpacity > 0f) config.puckOpacity else 0.30f))
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color.White.copy(alpha = 0.70f),
                modifier = Modifier.size(19.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.70f),
            )
        }
    }
}

@Composable
private fun TintRow(
    hue: Float,
    strength: Float,
    fromSystem: Boolean,
    onHue: (Float?) -> Unit,
    onSystem: () -> Unit,
    onStrength: (Float) -> Unit,
) {
    val on = hue >= 0f
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RowIcon(SfIcons.Appearance)
            Text(
                "Tint",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (on) "Clear" else "None",
                style = MaterialTheme.typography.bodyMedium,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(ContinuousCapsule())
                    .clickable(enabled = on) { onHue(null) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        var dragHue by remember { mutableStateOf<Float?>(null) }
        var lastWrite by remember { mutableLongStateOf(0L) }
        val shown = dragHue ?: hue
        val hues = remember {
            List(13) { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 360f / 12, 0.8f, 1f))) }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 2.dp)
                .height(30.dp),
        ) {
            val width = constraints.maxWidth.toFloat()
            val pick = { x: Float, settled: Boolean ->
                val picked = (x / width).coerceIn(0f, 1f) * 360f
                dragHue = picked
                val now = android.os.SystemClock.uptimeMillis()
                if (settled || now - lastWrite >= 250L) {
                    lastWrite = now
                    onHue(picked)
                }
                if (settled) dragHue = null
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .align(Alignment.CenterStart)
                    .clip(ContinuousCapsule())
                    .background(Brush.horizontalGradient(hues))
                    .pointerInput(width) { detectTapGestures { pick(it.x, true) } }
                    .pointerInput(width) {
                        detectHorizontalDragGestures(
                            onDragEnd = { dragHue?.let { pick(width * (it / 360f), true) } },
                            onDragCancel = { dragHue = null },
                        ) { change, _ -> pick(change.position.x, false) }
                    },
            )
            if (on || dragHue != null) {
                val x = with(LocalDensity.current) { (width * (shown / 360f)).toDp() }
                Box(
                    Modifier
                        .offset(x = x - 11.dp)
                        .size(22.dp)
                        .clip(ContinuousCapsule())
                        .background(Color.White)
                        .padding(3.dp)
                        .clip(ContinuousCapsule())
                        .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(shown, 0.8f, 1f))))
                        .align(Alignment.CenterStart),
                )
            }
        }

        Row(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val accentColor = Color(context.getColor(android.R.color.system_accent1_400))
                Swatch(colour = accentColor, selected = fromSystem, onClick = onSystem)
            }
            listOf(0f, 32f, 52f, 130f, 186f, 220f, 268f, 310f).forEach { preset ->
                Swatch(
                    colour = Color(android.graphics.Color.HSVToColor(floatArrayOf(preset, 0.8f, 1f))),
                    selected = !fromSystem && on && kotlin.math.abs(hue - preset) < 1f,
                    onClick = { onHue(preset) },
                )
            }
        }

        if (on) {
            GlassSlider(
                label = "Tint Strength",
                value = strength,
                range = 0f..1f,
                readout = { "${(it * 100).roundToInt()}%" },
                onChange = onStrength,
                padded = false,
            )
        }
    }
}

@Composable
private fun Swatch(colour: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(ContinuousCapsule())
            .background(if (selected) Ink else Color.Transparent)
            .padding(if (selected) 3.dp else 0.dp)
            .clip(ContinuousCapsule())
            .background(colour)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun GlassSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: (Float) -> String,
    icon: ImageVector? = null,
    onChange: (Float) -> Unit,
    padded: Boolean = true,
) {
    Column(if (padded) Modifier.padding(horizontal = 18.dp, vertical = 8.dp) else Modifier.padding(top = 10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon?.let { RowIcon(it) }
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                readout(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PlainSlider(
            value = value,
            range = range,
            onChange = onChange,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun GlassSwitch(
    label: String,
    checked: Boolean,
    icon: ImageVector? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon?.let { RowIcon(it) }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        PlainToggle(checked = checked, onChange = onChange)
    }
}

@Composable
fun SettingsSection(title: String?, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        if (title != null) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            content = { content() },
        )
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        modifier = Modifier.padding(start = 60.dp, end = 18.dp),
    )
}

@Composable
fun RowIcon(icon: ImageVector) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
    }
}

@Composable
fun SettingsChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(SfIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun PlainSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    BoxWithConstraints(modifier.fillMaxWidth().height(28.dp)) {
        val width = constraints.maxWidth.toFloat()
        val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
        val pick = { x: Float -> onChange(range.start + (x / width).coerceIn(0f, 1f) * span) }

        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .align(Alignment.CenterStart)
                .clip(ContinuousCapsule())
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .align(Alignment.CenterStart)
                .clip(ContinuousCapsule())
                .background(accent),
        )
        Box(
            Modifier
                .offset(x = with(LocalDensity.current) { (width * fraction).toDp() } - 11.dp)
                .size(22.dp)
                .align(Alignment.CenterStart)
                .softShadow(ContinuousCapsule(), elevation = 4.dp)
                .clip(ContinuousCapsule())
                .background(Color.White)
                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), ContinuousCapsule()),
        )
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(width, range) { detectTapGestures { pick(it.x) } }
                .pointerInput(width, range) {
                    detectHorizontalDragGestures { change, _ -> pick(change.position.x) }
                },
        )
    }
}

@Composable
fun PlainToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val knob by animateDpAsState(
        if (checked) 22.dp else 2.dp,
        label = "toggleKnob",
    )
    Box(
        Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(ContinuousCapsule())
            .background(if (checked) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
            .clickable { onChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .align(Alignment.CenterStart)
                .size(24.dp)
                .softShadow(ContinuousCapsule(), elevation = 4.dp)
                .clip(ContinuousCapsule())
                .background(Color.White)
                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), ContinuousCapsule()),
        )
    }
}
