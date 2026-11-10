package com.abhishek.ame.music.data.settings

/**
 * How the app's SF Pro Display is set. [DEFAULT] draws each weight one cut
 * lighter and spaces it to Apple Music's own screens; [LEGACY] is the cut the
 * app shipped with before that, every weight its own and spaced tight.
 *
 * Lives in the shared module rather than the phone's AppSettings so the theme
 * that consumes it can too — the desktop draws the same type as the phone.
 */
enum class AppFont { DEFAULT, LEGACY }
