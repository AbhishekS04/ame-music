/*
 * The bar's structure and its inline/expanded behaviour are
 * EchoMusicApp/Echo-Music's AppFloatingNavBar + FloatingMiniPlayer (GPL-3.0),
 * over the FloatingTabBar vendored in [com.abhishek.ame.music.ui.components.floatingtabbar].
 * Ame's own tabs, song model, transport and haptics are wired through it in
 * place of Echo's Screens routing and PlayerConnection.
 */
@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.abhishek.ame.music.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.abhishek.ame.music.R
import com.abhishek.ame.music.data.model.ROW_ART_PX
import com.abhishek.ame.music.data.model.Song
import com.abhishek.ame.music.data.model.artworkAt
import com.abhishek.ame.music.ui.glass.LiquidBottomTabs
import com.abhishek.ame.music.ui.glass.backdrop.catalog.components.LiquidBottomTab
import com.abhishek.ame.music.ui.glass.floatingtabbar.FloatingTabBar
import com.abhishek.ame.music.ui.glass.floatingtabbar.FloatingTabBarDefaults
import com.abhishek.ame.music.ui.glass.floatingtabbar.FloatingTabBarScrollConnection
import com.abhishek.ame.music.ui.glass.floatingtabbar.LocalGeometricFloatingTabBar
import com.abhishek.ame.music.ui.glass.liquidGlass
import com.abhishek.ame.music.ui.glass.GlassEffectConfig
import com.abhishek.ame.music.ui.glass.LocalGlassEffectConfig
import com.abhishek.ame.music.ui.glass.LocalAppBackdrop
import androidx.compose.ui.graphics.luminance
import com.abhishek.ame.music.ui.haptics.Haptic
import com.abhishek.ame.music.ui.haptics.rememberHaptics
import com.abhishek.ame.music.ui.player.PlayerDock
import com.abhishek.ame.music.ui.player.AndroidMusicVideo
import com.abhishek.ame.music.ui.icons.AmeIcons
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxSize

/**
 * The liquid glass navigation bar: the iOS 26 shape where the now playing
 * controls and the tabs are one component rather than two stacked bars.
 *
 * Expanded, it is a full width now playing pill sitting over the tab pill and a
 * separate circular Search tab. Scrolling down collapses it inline — the tabs
 * fold down to just the selected one, the now playing controls narrow into the
 * gap between it and Search, and the whole thing becomes a single row the width
 * of the screen. Scrolling back up expands it. The fold is driven by
 * [scrollConnection], which the page's scroll has to be dispatched into for any
 * of this to move; see MainActivity's `nestedScroll`.
 *
 * Every surface here samples the app backdrop through [Modifier.liquidGlass], so
 * this is only ever used where that is supported and switched on — off either,
 * MainActivity draws [MiniPlayer] and [FloatingBottomBar] instead.
 *
 * [song] null means nothing is playing, and the accessory is simply absent: the
 * bar is then the tab pill and Search alone, and the collapse still works.
 */
