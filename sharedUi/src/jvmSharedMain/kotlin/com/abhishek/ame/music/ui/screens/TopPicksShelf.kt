package com.abhishek.ame.music.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.ScrollableState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.abhishek.ame.music.data.canvas.CanvasArtwork
import com.abhishek.ame.music.ui.AppUi
import com.abhishek.ame.music.ui.graphics.rememberSkyShader
import com.abhishek.ame.music.ui.graphics.rememberPrismShader
import com.abhishek.ame.music.ui.player.CanvasArtworkPlayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import com.abhishek.ame.music.ui.components.libraryGrid
import com.abhishek.ame.music.ui.components.MOTION_FRAME_GAP_MS
import com.abhishek.ame.music.ui.components.LIBRARY_GRID_SPACING
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import com.abhishek.ame.music.ui.components.SHELF_CARD_WIDTH
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.PathOperation
import kotlin.math.exp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import kotlin.math.hypot
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.abhishek.ame.music.data.model.CARD_ART_PX
import com.abhishek.ame.music.data.model.HEADER_ART_PX
import com.abhishek.ame.music.data.model.TopPick
import com.abhishek.ame.music.data.model.TopPickKind
import com.abhishek.ame.music.data.model.TopPickMood
import com.abhishek.ame.music.data.model.artworkAt
import com.abhishek.ame.music.sharedui.resources.*
import com.abhishek.ame.music.ui.components.PAGE_GUTTER
import com.abhishek.ame.music.ui.components.rememberBlurredBackdrop
import com.abhishek.ame.music.ui.components.ShelfRow
import com.abhishek.ame.music.ui.components.SectionHeaderSkeleton
import com.abhishek.ame.music.ui.components.ShimmerBox
import com.abhishek.ame.music.ui.components.contextClick
import com.abhishek.ame.music.ui.components.longPressMenuClickable
import com.abhishek.ame.music.ui.components.thumbnailBorder
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/*
 * Home's "Top Picks for You": Apple Music's lead row of tall cards, each one
 * a station, a mood, an artist or a new release picked for this listener.
 *
 * The two "Made for You" stations are drawn here rather than shipped as video:
 * the same folded-paper chevrons and the fan of shards, as vector paths on a
 * Canvas whose phase is read only in the draw pass, so the animation repaints
 * the card without recomposing it, and stops when the row scrolls away and the
 * card leaves composition.
 */

/** Width over height: Apple's cards are a 3:4 portrait. */
private const val TOP_PICK_RATIO = 0.75f

/** Share of the row a card takes, so the next one shows past it. */
private const val TOP_PICK_FRACTION = 0.60f

/** Held back on a tablet, the same way [heroCardWidth] is. */
private val TOP_PICK_MAX_WIDTH = 280.dp

private val TopPickShape = RoundedCornerShape(12.dp)

private fun topPickWidth(available: Dp): Dp = minOf(available * TOP_PICK_FRACTION, TOP_PICK_MAX_WIDTH)

