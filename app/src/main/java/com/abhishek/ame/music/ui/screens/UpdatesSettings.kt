package com.abhishek.ame.music.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abhishek.ame.music.BuildConfig
import com.abhishek.ame.music.data.AppUpdateChecker
import com.abhishek.ame.music.data.settings.AppSettings
import com.abhishek.ame.music.ui.icons.SfIcons
import com.abhishek.ame.music.update.Updater
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import kotlinx.coroutines.launch

// Exact Ame Card Colors & Shapes
private val CardBackground = Color(0xFF1C1C1E)
private val CardBorderColor = Color(0x14FFFFFF)
private val CardShape = RoundedCornerShape(20.dp)
private val HeaderTextColor = Color(0xFF8E8E93)

private val BetaAccent = Color(0xFFEF4444)
private val StableAccent = Color(0xFF3B82F6)

/**
 * Pixel-perfect implementation of Ame's Updates Screen matching the reference design.
 *
 * Includes:
 * 1. UPDATES card (app name, channel, version tag, status, check for updates button).
 * 2. UPDATE CHANNEL selector (Stable vs Beta Pre-release with checkmark).
 * 3. Release Notes accordion card with 3D package icon and collapsible changelog.
 * 4. Beta Channel Advisory callout banner.
 * 5. Dev Sentry diagnostics (dev builds only).
 */
