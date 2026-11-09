package com.abhishek.ame.music.desktop

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.abhishek.ame.music.data.YtMusicRepository
import com.abhishek.ame.music.data.lyrics.LyricsSource
import com.abhishek.ame.music.data.model.HomeShelf
import com.abhishek.ame.music.data.model.LibraryPage
import com.abhishek.ame.music.data.model.LikeStatus
import com.abhishek.ame.music.data.model.MoodGenreSection
import com.abhishek.ame.music.data.model.SearchFilter
import com.abhishek.ame.music.data.model.SearchResult
import com.abhishek.ame.music.data.model.ShelfItem
import com.abhishek.ame.music.data.model.Song
import com.abhishek.ame.music.data.model.UiState
import com.abhishek.ame.music.desktop.auth.SessionStore
import com.abhishek.ame.music.desktop.auth.SignInScreen
import com.abhishek.ame.music.ui.LocalPullToRefreshEnabled
import com.abhishek.ame.music.ui.LyricsProviderState
import com.abhishek.ame.music.ui.components.MiniPlayer
import com.abhishek.ame.music.ui.icons.AmeIcons
import com.abhishek.ame.music.ui.player.NowPlayingScreen
import com.abhishek.ame.music.ui.screens.ExploreScreen
import com.abhishek.ame.music.ui.screens.HomeScreen
import com.abhishek.ame.music.ui.screens.LibraryScreen
import com.abhishek.ame.music.ui.screens.SearchScreen
import com.abhishek.ame.music.ui.theme.AmeTheme
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import java.io.File

/**
 * Turns the mouse wheel over [content] into a scroll on the page's own list.
 *
 * Compose Desktop delivers a wheel as a `Scroll` pointer event, and a
 * `LazyColumn` inside a `Box` does not consume it on its own — the feed sat
 * still under the wheel. The delta is read here and handed to whichever page is
 * on screen through [WheelScrollTarget], which is a `LazyListState` for all
 * three feed pages.
 *
 * One notch reports a small fractional delta, so it is scaled: without this a
 * wheel turn moved the list a few pixels and felt broken.
 */
private const val WHEEL_SCALE = 60f

internal val LocalWheelTarget = androidx.compose.runtime.staticCompositionLocalOf<WheelScrollTarget?> { null }

