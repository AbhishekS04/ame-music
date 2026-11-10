package com.abhishek.ame.music.desktop.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.abhishek.ame.music.ui.theme.AmeTheme
import kotlinx.coroutines.launch

/**
 * Signing in, as the desktop can do it.
 *
 * The phone shows a WebView. A window has no WebView here, so the two paths are
 * the ones that work on a Linux desktop: take the session this machine's browser
 * already has, or paste a Cookie header. Both end in [SessionStore.signIn], the
 * same place, so neither is a second-class route.
 *
 * Styled as a sign-in page rather than an error, which is the same thing the web
 * player does when its extension is missing.
 */
@Composable
fun SignInScreen(onSignedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pasted by remember { mutableStateOf("") }
    var showPaste by remember { mutableStateOf(false) }

    AmeTheme(darkTheme = true) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp).padding(32.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Ame",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sign in to listen to your library, likes and playlists.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(36.dp))

                ActionRow(
                    title = "Use this browser's session",
                    detail = "Reads the YouTube cookies Firefox or Chromium already has on this machine.",
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            val found = BrowserCookies.findYouTubeSession()
                            if (found == null) {
                                error = "No signed-in YouTube session found. Sign in at music.youtube.com " +
                                    "in Firefox, then try again — or paste the cookie below."
                                busy = false
                                return@launch
                            }
                            SessionStore.signIn(found.cookie).fold(
                                onSuccess = { onSignedIn() },
                                onFailure = { error = it.message ?: "That session was refused." },
                            )
                            busy = false
                        }
                    },
                )

                Spacer(Modifier.height(12.dp))

                ActionRow(
                    title = "Paste a Cookie header",
                    detail = "From music.youtube.com in your browser's dev tools: Network → any request → Cookie.",
                    enabled = !busy,
                    onClick = { showPaste = !showPaste },
                )

                if (showPaste) {
                    Spacer(Modifier.height(14.dp))
                    BasicTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        decorationBox = { inner ->
                            if (pasted.isEmpty()) {
                                Text(
                                    "SAPISID=…; __Secure-3PAPISID=…; SID=…; HSID=…; SSID=…; APISID=…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                error = null
                                SessionStore.signIn(pasted).fold(
                                    onSuccess = { onSignedIn() },
                                    onFailure = { error = it.message ?: "That cookie was refused." },
                                )
                                busy = false
                            }
                        },
                        enabled = !busy && pasted.isNotBlank(),
                    ) { Text("Sign in") }
                }

                if (busy) {
                    Spacer(Modifier.height(20.dp))
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                }

                error?.let {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(32.dp))
                Text(
                    "You can also keep browsing signed out — Home and Search work anonymously.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable { onSignedIn() },
                )
            }
        }
    }
}

@Composable
private fun ActionRow(title: String, detail: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
