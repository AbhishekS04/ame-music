package com.abhishek.ame.music.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abhishek.ame.music.data.YtMusicRepository
import com.abhishek.ame.music.data.model.Song
import com.abhishek.ame.music.data.model.UiState
import com.abhishek.ame.music.ui.components.SongRow
import com.abhishek.ame.music.ui.icons.AmeIcons
import kotlinx.coroutines.launch

/**
 * A collection's tracks: a mood, a genre, an album or a playlist.
 *
 * Explore's tiles and Library's shelves were inert — their callbacks were empty
 * lambdas, so tapping one did nothing at all. They now open this: one page that
 * asks the repository for the tracks behind a browse id and lists them, and a
 * row tap plays the whole page as the queue from that row, which is how Search
 * already builds one.
 *
 * A header names the collection and carries a Play button, because arriving at a
 * list of tracks with no way to start them is the other half of the same
 * complaint.
 */
@Composable
fun CollectionPage(
    title: String,
    browseId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember(browseId) { mutableStateOf<UiState<List<Song>>>(UiState.Loading) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            state = UiState.Loading
            state = YtMusicRepository.browseSongs(browseId).fold(
                onSuccess = { UiState.Success(it.songs) },
                onFailure = { UiState.Error(it.message ?: "Could not open $title") },
            )
        }
    }

    LaunchedEffect(browseId) { load() }

    Column(modifier.fillMaxSize()) {
        // Back, because a window has no system back gesture and this page is
        // reached from a tab that is still underneath it.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                AmeIcons.ChevronRight,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(50))
                    .androidxClickable(onBack),
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.W700,
            )
        }

        when (val current = state) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
            }

            is UiState.Error -> Column(
                Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(current.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Try again",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.androidxClickable(::load),
                )
            }

            is UiState.Success -> {
                val songs = current.data
                CompositionLocalProvider(
                    LocalWheelTarget provides WheelScrollTarget(
                        scrollBy = { pixels -> listState.scrollBy(pixels) },
                        scope = scope,
                    ),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item(key = "play-all") {
                            PlayAllRow(count = songs.size) { PlayerStore.playQueue(songs, 0) }
                        }
                        items(songs, key = { it.videoId }) { song ->
                            SongRow(
                                song = song,
                                onClick = {
                                    PlayerStore.playQueue(songs, songs.indexOfFirst { it.videoId == song.videoId })
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayAllRow(count: Int, onPlay: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .androidxClickable(onPlay)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(AmeIcons.Play, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Text(
            "Play all · $count tracks",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.W600,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A clickable that says nothing else, so the rows above stay readable. */
private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
