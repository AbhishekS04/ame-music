package com.abhishek.ame.music

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The home-screen icon choices. Each is an `<activity-alias>` in the manifest
 * carrying the launcher intent filter; exactly one is enabled at a time.
 *
 * The glass icons are adaptive icons whose foreground already holds the finished
 * icon in its own shape (Apple squircle, Samsung squircle or Pixel circle), so
 * [previewRes] — that foreground — shows the variant as designed rather than
 * through the device's launcher mask. The first two keep yesterday's alias
 * names so a choice made before the update stays enabled.
 */
enum class AppIcon(val alias: String, val iconRes: Int, val labelRes: Int, val previewRes: Int? = null) {
    Default("LauncherDefault", R.mipmap.ic_launcher, R.string.app_icon_default);

    private fun component(context: Context) =
        ComponentName(context.packageName, "com.abhishek.ame.music.$alias")

    companion object {
        private val ALL_LEGACY_ALIASES = listOf(
            "LauncherMonochrome", "LauncherGlassLight", "LauncherGlassDark",
            "LauncherGlassClear", "LauncherGlassClearDark", "LauncherSamsungGlass",
            "LauncherSamsungGlassDark", "LauncherSamsungGlassClear", "LauncherSamsungGlassClearDark",
            "PixelGlass", "PixelGlassDark", "PixelGlassClear", "PixelGlassClearDark",
            "NeonSilver", "NeonCyan", "NeonViolet", "SamsungNeonSilver", "SamsungNeonCyan",
            "SamsungNeonViolet", "PixelNeonSilver", "PixelNeonCyan", "PixelNeonViolet",
        )

        fun current(context: Context): AppIcon {
            val pm = context.packageManager
            return entries.firstOrNull {
                pm.getComponentEnabledSetting(it.component(context)) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } ?: Default
        }

        /** Enables [icon] before disabling the rest so there is never a moment with no launcher entry. */
        fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                icon.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(
                    it.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            ALL_LEGACY_ALIASES.forEach { legacy ->
                try {
                    pm.setComponentEnabledSetting(
                        ComponentName(context.packageName, "com.abhishek.ame.music.$legacy"),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                } catch (_: Exception) {}
            }
        }
    }
}