/** Something the wheel can move: a page's list. */
class WheelScrollTarget(private val scrollBy: suspend (Float) -> Unit, private val scope: kotlinx.coroutines.CoroutineScope) {
    fun by(pixels: Float) {
        scope.launch { scrollBy(pixels) }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun WheelScroll(content: @Composable () -> Unit) {
    val target = LocalWheelTarget.current
    Box(
        Modifier
            .fillMaxSize()
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                target?.by(change.scrollDelta.y * WHEEL_SCALE)
                change.consume()
            },
    ) {
        content()
    }
}

/**
 * The window's shell.
 *
 * Everything in it that has a phone counterpart is the phone's own component,
 * from sharedUi: [HomeScreen], [ExploreScreen], [SearchScreen], [MiniPlayer] and
 * [NowPlayingScreen]. The tabs and their glyphs are [AmeIcons]. What is written
 * here is only the arrangement — a sidebar instead of a bottom bar, because a
 * window is wide — and the pages the phone keeps in app/ (Settings, the
 * detail pages, onboarding beyond sign-in).
 */
private enum class Page(val label: String, val icon: ImageVector) {
    HOME("Home", AmeIcons.Home),
    EXPLORE("Explore", AmeIcons.TabExplore),
    LIBRARY("Library", AmeIcons.TabLibrary),
    SEARCH("Search", AmeIcons.TabSearch),
    SETTINGS("Settings", AmeIcons.Performance),
}

/**
 * A collection opened over the tabs, as the phone pushes one.
 *
 * The phone keeps an `AppNavigator` for this; a window needs the same one-level
 * idea — a browse id and the title to head the page with — and nothing more
 * until album and artist pages land.
 */
private data class OpenCollection(val title: String, val browseId: String)

@Composable
fun DesktopApp() {
    var onboarded by remember { mutableStateOf(hasOnboarded()) }
    var page by remember { mutableStateOf(Page.HOME) }
    var playerOpen by remember { mutableStateOf(false) }
    var collection by remember { mutableStateOf<OpenCollection?>(null) }
    // The phone defaults to DARK (AppSettings.themeMode starts there), so the
    // window does too rather than following the desktop's system setting.
    val dark = true

    if (!onboarded) {
        SignInScreen(
            onSignedIn = {
                markOnboarded()
                onboarded = true
            },
        )
        return
    }
    AmeTheme(darkTheme = dark) {
        // A phone pulls to refresh; a window's mouse wheel would turn scrolling
        // past the top of a page into a refresh nobody asked for.
        CompositionLocalProvider(LocalPullToRefreshEnabled provides false) {
            val lyrics = rememberLyrics()
            // The player takes the whole window when it is open, as the phone's
            // sheet does, rather than a pane of it. `songFlow` is collected
            // rather than read off `PlayerStore.song`: a plain getter registers
            // no Compose dependency, so this branch never re-ran when playback
            // started and the player never opened.
            val nowPlaying by PlayerStore.songFlow.collectAsState()
            if (playerOpen && nowPlaying != null) {
                PlayerScreen(onClose = { playerOpen = false }, lyrics = lyrics)
                return@CompositionLocalProvider
            }

            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Sidebar(selected = page, onSelect = { page = it; collection = null })
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f)) {
                        // A collection opened from Explore or Library sits over
                        // the tab, as the phone's detail stack does; the sidebar
                        // clears it, which is the window's version of back.
                        val open = collection
                        if (open != null) {
                            CollectionPage(
                                title = open.title,
                                browseId = open.browseId,
                                onBack = { collection = null },
                            )
                        } else {
                            // A mouse wheel is vertical only, and Compose's lazy
                            // lists on desktop do not consume it on their own.
                            // One handler here turns the wheel into a scroll on
                            // the page's own list, so every page scrolls by the
                            // same amount without each screen knowing about it.
                            WheelScroll {
                                when (page) {
                                    Page.HOME -> HomePage(onOpen = { collection = it })
                                    Page.EXPLORE -> ExplorePage(onOpen = { collection = it })
                                    Page.SEARCH -> SearchPage(onOpen = { collection = it })
                                    Page.LIBRARY -> LibraryPage(onOpen = { collection = it })
                                    Page.SETTINGS -> SettingsPage()
                                }
                            }
                        }
                    }
                    MiniBar(
                        onExpand = { playerOpen = true },
                    )
                }
            }
        }
    }
}

/**
 * The mini player, when something is playing.
 *
 * The phone's own [MiniPlayer], with the phone's geometry — 56dp pill, 40dp
 * artwork, the same two transport glyphs. It reads the blur setting through
 * PlayerPlatform, so a window without the frost falls back the same way.
 */
@Composable
private fun MiniBar(onExpand: () -> Unit) {
    val song by PlayerStore.songFlow.collectAsState()
    val current = song ?: return
    val playing by PlayerStore.playing.collectAsState()
    val loading by PlayerStore.loading.collectAsState()
    val hazeState = remember { HazeState() }

    // The web player floats its transport as a centred pill over the page
    // rather than running it edge to edge, and that is the single most
    // recognisable thing about its chrome. A Box aligned to the bottom centre
    // with a capped width gives the same shape without touching MiniPlayer,
    // which already draws the pill, its frost and its two glyphs.
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.widthIn(max = 780.dp)) {
            MiniPlayer(
                song = current,
                isPlaying = playing,
                isLoading = loading,
                hazeState = hazeState,
                onPlayPause = PlayerStore::togglePlayPause,
                onNext = PlayerStore::next,
                onPrevious = PlayerStore::previous,
                onExpand = onExpand,
            )
        }
    }
}

/**
 * The full player: the phone's [NowPlayingScreen], given the desktop's queue.
 *
 * NowPlayingScreen is 2,000-odd lines of the real thing — artwork mesh, the
 * scrubber, transport, queue drawer, lyrics panel — and it is entirely shared.
 * It needs no Android, which is why the window can show the player the phone
 * shows rather than an imitation of it.
 */