@Composable
internal fun TopPicksShelf(
    picks: List<TopPick>,
    /** The account's name, for "Your Station". Null also shows "Your Station". */
    listenerName: String?,
    /** The card, and the title it was showing — the station's name for the queue. */
    onClick: (TopPick, String) -> Unit,
    onLongPress: ((TopPick) -> Unit)?,
) {
    val rowState = rememberLazyListState()
    // Which cards turned out to have a clip, by index, and which one plays.
    val hasClip = remember(picks) { mutableStateMapOf<Int, Boolean>() }
    var owner by remember(picks) { mutableIntStateOf(-1) }
    // Handed on only once a swipe has come to rest: passing it card to card
    // mid-swipe would start a decoder for every card the row slid past.
    LaunchedEffect(rowState, hasClip) {
        snapshotFlow { rowState.isScrollInProgress to clipOwner(rowState.layoutInfo, hasClip) }
            .collect { (scrolling, next) -> if (!scrolling) owner = next }
    }
    val timeState = rememberContinuousTime()
    CompositionLocalProvider(LocalContinuousTime provides timeState) {
        Column(Modifier.padding(bottom = 26.dp)) {
            SectionHeader(stringResource(Res.string.top_picks_for_you))
            BoxWithConstraints {
                val cardWidth = topPickWidth(maxWidth)
                PausedWhileScrolling(rowState) {
                    ShelfRow(
                        contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        state = rowState,
                    ) {
                        itemsIndexed(picks, key = { index, pick -> "$index:${pick.kind}:${pick.title}" }) { index, pick ->
                            CompositionLocalProvider(
                                LocalClipSlot provides ClipSlot(
                                    mayPlay = index == owner,
                                    onClipFound = { found -> hasClip[index] = found },
                                ),
                            ) {
                                TopPickCard(
                                    pick = pick,
                                    listenerName = listenerName,
                                    onClick = onClick,
                                    onLongPress = onLongPress?.takeIf { pick.kind == TopPickKind.LATEST_RELEASE },
                                    modifier = Modifier.width(cardWidth),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Stands in for the row while it is being built, at the same size, so the feed doesn't jump. */
fun LazyListScope.topPicksSkeleton() {
    item(key = "skeleton:top-picks") {
        Column(Modifier.padding(bottom = 26.dp)) {
            SectionHeaderSkeleton()
            BoxWithConstraints {
                val cardWidth = topPickWidth(maxWidth)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    userScrollEnabled = false,
                ) {
                    items(2) {
                        ShimmerBox(
                            modifier = Modifier.width(cardWidth).aspectRatio(TOP_PICK_RATIO),
                            shape = TopPickShape,
                        )
                    }
                }
            }
        }
    }
}

/** What a card says: the small coloured line, the title, and an optional line under it. */
private class CardText(val eyebrow: String, val title: String, val subtitle: String)

@Composable
private fun cardText(pick: TopPick, listenerName: String?): CardText {
    val artist = pick.artistName.orEmpty()
    return when (pick.kind) {
        TopPickKind.LATEST_RELEASE -> CardText(
            eyebrow = stringResource(Res.string.top_pick_new_from, artist),
            title = pick.title,
            subtitle = localizeCardSubtitle(pick.subtitle),
        )
        TopPickKind.PERSONAL_STATION -> CardText(
            eyebrow = stringResource(Res.string.top_pick_made_for_you),
            title = listenerName?.substringBefore(' ')?.takeIf { it.isNotBlank() }
                ?.let { stringResource(Res.string.top_pick_named_station, it) }
                ?: stringResource(Res.string.top_pick_your_station),
            subtitle = "",
        )
        TopPickKind.MOOD -> {
            val mood = pick.mood ?: TopPickMood.FEEL_GOOD
            CardText(
                eyebrow = stringResource(Res.string.top_pick_mood_for_you),
                title = stringResource(mood.titleRes()),
                subtitle = stringResource(mood.lineRes()),
            )
        }
        TopPickKind.ARTIST_AND_SIMILAR -> CardText(
            eyebrow = stringResource(Res.string.top_pick_featuring, artist),
            title = stringResource(Res.string.top_pick_and_similar, artist),
            subtitle = "",
        )
        TopPickKind.FEATURED_ARTIST -> CardText(
            eyebrow = stringResource(Res.string.top_pick_featuring, artist),
            title = pick.title,
            subtitle = pick.subtitle,
        )
        TopPickKind.NEW_RELEASE_MIX -> CardText(
            eyebrow = stringResource(Res.string.top_pick_made_for_you),
            title = pick.title,
            subtitle = pick.subtitle,
        )
        TopPickKind.DISCOVERY_STATION -> CardText(
            eyebrow = stringResource(Res.string.top_pick_made_for_you),
            title = stringResource(Res.string.top_pick_discovery_station),
            subtitle = "",
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopPickCard(
    pick: TopPick,
    listenerName: String?,
    onClick: (TopPick, String) -> Unit,
    onLongPress: ((TopPick) -> Unit)?,
    modifier: Modifier,
) {
    val text = cardText(pick, listenerName)
    val longPress = onLongPress?.let { { it(pick) } }
    Box(
        modifier = modifier
            .aspectRatio(TOP_PICK_RATIO)
            .clip(TopPickShape)
            .thumbnailBorder(TopPickShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .longPressMenuClickable(onClick = { onClick(pick, text.title) }, onLongClick = longPress)
            .contextClick(longPress),
    ) {
        val eyebrowColor: Color
        val branded: Boolean
        when (pick.kind) {
            TopPickKind.PERSONAL_STATION -> {
                PersonalStationArt(Modifier.fillMaxSize())
                eyebrowColor = Color(0xFFB9C8F2)
                branded = true
            }
            TopPickKind.DISCOVERY_STATION -> {
                DiscoveryStationArt(Modifier.fillMaxSize())
                eyebrowColor = Color(0xFFFCE7F3)
                branded = true
            }
            TopPickKind.MOOD -> {
                val style = (pick.mood ?: TopPickMood.FEEL_GOOD).style()
                MoodArt(pick.mood ?: TopPickMood.FEEL_GOOD, style, Modifier.fillMaxSize())
                eyebrowColor = style.eyebrow
                branded = true
            }
            TopPickKind.ARTIST_AND_SIMILAR -> {
                val palette = collagePalette(pick.artistName.orEmpty())
                ArtistCollageArt(pick.images, palette, Modifier.fillMaxSize())
                eyebrowColor = palette.eyebrow
                branded = true
            }
            TopPickKind.LATEST_RELEASE, TopPickKind.NEW_RELEASE_MIX -> {
                ReleaseArt(
                    pick.images.firstOrNull(),
                    Modifier.fillMaxSize(),
                    // A New Release Mix is a playlist; only a real release has a sleeve that moves.
                    motion = pick.takeIf { it.kind == TopPickKind.LATEST_RELEASE }?.let { release ->
                        CardMotionSource(release.browseId ?: release.title) {
                            AppUi.host.releaseMotion(release.title, release.artistName.orEmpty())
                        }
                    },
                )
                eyebrowColor = PhotoEyebrow
                branded = false
            }
            TopPickKind.FEATURED_ARTIST -> {
                AsyncImage(
                    model = pick.images.firstOrNull().croppedAt(HEADER_ART_PX * 3 / 4, HEADER_ART_PX).whenArtworkReady(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                CardMotion(pick.artistMotion(), Modifier.fillMaxSize())
                // The caption sits on the picture's own darkened foot, as on
                // Apple's photo cards, rather than on a band of flat black.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                0.72f to Color.Black.copy(alpha = 0.45f),
                                1f to Color.Black.copy(alpha = 0.82f),
                            ),
                        ),
                )
                eyebrowColor = PhotoEyebrow
                branded = false
            }
        }
        if (branded) {
            Icon(
                painter = painterResource(Res.drawable.ic_logo),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .height(15.dp)
                    .aspectRatio(860f / 320f),
            )
        }
        CardCaption(text, eyebrowColor, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun CardCaption(text: CardText, eyebrowColor: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Text(
            text = text.eyebrow,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 16.sp),
            color = eyebrowColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = text.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (text.subtitle.isNotBlank()) {
            Text(
                text = text.subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 16.sp),
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The line above the title on a photo card: white, let through, as on Apple Music's phone app. */
private val PhotoEyebrow = Color.White.copy(alpha = 0.62f)

/**
 * A release's card, as Apple Music draws one: the cover whole, as a square at
 * the top, and under it the cover's lower edge running on out of focus,
 * behind the caption.
 *
 * The blur is not a blur pass on screen: [rememberBlurredBackdrop] blurs a
 * small copy of the cover once, as it loads, and the image cache keeps the
 * result. A live blur — RenderEffect, Haze — would be re-run on every frame
 * the page draws, and this row's animated cards draw every frame.
 *
 * With a [motion] source, the release's motion cover plays over the square
 * once found, dissolving into the blur the same way the still does.
 */
@Composable
private fun ReleaseArt(url: String?, modifier: Modifier, motion: CardMotionSource? = null) {
    // How much of the still the clip has covered. Two faded layers stacked
    // would leave the still showing through the clip's own fade, so the still
    // steps out as the clip comes in.
    var covered by remember { mutableFloatStateOf(0f) }
    Box(modifier.background(Color.Black)) {
        AsyncImage(
            model = rememberBlurredBackdrop(url.croppedAt(BACKDROP_SOURCE_PX).whenArtworkReady()),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.42f),
        )
        // Toned down toward the caption, as Apple does, so the white text reads
        // on any cover.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.6f to Color.Black.copy(alpha = 0.08f),
                        1f to Color.Black.copy(alpha = 0.38f),
                    ),
                ),
        )
        AsyncImage(
            model = url.croppedAt(HEADER_ART_PX).whenArtworkReady(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                // Faded out over its last stretch, so the cover runs into the
                // blur under it instead of ending on a hard line. Offscreen so
                // the mask cuts the cover alone; the cover never animates, so
                // the layer is drawn once and reused while it scrolls.
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = 1f - covered
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(0.78f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
        CardMotion(
            motion,
            Modifier.fillMaxWidth().aspectRatio(1f),
            bottomFade = 0.22f,
            onCover = { covered = it },
        )
    }
}

/**
 * Where a card's Apple Music clip comes from: [lookup], asked once per [key]
 * while the card is on screen.
 */
private class CardMotionSource(val key: Any, val lookup: suspend () -> CanvasArtwork?)

/** The artist's own looping clip, for the cards that show their face. */
private fun TopPick.artistMotion(): CardMotionSource? {
    val channel = artistBrowseId ?: return null
    val name = artistName ?: return null
    return CardMotionSource(channel) { AppUi.host.artistMotion(channel, name) }
}

/**
 * A card's clip over the still it sits on. The still stays underneath until
 * the clip's first frame has faded in, and for good where there is no clip —
 * which is most cards, and every card with animated artwork off.
 *
 * Held back with the pictures ([LocalArtworkImages]) so a cold start's feed
 * doesn't wait on it, paused through a scroll ([LocalArtworkScrolling]), and
 * paused and then let go of ([LocalArtworkMotion]) while Home isn't being looked
 * at. Only the card holding the row's [ClipSlot] plays; the others keep their
 * stills.
 */
@Composable
private fun CardMotion(
    source: CardMotionSource?,
    modifier: Modifier,
    bottomFade: Float = 0f,
    onCover: (Float) -> Unit = {},
) {
    if (source == null || !LocalArtworkImages.current) return
    // Looked up whether or not the clip may play yet, so the answer is to hand
    // when it may, and the row knows which cards have one to give the slot to.
    val clip by produceState<CanvasArtwork?>(null, source.key) { value = source.lookup() }
    val slot = LocalClipSlot.current
    val reportFound by rememberUpdatedState(slot?.onClipFound)
    val found = clip != null
    DisposableEffect(found) {
        reportFound?.invoke(found)
        onDispose { reportFound?.invoke(false) }
    }
    val canvas = clip ?: return
    val motion = LocalArtworkMotion.current
    val mayPlay = slot?.mayPlay != false
    val scrolling = LocalArtworkScrolling.current
    // Whether the clip's player exists at all. Building one blocks the main
    // thread and tearing one down takes its surface with it, so neither may
    // land on a frame anything is moving on: not mid-fling as the row scrolls
    // back into view, not on the first frame of Now Playing rising, and not on
    // the frame it lands back on the mini player. The still under it comes back
    // as it goes.
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(motion, mayPlay, scrolling) {
        when {
            // The row has handed the slot on, and it does that only at rest.
            !mayPlay -> mounted = false
            motion -> if (!mounted) {
                delay(CLIP_START_DELAY_MS)
                snapshotFlow { scrolling() }.first { !it }
                mounted = true
            }
            else -> {
                delay(CLIP_RELEASE_DELAY_MS)
                mounted = false
            }
        }
    }
    if (!mounted) return
    // Only paused for a scroll or for Now Playing passing over: neither is worth
    // fetching, decoding and fading the clip back in for. Read here, so it is
    // this card alone that recomposes as a scroll starts and ends.
    CanvasArtworkPlayer(
        canvas = canvas,
        isPlaying = true,
        modifier = modifier,
        bottomFade = bottomFade,
        onCoverChanged = onCover,
        pausedForTransition = !motion || scrolling(),
    )
}

/**
 * How long a card waits, once nothing stands in its clip's way, to build its
 * player: long enough for a player closing or a page settling to be finished.
 */
private const val CLIP_START_DELAY_MS = 400L

/**
 * How long a clip stays paused and mounted once the page stops being looked at
 * before its player and decoder are let go: past the whole of Now Playing's
 * slide, so the release lands under an opaque player, and so a quick look at
 * the player and back keeps the clip.
 */
private const val CLIP_RELEASE_DELAY_MS = 1_000L

/** The cover size the backdrop is cut from: it ends up 48px wide, so a small copy is plenty. */
private const val BACKDROP_SOURCE_PX = 160

// ---- Made for You stations ---------------------------------------------------

/**
 * Whether Top Picks' and Find Your Mood's artwork may move. Off while the
 * page isn't really being looked at — the app in the background, or Now
 * Playing pulled up over Home — when an endless animation would go on
 * redrawing a page nobody can see. The artwork then holds still where it is
 * and picks up again when the page is back.
 */
val LocalArtworkMotion = compositionLocalOf { true }

/**
 * Whether the page, or the row the artwork sits in, is being scrolled: every
 * loop is a redraw each frame, and a scroll is when the page can least spare
 * one. Handed down as something to ask rather than as a value, because a
 * value would recompose everything under it as each scroll starts and again
 * as it ends — on the very frames the scroll needs. Asked from the loops'
 * own coroutines instead ([loopPhase]), it recomposes nothing.
 */
val LocalArtworkScrolling = staticCompositionLocalOf<() -> Boolean> { { false } }

/** [content], with [state]'s scrolling added to what [LocalArtworkScrolling] answers. */
@Composable
internal fun PausedWhileScrolling(state: ScrollableState, content: @Composable () -> Unit) {
    val outer = LocalArtworkScrolling.current
    val scrolling = remember(outer, state) { { outer() || state.isScrollInProgress } }
    CompositionLocalProvider(LocalArtworkScrolling provides scrolling, content = content)
}

/**
 * One card's share of its row's clip: whether it [mayPlay], and where it says
 * whether it has one at all. A row of release and artist cards each decoding
 * their own video at once was a decoder per card; the row lets one play.
 */
private class ClipSlot(val mayPlay: Boolean, val onClipFound: (Boolean) -> Unit)

/** The card's [ClipSlot], or null outside a row that shares one out. */
private val LocalClipSlot = compositionLocalOf<ClipSlot?> { null }

/**
 * Which of the cards with a clip the row's slot goes to: the one most fully
 * in view, the earlier one on a tie. -1 when none in view has a clip.
 */
private fun clipOwner(layout: LazyListLayoutInfo, hasClip: Map<Int, Boolean>): Int {
    val start = layout.viewportStartOffset
    val end = layout.viewportEndOffset
    return layout.visibleItemsInfo
        .filter { hasClip[it.index] == true && it.size > 0 }
        .maxByOrNull { (minOf(it.offset + it.size, end) - maxOf(it.offset, start)).toFloat() / it.size }
        ?.index ?: -1
}

/**
 * Whether Top Picks may load its pictures yet. Off while Home is still
 * fetching its own feed: a cold start measured a third of a second slower to
 * Recents with the row's covers and photos decoding alongside it, so the
 * cards paint their colours and captions at once and their pictures follow
 * once the page they sit on has what it needs.
 */
val LocalArtworkImages = compositionLocalOf { true }

/** This image, or nothing yet while [LocalArtworkImages] is holding pictures back. */
@Composable
private fun String?.whenArtworkReady(): String? = if (LocalArtworkImages.current) this else null

/** One full breath of the stations' motion. Slow on purpose: it is a cover, not a spinner. */
private const val STATION_PERIOD_MS = 9_000

/**
 * A 0→1 loop of [periodMs], held wherever it is while [LocalArtworkMotion] is
 * off or [LocalArtworkScrolling] says a scroll is on, and carried on from
 * there after: a loop that went back to its first frame at every scroll would
 * jump. Read it only inside a draw lambda: then a frame of the loop costs a
 * redraw of that one canvas and never a recomposition.
 */
@Composable
private fun loopPhase(periodMs: Int): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    val running = LocalArtworkMotion.current
    val scrolling = LocalArtworkScrolling.current
    LaunchedEffect(running, periodMs, scrolling) {
        if (!running) return@LaunchedEffect
        var last = Long.MIN_VALUE
        while (true) {
            if (scrolling()) {
                // No frames asked for until it's over, so nothing is redrawn;
                // the time spent waiting isn't counted toward the loop.
                snapshotFlow { scrolling() }.first { !it }
                last = Long.MIN_VALUE
            }
            withFrameNanos { now ->
                if (last != Long.MIN_VALUE) {
                    phase.floatValue = (phase.floatValue + (now - last) / (periodMs * 1_000_000f)) % 1f
                }
                last = now
            }
            // A nine-second breath doesn't need every vsync, and each frame of
            // it redraws the page under the glass — see MOTION_FRAME_GAP_MS.
            delay(MOTION_FRAME_GAP_MS)
        }
    }
    return phase
}

/** Shared continuous monotonic time counter for shelf animations, tied directly to VSYNC. */
private val LocalContinuousTime = compositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

@Composable
private fun rememberContinuousTime(): State<Float> {
    val timeState = remember { mutableFloatStateOf(0f) }
    val running = LocalArtworkMotion.current
    val scrolling = LocalArtworkScrolling.current
    LaunchedEffect(running, scrolling) {
        if (!running) return@LaunchedEffect
        var last = Long.MIN_VALUE
        while (true) {
            if (scrolling()) {
                snapshotFlow { scrolling() }.first { !it }
                last = Long.MIN_VALUE
            }
            withFrameNanos { now ->
                if (last != Long.MIN_VALUE) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    timeState.floatValue += dt
                }
                last = now
            }
        }
    }
    return timeState
}

private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Keeps a card's drawn artwork as a hardware display list layer without
 * allocating heavy offscreen frame buffers.
 */
private val RetainedArt = Modifier.graphicsLayer()

private fun wave(phase: Float, offset: Float): Float = sin(2f * PI.toFloat() * (phase + offset))

/**
 * Whatever a canvas can work out once per size rather than once per frame —
 * brushes, fixed paths — rebuilt only when the card's size changes.
 */
private class SizeCache<T : Any>(private val build: (Size) -> T) {
    private var size = Size.Unspecified
    private var value: T? = null

    fun get(current: Size): T {
        val cached = value
        if (cached != null && current == size) return cached
        size = current
        return build(current).also { value = it }
    }
}

/**
 * Abhishek's Station (Personal Station) — FeralUI Animated "Night sky" Stratified Atmosphere.
 *
 * Soft, billowing, ethereal celestial cloudscape matching FeralUI "Night sky":
 * - Upper third: Luminous, pearlescent Ice Hanada (#B9C8F2) and moonlit pearl (#F4F8FF) cloud bank
 *   gently undulating across the sky.
 * - Middle third: Deep smoky atmospheric descent through Glazed Azure (#6E7FBF),
 *   Clear Hanada (#4A5AA0), and Storm Blue (#33406E).
 * - Lower half: Deep oceanic indigo Hanada fading into Midnight Abyss (#000032) for crisp text contrast.
 *
 * Exact palette stops and parameters from feralui.dev Night sky:
 * - ICE HANADA (#B9C8F2) at position 0.125
 * - GLAZED AZURE (#6E7FBF) at position 0.375
 * - CLEAR HANADA (#4A5AA0) at position 0.625
 * - STORM BLUE (#33406E) at position 0.875
 * - Base: MIDNIGHT ABYSS (#000032)
 * - speed: 14, startT: 26.09f
 */
private val NightSkyStops = listOf(
    Color(0xFFB9C8F2), // ICE HANADA (0.125)
    Color(0xFF6E7FBF), // GLAZED AZURE (0.375)
    Color(0xFF4A5AA0), // CLEAR HANADA (0.625)
    Color(0xFF33406E), // STORM BLUE (0.875)
)
private val NightSkyMidnight = Color(0xFF000032) // Midnight Abyss base
private val NightSkyPearl = Color(0xFFF4F8FF)    // Pearlescent moonlit cloud highlight

@Composable
private fun PersonalStationArt(modifier: Modifier = Modifier) {
    val skyShader = rememberSkyShader(
        tonesHigh = NightSkyStops[0], // ICE HANADA (#B9C8F2)
        tonesMain = NightSkyStops[1], // GLAZED AZURE (#6E7FBF)
        tonesMid  = NightSkyStops[2], // CLEAR HANADA (#4A5AA0)
        tonesLow  = NightSkyStops[3], // STORM BLUE (#33406E)
        scale = 42f,
        distortion = 46f,
        swirl = 28f,
    )
    val timeState = LocalContinuousTime.current
    Canvas(modifier.then(RetainedArt)) {
        if (skyShader != null) {
            val t = timeState.value * (14f / 100f) * 1.2f + 26.09f
            skyShader.updateTimeAndSize(t, size.width, size.height)
            drawRect(skyShader.brush)
        } else {
            // Base Stratified Celestial Atmosphere Gradient (fallback)
            drawRect(
                brush = Brush.verticalGradient(
                    0.00f to Color(0xFF91A0DA),
                    0.08f to Color(0xFFAFBEE8),
                    0.15f to Color(0xFFB9C8F2),
                    0.22f to Color(0xFFB4C3F0),
                    0.30f to Color(0xFF8C9BD7),
                    0.36f to Color(0xFF6470B4),
                    0.42f to Color(0xFF414887),
                    0.48f to Color(0xFF2D325E),
                    0.55f to Color(0xFF191C41),
                    0.65f to Color(0xFF111339),
                    0.75f to Color(0xFF0A0C32),
                    0.85f to NightSkyMidnight,
                    1.00f to NightSkyMidnight,
                )
            )
        }

        // Contrast foot scrim for crystal clear typography legibility
        drawRect(
            brush = Brush.verticalGradient(
                0.60f to Color.Transparent,
                1.00f to NightSkyMidnight.copy(alpha = 0.82f),
            )
        )
    }
}

/**
 * Discovery Station — FeralUI Animated "Cherry pop" PRISM Valley Gradient.
 *
 * Cascading V-shaped canyon of vertical slats expanding smoothly outward,
 * flowing from deep Ink Purple at the top through Dusk Crimson and Persimmon Red
 * into glowing Soft Crimson and Peach Silk at the bottom.
 *
 * Exact palette from feralui.dev Cherry pop:
 * - INK PURPLE (#521A37) at position 0.1
 * - DUSK CRIMSON (#9C204C) at position 0.3
 * - PERSIMMON RED (#E24762) at position 0.5
 * - SOFT CRIMSON (#F8968E) at position 0.7
 * - PEACH SILK (#F7D0B5) at position 0.9
 */
private val CherryPopStops = listOf(
    Color(0xFF521A37), // INK PURPLE (0.1)
    Color(0xFF9C204C), // DUSK CRIMSON (0.3)
    Color(0xFFE24762), // PERSIMMON RED (0.5)
    Color(0xFFF8968E), // SOFT CRIMSON (0.7)
    Color(0xFFF7D0B5), // PEACH SILK (0.9)
)
private val CherryPopPositions = floatArrayOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)

private fun sampleCherryPop(t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    if (clamped <= CherryPopPositions[0]) return CherryPopStops[0]
    if (clamped >= CherryPopPositions[4]) return CherryPopStops[4]
    for (i in 0 until 4) {
        val p0 = CherryPopPositions[i]
        val p1 = CherryPopPositions[i + 1]
        if (clamped in p0..p1) {
            val fraction = (clamped - p0) / (p1 - p0)
            return lerp(CherryPopStops[i], CherryPopStops[i + 1], fraction)
        }
    }
    return CherryPopStops[4]
}

@Composable
private fun DiscoveryStationArt(modifier: Modifier = Modifier) {
    val prismShader = rememberPrismShader()
    val timeState = LocalContinuousTime.current

    Canvas(modifier.then(RetainedArt)) {
        val w = size.width
        val h = size.height
        val oTime = timeState.value * (40f / 100f) * 1.2f + 20.75f

        if (prismShader != null) {
            prismShader.updateTimeAndSize(oTime, w, h)
            drawRect(prismShader.brush)
        } else {
            // 1. Deep Ink Purple base field
            drawRect(CherryPopStops[0])

            // 2. FeralUI Prism Valley slats (fallback)
            val a = 11f
            val c = oTime * 0.48f
            val pFloor = floor(-c).toInt()
            val gCeil = ceil(a - c).toInt()
            val en = 24

            for (ySign in intArrayOf(-1, 1)) {
                for (b in pFloor until gCeil) {
                    val bFloat = b.toFloat()
                    val F = ((bFloat + c) / a).coerceAtLeast(0f)
                    val C = ((bFloat + 1f + c) / a).coerceAtMost(1f)
                    if (C <= F) continue

                    val E = if (ySign < 0) (1f - C) / 2f else (1f + F) / 2f
                    val L = if (ySign < 0) (1f - F) / 2f else (1f + C) / 2f
                    val D = (E + L) / 2f
                    val P = (D - 0.5f) * 2f
                    val m = 0.5f - 0.5f * cos(abs(P).coerceAtMost(1f) * PI.toFloat())
                    val S = 1f - m // valley shape
                    val v = 0.015f * sin(oTime * 0.32f)
                    val R = 0.28f + S * 0.36f + v
                    val I = 0.025f * sin(bFloat * 1.8f) + 0.012f * sin(bFloat * 0.73f + oTime * 0.24f)
                    val T = 0.74f

                    val slatColors = List(en) { le ->
                        val O = le / (en - 1f)
                        val underscore = smoothstep((O - R + 0.22f) / T)
                        val W = 0.02f
                        val H = (W + (0.98f - W) * underscore + I).coerceIn(0f, 1f)
                        sampleCherryPop(H)
                    }

                    val slatBrush = Brush.verticalGradient(
                        colors = slatColors,
                        startY = 0f,
                        endY = h,
                    )

                    val slatLeft = E * w
                    val slatW = (L - E) * w + 0.75f // Overlap by 0.75px to ensure no seam gaps
                    drawRect(
                        brush = slatBrush,
                        topLeft = Offset(slatLeft, 0f),
                        size = Size(slatW, h),
                    )
                }
            }
        }

        // Subtle foot scrim to ensure caption text is crisp and readable over peach silk
        drawRect(
            brush = Brush.verticalGradient(
                0.60f to Color.Transparent,
                1.00f to Color(0xFF1E0713).copy(alpha = 0.52f),
            )
        )
    }
}

// ---- Mood for You -------------------------------------------------------------

/**
 * FeralUI animated Sky gradients for Mood cards.
 * Uses procedural atmospheric fluid noise with custom themed palettes:
 * - Blue sky: Serene mist and river indigo (Relax, Chill, Feeling Blue, Commuting, Alone Time)
 * - Ink sky: Minimal lunar pearl and cloud grey (Focus, Work, Sleep, After Hours)
 * - Night sky: Midnight celestial hanada (Evening, Wellbeing)
 * - Emerald sky: Lush organic verdant green (Energy, Fitness, Sports, Motivation, Outdoors)
 * - Golden Dawn sky: Radiant sunny amber (Feel Good, Morning, Summertime, Weekend, Cooking)
 * - Rose Sunset sky: Ethereal magenta blush (Love, Party, Social, Gaming, Home)
 * - Crimson Dusk sky: Dramatic scarlet dusk (Heartbreak, Holiday)
 */
private class MoodStyle(
    val tonesHigh: Color,
    val tonesMain: Color,
    val tonesMid: Color,
    val tonesLow: Color,
    val speed: Float = 20f,
    val eyebrow: Color = tonesHigh,
)

private fun TopPickMood.style(): MoodStyle = when (this) {
    // 1. Blue sky (FeralUI: #E6F2FF, #B3D9FF, #80B3FF, #6699E6, speed 22)
    TopPickMood.RELAX,
    TopPickMood.CHILL,
    TopPickMood.FEELING_BLUE,
    TopPickMood.COMMUTING,
    TopPickMood.ALONE_TIME -> MoodStyle(
        tonesHigh = Color(0xFFE6F2FF),
        tonesMain = Color(0xFFB3D9FF),
        tonesMid  = Color(0xFF80B3FF),
        tonesLow  = Color(0xFF6699E6),
        speed = 22f,
        eyebrow = Color(0xFFB3D9FF),
    )

    // 2. Ink sky (FeralUI: #F2F2F0, #C4C6C9, #8E9094, #55585E, speed 24)
    TopPickMood.FOCUS,
    TopPickMood.WORK,
    TopPickMood.SLEEP,
    TopPickMood.AFTER_HOURS -> MoodStyle(
        tonesHigh = Color(0xFFF2F2F0),
        tonesMain = Color(0xFFC4C6C9),
        tonesMid  = Color(0xFF8E9094),
        tonesLow  = Color(0xFF55585E),
        speed = 24f,
        eyebrow = Color(0xFFE2E4E8),
    )

    // 3. Night sky (FeralUI: #B9C8F2, #6E7FBF, #4A5AA0, #33406E, speed 14)
    TopPickMood.EVENING,
    TopPickMood.WELLBEING -> MoodStyle(
        tonesHigh = Color(0xFFB9C8F2),
        tonesMain = Color(0xFF6E7FBF),
        tonesMid  = Color(0xFF4A5AA0),
        tonesLow  = Color(0xFF33406E),
        speed = 14f,
        eyebrow = Color(0xFFB9C8F2),
    )

    // 4. Emerald sky (Vibrant Verdant: #EDFFEB, #9AEFB0, #3CC869, #15632E, speed 20)
    TopPickMood.ENERGY,
    TopPickMood.FITNESS,
    TopPickMood.SPORTS,
    TopPickMood.MOTIVATION,
    TopPickMood.OUTDOORS -> MoodStyle(
        tonesHigh = Color(0xFFEDFFEB),
        tonesMain = Color(0xFF9AEFB0),
        tonesMid  = Color(0xFF3CC869),
        tonesLow  = Color(0xFF15632E),
        speed = 20f,
        eyebrow = Color(0xFFB8FFCE),
    )

    // 5. Golden Dawn sky (Feel-good Sunrise: #FFFDEB, #FFDE75, #FFA020, #B84805, speed 18)
    TopPickMood.FEEL_GOOD,
    TopPickMood.MORNING,
    TopPickMood.SUMMERTIME,
    TopPickMood.WEEKEND,
    TopPickMood.EATING_COOKING -> MoodStyle(
        tonesHigh = Color(0xFFFFFDEB),
        tonesMain = Color(0xFFFFDE75),
        tonesMid  = Color(0xFFFFA020),
        tonesLow  = Color(0xFFB84805),
        speed = 18f,
        eyebrow = Color(0xFFFFE8A0),
    )

    // 6. Rose Sunset sky (Romantic / Euphoric: #FFE8F5, #FFA6D8, #E63A98, #7A1050, speed 20)
    TopPickMood.LOVE,
    TopPickMood.PARTY,
    TopPickMood.SOCIAL,
    TopPickMood.GAMING,
    TopPickMood.HOME -> MoodStyle(
        tonesHigh = Color(0xFFFFE8F5),
        tonesMain = Color(0xFFFFA6D8),
        tonesMid  = Color(0xFFE63A98),
        tonesLow  = Color(0xFF7A1050),
        speed = 20f,
        eyebrow = Color(0xFFFFB8DC),
    )

    // 7. Crimson Dusk sky (Dramatic / Deep Scarlet: #FFEBE8, #FFA592, #E83A30, #6E1010, speed 16)
    TopPickMood.HEARTBREAK,
    TopPickMood.HOLIDAY -> MoodStyle(
        tonesHigh = Color(0xFFFFEBE8),
        tonesMain = Color(0xFFFFA592),
        tonesMid  = Color(0xFFE83A30),
        tonesLow  = Color(0xFF6E1010),
        speed = 16f,
        eyebrow = Color(0xFFFFC0B8),
    )
}

internal fun TopPickMood.titleRes() = when (this) {
    TopPickMood.ENERGY -> Res.string.mood_energy
    TopPickMood.RELAX -> Res.string.mood_relax
    TopPickMood.FEELING_BLUE -> Res.string.mood_feeling_blue
    TopPickMood.FEEL_GOOD -> Res.string.mood_feel_good
    TopPickMood.FOCUS -> Res.string.mood_focus
    TopPickMood.HEARTBREAK -> Res.string.mood_heartbreak
    TopPickMood.LOVE -> Res.string.mood_love
    TopPickMood.AFTER_HOURS -> Res.string.mood_after_hours
    TopPickMood.ALONE_TIME -> Res.string.mood_alone_time
    TopPickMood.CHILL -> Res.string.mood_chill
    TopPickMood.COMMUTING -> Res.string.mood_commuting
    TopPickMood.EATING_COOKING -> Res.string.mood_eating_cooking
    TopPickMood.EVENING -> Res.string.mood_evening
    TopPickMood.FITNESS -> Res.string.mood_fitness
    TopPickMood.GAMING -> Res.string.mood_gaming
    TopPickMood.HOME -> Res.string.mood_home
    TopPickMood.MORNING -> Res.string.mood_morning
    TopPickMood.MOTIVATION -> Res.string.mood_motivation
    TopPickMood.OUTDOORS -> Res.string.mood_outdoors
    TopPickMood.PARTY -> Res.string.mood_party
    TopPickMood.SLEEP -> Res.string.mood_sleep
    TopPickMood.SOCIAL -> Res.string.mood_social
    TopPickMood.SPORTS -> Res.string.mood_sports
    TopPickMood.SUMMERTIME -> Res.string.mood_summertime
    TopPickMood.HOLIDAY -> Res.string.mood_holiday
    TopPickMood.WEEKEND -> Res.string.mood_weekend
    TopPickMood.WELLBEING -> Res.string.mood_wellbeing
    TopPickMood.WORK -> Res.string.mood_work
}

private fun TopPickMood.lineRes() = when (this) {
    TopPickMood.ENERGY -> Res.string.mood_energy_line
    TopPickMood.RELAX -> Res.string.mood_relax_line
    TopPickMood.FEELING_BLUE -> Res.string.mood_feeling_blue_line
    TopPickMood.FEEL_GOOD -> Res.string.mood_feel_good_line
    TopPickMood.FOCUS -> Res.string.mood_focus_line
    TopPickMood.HEARTBREAK -> Res.string.mood_heartbreak_line
    TopPickMood.LOVE -> Res.string.mood_love_line
    TopPickMood.AFTER_HOURS -> Res.string.mood_after_hours_line
    TopPickMood.ALONE_TIME -> Res.string.mood_alone_time_line
    TopPickMood.CHILL -> Res.string.mood_chill_line
    TopPickMood.COMMUTING -> Res.string.mood_commuting_line
    TopPickMood.EATING_COOKING -> Res.string.mood_eating_cooking_line
    TopPickMood.EVENING -> Res.string.mood_evening_line
    TopPickMood.FITNESS -> Res.string.mood_fitness_line
    TopPickMood.GAMING -> Res.string.mood_gaming_line
    TopPickMood.HOME -> Res.string.mood_home_line
    TopPickMood.MORNING -> Res.string.mood_morning_line
    TopPickMood.MOTIVATION -> Res.string.mood_motivation_line
    TopPickMood.OUTDOORS -> Res.string.mood_outdoors_line
    TopPickMood.PARTY -> Res.string.mood_party_line
    TopPickMood.SLEEP -> Res.string.mood_sleep_line
    TopPickMood.SOCIAL -> Res.string.mood_social_line
    TopPickMood.SPORTS -> Res.string.mood_sports_line
    TopPickMood.SUMMERTIME -> Res.string.mood_summertime_line
    TopPickMood.HOLIDAY -> Res.string.mood_holiday_line
    TopPickMood.WEEKEND -> Res.string.mood_weekend_line
    TopPickMood.WELLBEING -> Res.string.mood_wellbeing_line
    TopPickMood.WORK -> Res.string.mood_work_line
}

/**
 * A mood's artwork — animated FeralUI Sky gradient backdrop.
 * Smooth undulating organic clouds, completely free of SVGs/emblems.
 * [square] is a Find Your Mood tile; otherwise it's a tall Top Picks card.
 */
@Composable
private fun MoodArt(
    mood: TopPickMood,
    style: MoodStyle,
    modifier: Modifier = Modifier,
    square: Boolean = false,
    still: Boolean = false,
) {
    val skyShader = if (still) null else rememberSkyShader(
        tonesHigh = style.tonesHigh,
        tonesMain = style.tonesMain,
        tonesMid  = style.tonesMid,
        tonesLow  = style.tonesLow,
        scale = 42f,
        distortion = 46f,
        swirl = 28f,
    )
    val timeState = LocalContinuousTime.current

    Canvas(if (still) modifier else modifier.graphicsLayer()) {
        if (skyShader != null) {
            val baseT = if (still) 0f else timeState.value
            val t = baseT * (style.speed / 100f) * 1.2f + (mood.ordinal * 7.3f) % 30f
            skyShader.updateTimeAndSize(t, size.width, size.height)
            drawRect(skyShader.brush)
        } else {
            drawRect(
                brush = Brush.verticalGradient(
                    0.00f to style.tonesHigh,
                    0.35f to style.tonesMain,
                    0.65f to style.tonesMid,
                    1.00f to style.tonesLow,
                )
            )
        }

        // Contrast foot scrim for crystal clear legibility
        drawRect(
            brush = Brush.verticalGradient(
                0.60f to Color.Transparent,
                1.00f to style.tonesLow.copy(alpha = 0.55f),
            )
        )
    }
}


// ---- Find Your Mood -----------------------------------------------------------

/**
 * Apple Music's Find Your Mood shelf: its seven moods as square tiles, each
 * named under it, and Show all on the heading for the activities as well —
 * the grid [MoodGridPage] lays out. A tile plays that mood's station, the same
 * queue a Mood for You card starts.
 */
@Composable
internal fun FindYourMoodShelf(onClick: (TopPickMood, String) -> Unit, onShowAll: () -> Unit) {
    val rowState = rememberLazyListState()
    val timeState = rememberContinuousTime()
    CompositionLocalProvider(LocalContinuousTime provides timeState) {
        PausedWhileScrolling(rowState) {
            Column(Modifier.padding(bottom = 26.dp)) {
                SectionHeader(stringResource(Res.string.find_your_mood), onShowAll = onShowAll)
                ShelfRow(
                    contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    state = rowState,
                ) {
                    items(TopPickMood.entries.filter { it.isCoreMood }, key = { it.name }) { mood ->
                        MoodTile(mood, onClick, Modifier.width(SHELF_CARD_WIDTH))
                    }
                }
            }
        }
    }
}

/**
 * Every mood and activity, laid out as the Library's Show all grid is — the
 * same columns, card width and spacing — with Apple's seven moods first.
 */
@Composable
fun MoodGridPage(
    gridState: LazyGridState,
    onClick: (TopPickMood, String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val timeState = rememberContinuousTime()
    CompositionLocalProvider(LocalContinuousTime provides timeState) {
        PausedWhileScrolling(gridState) {
            BoxWithConstraints(modifier.fillMaxSize()) {
                val grid = libraryGrid(maxWidth - PAGE_GUTTER * 2)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(grid.columns),
                    state = gridState,
                    contentPadding = contentPadding,
                    horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(horizontal = PAGE_GUTTER),
                ) {
                    gridItems(TopPickMood.entries, key = { it.name }) { mood ->
                        MoodTile(mood, onClick, Modifier.fillMaxWidth(), still = false)
                    }
                }
            }
        }
    }
}

/** A mood's square tile and its name under it, sized like a [ShelfCard]. */
@Composable
private fun MoodTile(
    mood: TopPickMood,
    onClick: (TopPickMood, String) -> Unit,
    modifier: Modifier = Modifier,
    still: Boolean = false,
) {
    val title = stringResource(mood.titleRes())
    Column(modifier.clickable { onClick(mood, title) }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .thumbnailBorder(RoundedCornerShape(12.dp)),
        ) {
            MoodArt(mood, mood.style(), Modifier.fillMaxSize(), square = true, still = still)
            Icon(
                painter = painterResource(Res.drawable.ic_logo),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .height(11.dp)
                    .aspectRatio(860f / 320f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- Featuring … & Similar Artists ----------------------------------------------

private class CollagePalette(val top: Color, val bottom: Color, val eyebrow: Color)

/** Vivid backdrops, one per artist and the same one every day. */
private val CollagePalettes = listOf(
    CollagePalette(Color(0xFF8F3F00), Color(0xFFFF6A0F), Color(0xFFFFC48A)),
    CollagePalette(Color(0xFF2D8A20), Color(0xFF4FD22C), Color(0xFFCBFFA3)),
    CollagePalette(Color(0xFF173E9E), Color(0xFF2F7BFF), Color(0xFFC0D8FF)),
    CollagePalette(Color(0xFF4A1E99), Color(0xFF8A4BFF), Color(0xFFDCC8FF)),
    CollagePalette(Color(0xFF0B6E6E), Color(0xFF14B8A6), Color(0xFFB5FFF3)),
    CollagePalette(Color(0xFF8E1426), Color(0xFFF0364F), Color(0xFFFFC4CC)),
)

private fun collagePalette(name: String): CollagePalette = CollagePalettes[abs(name.hashCode()) % CollagePalettes.size]

/**
 * The artist in a large circle, with up to two related artists in smaller
 * ones beneath it, on a colour that brightens toward the caption.
 *
 * Still, unlike the single-artist photo card: an artist's clip is shot for a
 * full portrait frame, and cut down to a circle among other faces it reads as
 * a small moving tile rather than the artist.
 */
@Composable
private fun ArtistCollageArt(
    images: List<String>,
    palette: CollagePalette,
    modifier: Modifier,
) {
    BoxWithConstraints(
        modifier.background(Brush.verticalGradient(listOf(palette.top, palette.bottom))),
    ) {
        val w = maxWidth
        val h = maxHeight
        // Centre x, centre y (as shares of the card) and diameter (of its width).
        val slots = when (images.size) {
            0 -> emptyList()
            1 -> listOf(Triple(0.5f, 0.34f, 0.66f))
            2 -> listOf(Triple(0.40f, 0.28f, 0.60f), Triple(0.72f, 0.52f, 0.36f))
            else -> listOf(Triple(0.37f, 0.257f, 0.594f), Triple(0.76f, 0.48f, 0.333f), Triple(0.457f, 0.608f, 0.29f))
        }
        slots.zip(images).forEach { (slot, url) ->
            val (cx, cy, d) = slot
            val diameter = w * d
            val circle = Modifier
                .offset(x = w * cx - diameter / 2, y = h * cy - diameter / 2)
                .size(diameter)
                .clip(CircleShape)
            AsyncImage(
                model = url.croppedAt(if (d > 0.5f) HEADER_ART_PX else CARD_ART_PX).whenArtworkReady(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = circle.background(Color.Black.copy(alpha = 0.18f)),
            )
        }
    }
}

private val GOOGLE_SIZE_SUFFIX = Regex("""=w\d+-h\d+[^/?]*$""")

/**
 * Artwork cut to the card's own shape by Google's image server rather than by
 * the view. An artist's header picture is a wide banner; asked for a portrait
 * or a square with `-p`, the server crops it around the subject, which a
 * centre crop of the banner does not. Anything else is sized as usual.
 */
private fun String?.croppedAt(width: Int, height: Int = width): String? {
    if (this == null) return null
    // Apple Music's photos: the last path segment is the size. Their artist
    // portraits are square, and asking for a portrait crop gets the square
    // back anyway, so the square is asked for and the card crops it.
    if ("mzstatic.com/image/thumb/" in this) {
        val side = maxOf(width, height)
        return substringBeforeLast('/') + "/${side}x${side}cc.jpg"
    }
    return if (GOOGLE_SIZE_SUFFIX.containsMatchIn(this)) {
        replace(GOOGLE_SIZE_SUFFIX, "=w$width-h$height-p-l90-rj")
    } else {
        artworkAt(maxOf(width, height))
    }
}