@Composable
fun GlassNavBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    scrollConnection: FloatingTabBarScrollConnection,
    song: Song?,
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    /** @see com.abhishek.ame.music.data.listentogether.ListenTogether.State.controlsLocked */
    controlsLocked: Boolean = false,
    onBlockedControl: () -> Unit = {},
    /** Where the player's artwork lands when it closes into this bar. */
    dock: PlayerDock? = null,
    /** Dragging the bar's player up into the full player. */
    pull: MiniPlayerPull? = null,
    modifier: Modifier = Modifier,
) {
    // Held for the same reason [tabs] is. The glass factory below closes over
    // this shape, and a fresh RoundedCornerShape each pass means a fresh factory
    // lambda, which the tab bar sees as a changed argument and recomposes on.
    val pillShape = remember { RoundedCornerShape(percent = 50) }
    val contentColor = glassContentColor()
    val selectedColor = contentColor
    val unselectedColor = contentColor.copy(alpha = 0.65f)
    val haptics = rememberHaptics()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val backdrop = LocalAppBackdrop.current

    val glassConfig = LocalGlassEffectConfig.current
    val barGlass = Modifier.liquidGlass(
        config = glassConfig,
        shape = pillShape,
        highlightAlpha = 0.3f,
    )
    val filmColor = Color.White.copy(alpha = if (isDark) 0.12f else 0.45f)
    val puckWash = if (isDark) Color.Black.copy(alpha = (glassConfig.puckOpacity * 1.3f).coerceAtMost(0.5f))
                   else Color.Black.copy(alpha = (glassConfig.puckOpacity * 0.13f).coerceAtMost(0.11f))

    val searchIndex = remember(tabs) {
        val idx = tabs.indexOfFirst {
            it.icon == AmeIcons.TabSearch ||
            it.label.equals("Search", ignoreCase = true)
        }
        if (idx >= 0) idx else 3.coerceAtMost(tabs.lastIndex)
    }

    FloatingTabBar(
        selectedTabKey = selectedIndex,
        searchTabKey = searchIndex,
        scrollConnection = scrollConnection,
        modifier = modifier
            .windowInsetsPadding(floatingBarInsets)
            .padding(horizontal = NAV_BAR_GUTTER)
            .padding(bottom = NAV_BAR_LIFT)
            .fillMaxWidth(),
        tabBarContentModifier = barGlass,
        colors = FloatingTabBarDefaults.colors(
            backgroundColor = filmColor,
            accessoryBackgroundColor = filmColor,
            selectedTabBackgroundColor = puckWash,
            selectedStandaloneTabBackgroundColor = puckWash,
        ),
        elevations = FloatingTabBarDefaults.elevations(
            inlineElevation = 0.dp,
            expandedElevation = 0.dp,
        ),
        sizes = FloatingTabBarDefaults.sizes(
            componentSpacing = 14.dp,
            tabSpacing = 0.dp,
            tabBarContentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp),
            tabExpandedContentPadding = PaddingValues(vertical = 8.dp, horizontal = 14.dp),
            tabInlineContentPadding = PaddingValues(14.dp),
        ),
        expandedTabsHeight = 61.dp,
        standaloneInExpanded = false,
        tabsFillWidth = true,
        expandedTabs = { tabsModifier, _ ->
            val here = selectedIndex.coerceAtLeast(0)
            val hereState = rememberUpdatedState(here)
            val selectedTabIndex = remember { { hereState.value } }

            LiquidBottomTabs(
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { index ->
                    if (index != selectedIndex) haptics.play(Haptic.Select)
                    currentOnTabSelected(index)
                },
                backdrop = backdrop,
                tabsCount = tabs.size,
                accentColor = selectedColor,
                containerColor = filmColor,
                indicatorColor = puckWash,
                height = 61.dp,
                lensDepth = 20.dp,
                indicatorVisible = selectedIndex in tabs.indices,
                modifier = tabsModifier,
                drawContainer = !LocalGeometricFloatingTabBar.current,
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = index == selectedIndex
                    val tint = if (isSelected) selectedColor else unselectedColor
                    LiquidBottomTab(
                        onClick = {
                            if (!isSelected) haptics.play(Haptic.Select)
                            currentOnTabSelected(index)
                        },
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(26.dp),
                        )
                        Text(
                            text = tab.label,
                            style = tabLabelStyle(),
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = TAB_ICON_LABEL_GAP),
                        )
                    }
                }
            }
        },
        inlineAccessory = song?.let { current ->
            { accessoryModifier, _ ->
                GlassNowPlaying(
                    song = current,
                    isInline = true,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    contentColor = contentColor,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onExpand = onExpand,
                    controlsLocked = controlsLocked,
                    onBlockedControl = onBlockedControl,
                    dock = dock,
                    pull = pull,
                    modifier = accessoryModifier.then(barGlass),
                )
            }
        },
        expandedAccessory = song?.let { current ->
            { accessoryModifier, _ ->
                GlassNowPlaying(
                    song = current,
                    isInline = false,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    contentColor = contentColor,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onExpand = onExpand,
                    controlsLocked = controlsLocked,
                    onBlockedControl = onBlockedControl,
                    dock = dock,
                    pull = pull,
                    modifier = accessoryModifier.fillMaxWidth().then(barGlass),
                )
            }
        },
        contentKey = remember(selectedIndex, tabs, contentColor) {
            listOf(selectedIndex, tabs, contentColor)
        },
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            val isSearch = index == searchIndex
            val isHome = index == 0
            val tint = if (isSelected || isSearch || isHome) selectedColor else unselectedColor
            val onClick = {
                if (!isSelected) haptics.play(Haptic.Select)
                currentOnTabSelected(index)
            }
            tab(
                key = index,
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(TAB_ICON_SIZE),
                    )
                },
                title = {
                    Text(
                        text = tab.label,
                        style = tabLabelStyle(),
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = TAB_ICON_LABEL_GAP),
                    )
                },
                onClick = onClick,
            )
        }
    }
}