@Composable
private fun PlayerScreen(onClose: () -> Unit, lyrics: List<com.abhishek.ame.music.data.lyrics.LyricLine>?) {
    val songState by PlayerStore.songFlow.collectAsState()
    val song = songState
    if (song == null) {
        onClose()
        return
    }
    val queue by PlayerStore.queue.collectAsState()
    val index by PlayerStore.index.collectAsState()
    val playing by PlayerStore.playing.collectAsState()
    val loading by PlayerStore.loading.collectAsState()
    val shuffle by PlayerStore.shuffle.collectAsState()
    val repeat by PlayerStore.repeatMode.collectAsState()
    val autoplay by PlayerStore.autoplay.collectAsState()
    val like by PlayerStore.likeStatus.collectAsState()
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        NowPlayingScreen(
            song = song,
            isPlaying = playing,
            isLoading = loading,
            position = PlayerStore.position,
            durationMs = PlayerStore.durationMsOf(song),
            audioVersionSwitching = false,
            qualityUpgraded = false,
            queue = queue,
            queueIndex = index,
            hasPrevious = index > 0,
            hasNext = index < queue.lastIndex,
            repeatMode = repeat,
            shuffleEnabled = shuffle,
            autoplayEnabled = autoplay,
            signedIn = SessionStore.isSignedIn,
            accountName = SessionStore.active?.name,
            likeStatus = like,
            onToggleLike = PlayerStore::toggleLike,
            onPlayPause = PlayerStore::togglePlayPause,
            onNext = PlayerStore::next,
            onPrevious = PlayerStore::previous,
            onBlockedControl = {},
            onSeek = PlayerStore::seekTo,
            onSeekFraction = PlayerStore::seekToFraction,
            onToggleShuffle = PlayerStore::toggleShuffle,
            onCycleRepeat = PlayerStore::cycleRepeat,
            onToggleAutoplay = PlayerStore::toggleAutoplay,
            onJumpTo = PlayerStore::jumpTo,
            onRemoveFromQueue = PlayerStore::removeAt,
            onMoveInQueue = PlayerStore::move,
            onClearQueue = PlayerStore::clearQueue,
            onOpenMenu = {},
            // Album and artist pages are app/ pages; the desktop has not ported
            // them yet, so these are inert rather than pretending to open.
            onOpenAlbum = {},
            onOpenArtist = { _, _ -> },
            onOpenPlaybackSource = {},
            onListenTogether = {},
            lyrics = lyrics,
            lyricsSource = null,
            lyricsProviderStates = emptyMap<LyricsSource, LyricsProviderState>(),
            onSelectLyricsProvider = {},
            lyricsUnavailable = lyrics == null,
            lyricsOffsetOpen = false,
            onDismissLyricsOffset = {},
            windowWidth = with(density) { 1280.dp },
            windowHeight = with(density) { 820.dp },
        )
    }
}

/** Lyrics are a later phase; an empty list is the "none yet" the player draws for. */
@Composable
private fun rememberLyrics(): List<com.abhishek.ame.music.data.lyrics.LyricLine>? = null

@Composable
private fun Sidebar(selected: Page, onSelect: (Page) -> Unit) {
    Column(
        modifier = Modifier
            .width(224.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 22.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "Ame",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp, bottom = 24.dp),
        )
        for (p in Page.entries) {
            SidebarItem(p, active = p == selected, onClick = { onSelect(p) })
        }
        Spacer(Modifier.weight(1f))
        AccountRow()
    }
}

@Composable
private fun SidebarItem(page: Page, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // A window has a pointer, so it gets what a finger cannot: hover. Quiet, and
    // off at rest, so the selected row still reads as the selected one.
    val tint = when {
        active || hovered -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = when {
                    active -> 1f
                    hovered -> 0.16f
                    else -> 0f
                }),
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(page.icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        Text(
            page.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.W600 else FontWeight.W400,
            color = tint,
        )
    }
}

