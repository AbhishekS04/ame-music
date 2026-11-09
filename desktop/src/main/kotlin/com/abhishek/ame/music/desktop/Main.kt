package com.abhishek.ame.music.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.SingletonImageLoader
import com.abhishek.ame.music.desktop.auth.SessionStore
import com.abhishek.ame.music.ui.AppUi
import com.abhishek.ame.music.ui.player.PlayerPlatform

/**
 * The window. Everything drawn inside it comes from sharedUi; what is installed
 * here first is the platform the shared UI reads underneath.
 *
 * Order matters: both hosts must be installed before the first composition,
 * because the screens read them during it (`PlayerPlatform.host`,
 * `AppUi.host`), and each throws rather than returning a stand-in.
 */
fun main() {
    PlayerPlatform.install(DesktopPlayerHost())
    AppUi.install(DesktopAppUiHost)
    // Before the first frame: the shelves ask Coil for their artwork inside it.
    SingletonImageLoader.setSafe(ImageLoading)
    // Sessions are read from disk synchronously, before the window is drawn, so
    // the first Home request already carries the account's cookie rather than
    // reloading once the file lands.
    SessionStore.load()

    application {
        // A window has a keyboard, and a music player is expected to answer it:
        // space for play/pause, the arrows for skip and seek.
        val keys = KeyShortcuts()
        Window(
            onCloseRequest = ::exitApplication,
            title = "Ame",
            state = rememberWindowState(size = DpSize(1280.dp, 820.dp)),
            onPreviewKeyEvent = keys::onPreviewKeyEvent,
        ) {
            DesktopApp()
        }
    }
}

/**
 * The player's keyboard, which the phone has no equivalent of.
 *
 * Registered on the window rather than in a composable so it keeps working
 * whatever has focus — a text field in Search is the one place it stands aside,
 * because space and the arrows belong to typing there.
 */
private class KeyShortcuts {
    var typing: Boolean = false

    fun onPreviewKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (typing && event.key != Key.Escape) return false
        return when (event.key) {
            Key.Spacebar -> { PlayerStore.togglePlayPause(); true }
            Key.DirectionRight -> { PlayerStore.next(); true }
            Key.DirectionLeft -> { PlayerStore.previous(); true }
            Key.MediaPlayPause -> { PlayerStore.togglePlayPause(); true }
            else -> false
        }
    }
}