/**
 * The now playing controls docked into [GlassNavBar] as its accessory.
 *
 * Two densities of the same row rather than two components, so the shared
 * element carrying it between the bar's states has one thing to interpolate:
 * [isInline] drops the skip button and draws the play button smaller. The
 * artwork and the type are the same at both, as they are in Apple's accessory,
 * which only changes width between its two placements.
 *
 * The content is [MiniPlayer]'s — same artwork, same transport, same haptics —
 * and not Echo's, which reaches into a player connection this app doesn't have.
 * The press response is Echo's, and belongs to the glass rather than the row:
 * a surface you can push on is the whole point of the material.
 */
@Composable
private fun GlassNowPlaying(
    song: Song,
    isInline: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    contentColor: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    controlsLocked: Boolean,
    onBlockedControl: () -> Unit,
    dock: PlayerDock?,
    pull: MiniPlayerPull?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val pressSource = remember { MutableInteractionSource() }
    val isPressed by pressSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "accessoryPressScale",
    )

    val titleStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = ACCESSORY_TITLE_SIZE,
        fontWeight = ACCESSORY_TITLE_WEIGHT,
        letterSpacing = ACCESSORY_TITLE_TRACKING,
    )
    val artistStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = ACCESSORY_ARTIST_SIZE,
        letterSpacing = ACCESSORY_ARTIST_TRACKING,
    )
    val glyphSlot = if (isInline) 32.dp else 40.dp
    val glyphSize = if (isInline) 24.dp else EXPANDED_GLYPH_SIZE

    Box(
        // Inline, the accessory is stretched to the row's height by the tab bar
        // (`fillMaxHeight` on a row measured at IntrinsicSize.Max), and this row
        // is shorter than that — the tab pill beside it, with a 28dp glyph in
        // 10dp of padding, is the tallest thing in the row and sets the height.
        // A Box defaults to TopStart, so the artwork and the transport sat a
        // couple of dp above the pill's centre line. Expanded the Box wraps its
        // content, so centring is a no-op there.
        contentAlignment = Alignment.Center,
        // The gestures and the tap belong to the whole glass surface, not the
        // row centred in it. Inline, that row is shorter than the pill it sits
        // in, and a pull started in the strip above or below it went nowhere —
        // on a bar only 48dp tall to begin with.
        modifier = Modifier
            // Outside the press scale, which it lifts along with everything else.
            .miniPlayerGestures(
                pull = pull,
                onNext = {
                    haptics.play(Haptic.SkipNext)
                    onNext()
                },
                onPrevious = {
                    haptics.play(Haptic.SkipPrevious)
                    onPrevious()
                },
                locked = controlsLocked,
                onBlocked = onBlockedControl,
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(modifier)
            .clickable(
                interactionSource = pressSource,
                indication = null,
                onClick = onExpand,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    // The artwork sits in the end of a capsule, which curves
                    // away from it; at 8dp its corner met the curve.
                    start = 16.dp,
                    end = if (isInline) 8.dp else 12.dp,
                    // Expanded, the 40dp play button and these come to the 48dp
                    // Apple's accessory is.
                    top = 4.dp,
                    bottom = 4.dp,
                ),
        ) {
            // A music video plays in the cover's place at its own 16:9, as in
            // the floating mini player; the full player takes it over when up.
            val artWidth by animateDpAsState(
                targetValue = if (song.isVideo) ACCESSORY_ART_SIZE * (16f / 9f) else ACCESSORY_ART_SIZE,
                label = "glassMiniArtWidth",
            )
            Box(
                modifier = Modifier
                    .size(width = artWidth, height = ACCESSORY_ART_SIZE)
                    // Whichever of the two densities is on screen is the one
                    // the player flies to: only one is composed at rest.
                    .playerDockArt(dock, ACCESSORY_ART_CORNER)
                    .clip(RoundedCornerShape(ACCESSORY_ART_CORNER))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = rememberRemoteArtworkUrl(song)?.artworkAt(ROW_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (song.isVideo) {
                    AndroidMusicVideo(
                        videoId = song.videoId,
                        fit = false,
                        onRenderedChanged = {},
                        modifier = Modifier.fillMaxSize(),
                        priority = 0,
                    )
                }
            }
            Spacer(Modifier.width(if (isInline) 8.dp else 9.dp))
            // Title and artist at both densities, as Apple Music has them, in its
            // small, openly spaced type (see [ACCESSORY_TITLE_SIZE]). The inline
            // row shares its width with the tab pill and the Search circle, so
            // a long line fades out at its end, as Apple's does.
            Column(Modifier.weight(1f)) {
                val secondary = contentColor.copy(alpha = 0.7f)
                ExplicitSongTitle(
                    song = song,
                    style = titleStyle,
                    color = contentColor,
                    badgeColor = secondary,
                    fadeOverflow = true,
                )
                FadingLine(
                    text = song.artist,
                    style = artistStyle,
                    color = secondary,
                )
            }
            if (isLoading) {
                Box(Modifier.size(glyphSlot), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = contentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(if (isInline) 18.dp else 20.dp),
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                        onPlayPause()
                    },
                    modifier = Modifier.size(glyphSlot),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                        tint = contentColor,
                        modifier = Modifier.size(glyphSize),
                    )
                }
            }
            // Dropped inline: the collapsed row is sharing its width with the
            // tab pill and the Search circle, and the title is what has to
            // survive that, not a second transport button.
            if (!isInline) {
                // Glyph centres 44dp apart, as Apple's are.
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        haptics.play(Haptic.SkipNext)
                        onNext()
                    },
                    enabled = !controlsLocked,
                    modifier = Modifier.size(glyphSlot),
                ) {
                    MiniPlayerNextGlyph(
                        tint = contentColor.copy(alpha = if (controlsLocked) 0.3f else 1f),
                    )
                }
            }
        }
    }
}

