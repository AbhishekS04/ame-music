package com.abhishek.ame.music.desktop

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abhishek.ame.music.desktop.auth.SessionStore
import com.abhishek.ame.music.ui.icons.AmeIcons

/**
 * Settings, as the desktop can honestly offer them.
 *
 * The phone's SettingsSheet is 1,900 lines and reads AppSettings, which is an
 * Android SharedPreferences store this module does not have. Rather than draw
 * its rows against values that do not exist, this shows the settings that *are*
 * real here — the session, the account, and the few values DesktopSettings
 * persists — and says plainly where the rest stands. A settings page full of
 * switches that do nothing would be worse than a short one that works.
 */
@Composable
fun SettingsPage() {
    val account by SessionStore.sessions.collectAsState()
    val activeId by SessionStore.activeAccountId.collectAsState()
    val signedIn = account.firstOrNull { it.accountId == activeId }
    val profile = signedIn?.let { SessionStore.activeProfile(it) }
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        SettingsSection("Account")
        SettingsRow(
            icon = AmeIcons.Home,
            title = profile?.name?.takeIf { it.isNotBlank() } ?: signedIn?.name ?: "Not signed in",
            detail = signedIn?.email?.takeIf { it.isNotBlank() }
                ?: "Home and Search work signed out. Everything else needs a session.",
        )
        if (signedIn != null) {
            val brand = signedIn.profiles.filter { it.isBrandAccount }
            if (brand.isNotEmpty()) {
                SettingsRow(
                    icon = AmeIcons.TabLibrary,
                    title = "Channel",
                    detail = "acting as ${profile?.name ?: "the personal channel"} · ${brand.size} brand channel(s) available",
                )
            }
            ActionRow("Sign out", "Forget this session. Your browser stays signed in.") {
                SessionStore.signOut()
            }
        }

        SettingsSection("Playback")
        ToggleRow(
            title = "Automix",
            detail = "Beat-matched transitions between tracks. Needs analysis, which is not wired yet.",
            checked = false,
            enabled = false,
            onChange = {},
        )
        ToggleRow(
            title = "Gapless playback",
            detail = "Needs a PCM mixer; the engine plays one track at a time today.",
            checked = false,
            enabled = false,
            onChange = {},
        )
        SettingsRow(
            icon = AmeIcons.Performance,
            title = "Output",
            detail = "16-bit PCM · 48 kHz · via ffmpeg and the system mixer",
        )

        SettingsSection("Appearance")
        ToggleRow(
            title = "Animated artwork",
            detail = "Video canvases need a video decoder on the desktop.",
            checked = false,
            enabled = false,
            onChange = {},
        )

        SettingsSection("About")
        SettingsRow(icon = AmeIcons.Star, title = "Ame Desktop", detail = "0.1.1 · Linux · GPL-3.0")
        SettingsRow(
            icon = AmeIcons.Clock,
            title = "Where settings live",
            detail = "~/.config/ame/ — sessions.json (chmod 600) and the first-run marker",
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.W600,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = 2.dp, start = 4.dp),
    )
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ActionRow(title: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            // The row is a button, so the whole of it is the target — the
            // earlier version drew the arrow but never made the row clickable,
            // so Sign out did nothing.
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "→",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/*
 * Wheel handling lives in App.kt's WheelScroll: the three feed pages each own a
 * LazyColumn, and a window's wheel has to reach whichever one is on screen.
 */
