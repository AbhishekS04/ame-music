package com.abhishek.ame.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abhishek.ame.music.R
import com.abhishek.ame.music.data.Announcement
import com.abhishek.ame.music.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/** Long notices scroll inside the card rather than growing it off the screen. */
private val MESSAGE_MAX_HEIGHT = 260.dp

/**
 * A one-time in-app announcement, shaped like the rest of Ame's iOS-style
 * alerts and drawn over the app for the same reason as
 * [UpdateAvailableDialog]: its frosted card samples the app's own [HazeState].
 *
 * Dismissing always marks the notice seen, whether through the action button,
 * the OK button, the scrim or Back. The server is never told what happened.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AnnouncementDialog(
    announcement: Announcement,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onOpenAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(ALERT_CORNER)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SCRIM_COLOR)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(ALERT_WIDTH)
                .clip(shape)
                .then(
                    if (reduceDynamicBlur) {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    } else {
                        Modifier.optimizedHazeEffect(
                            state = hazeState,
                            style = HazeMaterials.regular(MaterialTheme.colorScheme.surface),
                        )
                    },
                )
                // Swallows taps on the card itself.
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = announcement.title.ifBlank { stringResource(R.string.announcement_default_title) },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.W600,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = announcement.message,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .heightIn(max = MESSAGE_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }

            AlertRule()
            if (announcement.actionUrl != null) {
                AlertAction(
                    label = announcement.actionLabel ?: stringResource(R.string.announcement_open),
                    emphasised = true,
                    onClick = onOpenAction,
                )
                AlertRule()
                AlertAction(
                    label = stringResource(R.string.announcement_ok),
                    emphasised = false,
                    onClick = onDismiss,
                )
            } else {
                AlertAction(
                    label = stringResource(R.string.announcement_ok),
                    emphasised = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}
