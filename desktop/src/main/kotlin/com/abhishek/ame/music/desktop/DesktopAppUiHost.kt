package com.abhishek.ame.music.desktop

import androidx.compose.runtime.Composable
import com.abhishek.ame.music.data.canvas.CanvasArtwork
import com.abhishek.ame.music.data.settings.LibrarySort
import com.abhishek.ame.music.ui.AppUiHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the shared pages (Home, Explore, Library, Search) read from the desktop.
 *
 * The phone answers these from AppSettings and Downloads. There are no downloads
 * on the desktop yet, and its settings store is [DesktopSettings], so the answers
 * are the desktop's own — the pages read the same values either way.
 */
object DesktopAppUiHost : AppUiHost {
    override val swipeToPlayNext: StateFlow<Boolean> = MutableStateFlow(false)
    override val pinnedPlaylists: StateFlow<List<String>> = MutableStateFlow(emptyList())
    override val librarySort: StateFlow<LibrarySort> = MutableStateFlow(LibrarySort.DEFAULT)

    /** Nothing is downloaded; an empty set is the correct answer, not a placeholder. */
    @Composable
    override fun downloadedIds(): Set<String> = emptySet()

    override suspend fun releaseMotion(album: String, artist: String): CanvasArtwork? = null

    override suspend fun artistMotion(browseId: String, artistName: String): CanvasArtwork? = null
}

/**
 * A short notice for the window, in place of the phone's Toast.
 *
 * The shared UI calls [com.abhishek.ame.music.ui.player.PlayerHost.showMessage]
 * for things like "already in this language". A desktop has no toast; this
 * carries the message to whatever is listening, and the shell draws it.
 */
object MessageBus {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun show(text: String) {
        _message.value = text
    }

    fun clear() {
        _message.value = null
    }
}