@Composable
private fun AccountRow() {
    val account by SessionStore.sessions.collectAsState()
    val activeId by SessionStore.activeAccountId.collectAsState()
    val signedIn = account.firstOrNull { it.accountId == activeId }
    val profile = signedIn?.let { SessionStore.activeProfile(it) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val avatar = profile?.avatar
        if (avatar != null) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(26.dp).clip(CircleShape),
            )
        } else {
            Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Column(Modifier.weight(1f)) {
            Text(
                profile?.name?.takeIf { it.isNotBlank() } ?: signedIn?.name?.takeIf { it.isNotBlank() }
                    ?: if (signedIn != null) "Signed in" else "Signed out",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (signedIn == null) {
                Text(
                    "Home and Search only",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HomePage(onOpen: (OpenCollection) -> Unit) {
    var state by remember { mutableStateOf<UiState<List<HomeShelf>>>(UiState.Loading) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            state = UiState.Loading
            state = YtMusicRepository.home().fold(
                onSuccess = { UiState.Success(it.shelves) },
                onFailure = { UiState.Error(it.message ?: "Could not load Home") },
            )
        }
    }

    LaunchedEffect(Unit) { load() }

    CompositionLocalProvider(
        LocalWheelTarget provides WheelScrollTarget(
            scrollBy = { pixels -> listState.scrollBy(pixels) },
            scope = scope,
        ),
    ) {
        HomeScreen(
            state = state,
            listState = listState,
            onItemClick = { item, _ ->
                val id = item.videoId
                if (id != null) {
                    val songs = state.songsOf()
                    PlayerStore.playQueue(songs, songs.indexOfFirst { it.videoId == id }.coerceAtLeast(0))
                } else {
                    // A card pointing at an album or playlist: open it rather
                    // than doing nothing, which is what it used to do.
                    item.browseId?.let { onOpen(OpenCollection(item.title, it)) }
                }
            },
            onRetry = ::load,
            refreshing = false,
            onRefresh = ::load,
            pullState = rememberPullToRefreshState(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
            title = null,
            signedIn = SessionStore.isSignedIn,
            leadHero = false,
            currentSong = PlayerStore.song,
            isPlaying = PlayerStore.playing.collectAsState().value,
        )
    }
}

/** Every track a feed's shelves carry, in shelf order — the queue a tap builds. */
private fun UiState<List<HomeShelf>>.songsOf(): List<Song> {
    val shelves = (this as? UiState.Success)?.data ?: return emptyList()
    return shelves.flatMap { shelf ->
        shelf.items.mapNotNull { it.toSong() }
    }
}

@Composable
private fun ExplorePage(onOpen: (OpenCollection) -> Unit) {
    var state by remember { mutableStateOf<UiState<List<MoodGenreSection>>>(UiState.Loading) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            state = UiState.Loading
            state = YtMusicRepository.moodAndGenres().fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Could not load Explore") },
            )
        }
    }

    LaunchedEffect(Unit) { load() }

    CompositionLocalProvider(
        LocalWheelTarget provides WheelScrollTarget(
            scrollBy = { pixels -> listState.scrollBy(pixels) },
            scope = scope,
        ),
    ) {
        ExploreScreen(
            state = state,
            listState = listState,
            // A mood or genre tile opens the shelves behind it. This was an
            // empty lambda, so every tile in Explore did nothing when tapped.
            onCategoryClick = { mood -> onOpen(OpenCollection(mood.title, mood.browseId)) },
            onRetry = ::load,
            refreshing = false,
            onRefresh = ::load,
            pullState = rememberPullToRefreshState(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
            showTitle = false,
        )
    }
}

@Composable
private fun SearchPage(onOpen: (OpenCollection) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SearchFilter.ALL) }
    var results by remember { mutableStateOf<UiState<List<SearchResult>>>(UiState.Success(emptyList())) }
    var submitAt by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun search() {
        if (query.isBlank()) return
        scope.launch {
            results = YtMusicRepository.searchPage(query, filter).fold(
                onSuccess = { UiState.Success(it.rows) },
                onFailure = { UiState.Error(it.message ?: "Search failed") },
            )
        }
    }

    CompositionLocalProvider(
        LocalWheelTarget provides WheelScrollTarget(
            scrollBy = { pixels -> listState.scrollBy(pixels) },
            scope = scope,
        ),
    ) {
        SearchScreen(
            query = query,
            onQueryChange = { query = it },
            filter = filter,
            currentSong = PlayerStore.song,
            isPlaying = PlayerStore.playing.collectAsState().value,
            onFilterChange = { filter = it; search() },
            results = results,
            loadingMore = false,
            onLoadMore = {},
            listState = listState,
            scrollResetTrigger = submitAt,
            focusRequested = false,
            onFocusHandled = {},
            onSongClick = { songs, index -> PlayerStore.playQueue(songs, index) },
            onSongLongPress = {},
            onSongSwipe = {},
            onTopResultPlay = { PlayerStore.playQueue(listOf(it), 0) },
            onTopResultPlaylist = { PlayerStore.playQueue(listOf(it), 0) },
            // An album, artist or playlist hit opens; before this it was inert.
            onBrowseClick = { browse -> onOpen(OpenCollection(browse.title, browse.browseId)) },
            history = emptyList(),
            suggestions = emptyList(),
            typeaheadResults = emptyList(),
            onSubmit = { submitAt++; search() },
            onSuggestionClick = { query = it },
            onHistoryClick = {},
            onHistoryRemove = {},
            onHistoryClear = {},
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
            topPadding = 0.dp,
            showField = true,
        )
    }
}

/**
 * The Library: the phone's [LibraryScreen] with the shelves YouTube returns for
 * this account.
 *
 * The phone's page also carries Replay cards, on-device folders and the
 * downloads shelf, which are app-side features the desktop does not have yet;
 * those arrive empty, which the screen already draws as absent rather than as
 * an error.
 */
@Composable
private fun LibraryPage(onOpen: (OpenCollection) -> Unit) {
    var state by remember { mutableStateOf<UiState<LibraryPage>>(UiState.Loading) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            state = UiState.Loading
            state = YtMusicRepository.library().fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Could not load your library") },
            )
        }
    }

    LaunchedEffect(Unit) { load() }

    CompositionLocalProvider(
        LocalWheelTarget provides WheelScrollTarget(
            scrollBy = { pixels -> listState.scrollBy(pixels) },
            scope = scope,
        ),
    ) {
        LibraryScreen(
            signedIn = SessionStore.isSignedIn,
            state = state,
            listState = listState,
            onShelfItemClick = { item ->
                val id = item.videoId
                if (id != null) {
                    val pages = (state as? UiState.Success)?.data
                    val songs = pages?.shelves.orEmpty().flatMap { shelf -> shelf.items.mapNotNull { it.toSong() } }
                    if (songs.isNotEmpty()) {
                        PlayerStore.playQueue(songs, songs.indexOfFirst { it.videoId == id }.coerceAtLeast(0))
                    }
                } else {
                    // A saved playlist or album: open it. This was inert.
                    item.browseId?.let { onOpen(OpenCollection(item.title, it)) }
                }
            },
            onShelfItemLongPress = {},
            onNewPlaylist = {},
            onShowAll = {},
            replay = {},
            onSignIn = {},
            onRetry = ::load,
            refreshing = false,
            onRefresh = ::load,
            pullState = rememberPullToRefreshState(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
            links = emptyList(),
            deviceItems = emptyList(),
            showTitle = false,
        )
    }
}

@Composable
private fun PlaceholderPage(title: String, detail: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(10.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A shelf card is either a track or a collection; only the track is playable here. */
private fun ShelfItem.toSong(): Song? {
    val id = videoId ?: return null
    return Song(
        videoId = id,
        title = title,
        artist = subtitle,
        thumbnailUrl = thumbnailUrl,
        isVideo = isVideo,
    )
}

/*
 * Whether the first-run page has been answered.
 *
 * A marker file rather than a setting: the desktop has no AppSettings, and this
 * is one bit with no other meaning. Kept beside the sessions so a reset is one
 * directory.
 */
private val markDir: File = File(
    System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: "${System.getProperty("user.home")}/.config",
    "ame",
)

private fun markerFile() = File(markDir, "onboarded")

private fun hasOnboarded(): Boolean = markerFile().isFile

private fun markOnboarded() {
    runCatching {
        markDir.mkdirs()
        markerFile().writeText("1")
    }
}