@Composable
fun UpdatesSettingsContent(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val currentChannel by AppSettings.updateChannel.collectAsStateWithLifecycle()
    val isChecking by AppUpdateChecker.isChecking.collectAsStateWithLifecycle()
    val latestStable by AppUpdateChecker.latestStable.collectAsStateWithLifecycle()
    val latestBeta by AppUpdateChecker.latestBeta.collectAsStateWithLifecycle()
    val downloadState by AppUpdateChecker.download.collectAsStateWithLifecycle()

    val isBeta = currentChannel == AppSettings.UpdateChannel.BETA
    val channelAccent = if (isBeta) BetaAccent else StableAccent

    val targetRelease = when (currentChannel) {
        AppSettings.UpdateChannel.STABLE -> latestStable
        AppSettings.UpdateChannel.BETA -> latestBeta ?: latestStable
    }

    val isDifferent = targetRelease != null && targetRelease.version != BuildConfig.VERSION_NAME
    val isNewer = targetRelease != null && AppUpdateChecker.isNewer(targetRelease.version, BuildConfig.VERSION_NAME)

    val updater = remember(context) { Updater(context) }

    LaunchedEffect(Unit) {
        if (latestStable == null && latestBeta == null) {
            AppUpdateChecker.check(force = false)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ---- SECTION 1: UPDATES CARD ----
        SectionHeader("UPDATES")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(CardBackground)
                .border(1.dp, CardBorderColor, CardShape)
                .padding(18.dp),
        ) {
            // Row 1: ame, channel, and version number
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = "ame",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = Color.White,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isBeta) "Beta channel" else "Stable channel",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                        ),
                        color = HeaderTextColor,
                    )
                }

                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    ),
                    color = channelAccent,
                )
            }

            Spacer(Modifier.height(14.dp))

            // Row 2: Status indicator (Up to date or Update Available)
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = channelAccent,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Checking for updates…",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                        color = HeaderTextColor,
                    )
                } else if (isDifferent && targetRelease != null) {
                    Icon(
                        imageVector = SfIcons.Download,
                        contentDescription = null,
                        tint = channelAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Update available: v${targetRelease.version}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = channelAccent,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = HeaderTextColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Up to date",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                        color = HeaderTextColor,
                    )
                }
            }

            // Live download progress bar if downloading
            val downloading = downloadState as? AppUpdateChecker.DownloadState.Downloading
            if (downloading != null) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { downloading.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = channelAccent,
                    trackColor = Color(0x22FFFFFF),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Downloading ${(downloading.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = HeaderTextColor,
                )
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(thickness = 0.5.dp, color = Color(0x18FFFFFF))
            Spacer(Modifier.height(14.dp))

            // Row 3: Action Button (Check for updates / Install Now / Download)
            val readyState = downloadState as? AppUpdateChecker.DownloadState.Ready
            when {
                readyState != null -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF10B981))
                            .clickable {
                                AppUpdateChecker.installApk(context, readyState.file)
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Install Now",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            ),
                            color = Color.White,
                        )
                    }
                }

                isDifferent && targetRelease != null -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(channelAccent)
                            .clickable {
                                if (targetRelease.apkUrl != null) {
                                    scope.launch { AppUpdateChecker.downloadApk(context, targetRelease) }
                                } else {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetRelease.releaseUrl)))
                                }
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Download v${targetRelease.version}",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            ),
                            color = Color.White,
                        )
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { AppUpdateChecker.check(force = true) }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0x18FFFFFF)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = SfIcons.Cached,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp),
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Text(
                            text = if (isChecking) "Checking for updates…" else "Check for updates",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            ),
                            color = Color.White,
                        )
                    }
                }
            }
        }

        // ---- SECTION 2: UPDATE CHANNEL ----
        SectionHeader("UPDATE CHANNEL")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(CardBackground)
                .border(1.dp, CardBorderColor, CardShape),
        ) {
            // Stable Channel Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        AppSettings.setUpdateChannel(AppSettings.UpdateChannel.STABLE)
                        AppUpdateChecker.refreshAvailableForChannel(AppSettings.UpdateChannel.STABLE)
                        scope.launch { AppUpdateChecker.check(force = true) }
                    }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Stable",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (!isBeta) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 16.sp,
                    ),
                    color = Color.White,
                )
                if (!isBeta) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = StableAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp),
                thickness = 0.5.dp,
                color = Color(0x18FFFFFF),
            )

            // Beta (Pre-release) Channel Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        AppSettings.setUpdateChannel(AppSettings.UpdateChannel.BETA)
                        AppUpdateChecker.refreshAvailableForChannel(AppSettings.UpdateChannel.BETA)
                        scope.launch { AppUpdateChecker.check(force = true) }
                    }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Beta (Pre-release)",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isBeta) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 16.sp,
                    ),
                    color = Color.White,
                )
                if (isBeta) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = BetaAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // ---- SECTION 3: RELEASE NOTES ACCORDION ----
        var notesExpanded by remember { mutableStateOf(false) }
        val chevronRotation by animateFloatAsState(
            targetValue = if (notesExpanded) 90f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "chevronRotation",
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(CardBackground)
                .border(1.dp, CardBorderColor, CardShape)
                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { notesExpanded = !notesExpanded }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 3D Package Box Icon Container
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2C2C2E)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SfIcons.Apps,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Release Notes",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        ),
                        color = Color.White,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "v${targetRelease?.version ?: BuildConfig.VERSION_NAME} • ${if (isBeta) "Beta" else "Stable"}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                        ),
                        color = HeaderTextColor,
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = HeaderTextColor,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation),
                )
            }

            if (notesExpanded) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = Color(0x18FFFFFF),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    val notes = targetRelease?.notes?.takeIf { it.isNotBlank() }
                        ?: "No changelog published for this build."
                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                        RichText(style = RichTextStyle.Default) {
                            Markdown(notes)
                        }
                    }
                }
            }
        }

        // ---- SECTION 4: BETA CHANNEL ADVISORY BANNER ----
        if (isBeta) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(Color(0xFF241416))
                    .border(1.dp, Color(0x40EF4444), CardShape)
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = SfIcons.Warning,
                        contentDescription = null,
                        tint = BetaAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Beta Channel Advisory",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        ),
                        color = BetaAccent,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Beta builds include cutting-edge experimental features, early UI updates, and test pipelines before official public rollout. They may contain bugs, crashes, or unpolished behavior. You can switch back to Stable at any time.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                    ),
                    color = Color(0xFFE5E5EA),
                )
            }
        }

        // ---- SECTION 5: SENTRY DIAGNOSTICS (DEV BUILDS ONLY) ----
        if (BuildConfig.BUILD_TYPE == "debug" || BuildConfig.APPLICATION_ID.endsWith(".dev")) {
            SectionHeader("SENTRY DIAGNOSTICS (DEV ONLY)")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(CardBackground)
                    .border(1.dp, Color(0x25F59E0B), CardShape),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val ex = RuntimeException(
                                "Ame Dev Test Error: Sentry verification [${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}]",
                            )
                            io.sentry.Sentry.captureException(ex)
                            Toast.makeText(context, "Captured & sent test error to Sentry!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = SfIcons.Bug,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Send Test Error to Sentry",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                        Text(
                            text = "Captures non-fatal test error with device metadata",
                            style = MaterialTheme.typography.bodySmall,
                            color = HeaderTextColor,
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    thickness = 0.5.dp,
                    color = Color(0x18FFFFFF),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            throw RuntimeException("Ame Dev Fatal Crash: Sentry verification crash [${Build.MANUFACTURER} ${Build.MODEL}]")
                        }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = SfIcons.Warning,
                        contentDescription = null,
                        tint = BetaAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Trigger Test Fatal Crash",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                        Text(
                            text = "Immediate unhandled crash for Sentry verification",
                            style = MaterialTheme.typography.bodySmall,
                            color = HeaderTextColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontSize = 12.sp,
        ),
        color = HeaderTextColor,
        modifier = Modifier.padding(start = 4.dp),
    )
}