/** The collapsed row's margin at either side of the screen, and its items' spacing. */
private val INLINE_GUTTER = 28.dp
private val INLINE_SPACING = 14.dp

/**
 * Half the difference between the expanded tab row, 62dp, and the collapsed
 * one, 48 — a 28dp glyph in 10dp of padding — so the one folds onto the
 * other's centre line.
 */
private val INLINE_LIFT = 7.dp

/**
 * The accessory's type, measured off Apple's, collapsed and expanded alike:
 * 13pt over 12pt, set looser than the scale's 16 and 14 as SF Pro Text spaces
 * these sizes — the title less so than measured, tuned by eye on device: in
 * Semibold +0.4 read loose and the cut's own spacing tight.
 */
private val ACCESSORY_TITLE_SIZE = 13.sp

/**
 * W700, which in this app's family draws the Semibold cut (see SFProDisplay):
 * Apple's accessory sets its title in Semibold, and the scale's W600 — the
 * Medium cut — read thin at 13sp.
 */
private val ACCESSORY_TITLE_WEIGHT = FontWeight.W700
private val ACCESSORY_TITLE_TRACKING = 0.15.sp
private val ACCESSORY_ARTIST_SIZE = 12.sp
private val ACCESSORY_ARTIST_TRACKING = 0.35.sp

/** The accessory's artwork, about Apple's 30pt at both densities, and its corner. */
private val ACCESSORY_ART_SIZE = 30.4.dp
private val ACCESSORY_ART_CORNER = 5.7.dp

/** The expanded play glyph: its triangle about Apple's 15pt tall. */
private val EXPANDED_GLYPH_SIZE = 29.dp
