package com.abhishek.ame.music.playback.audio

import android.content.Context
import android.media.AudioManager
import android.media.MediaCodecList
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.content.getSystemService
import com.abhishek.ame.music.R

/**
 * Detects and manages device-specific spatial audio capabilities.
 * 
 * Different OEMs have their own audio enhancement systems:
 * - Vivo/iQOO: DeepField Audio
 * - OnePlus/Oppo: Dirac
 * - Samsung/Xiaomi: Dolby Atmos
 * - Google Pixel (Android 13+): Android Spatializer
 * 
 * This class auto-detects which system is available and provides
 * appropriate branding for the Settings UI.
 */
object DeviceAudioCapabilities {

    enum class SpatialAudioType(
        val displayName: String,
        val description: String,
        @DrawableRes val iconRes: Int?,
        val useCustomIcon: Boolean = false,
    ) {
        DOLBY_ATMOS(
            displayName = "Dolby Atmos",
            description = "Immersive surround sound",
            iconRes = R.drawable.ic_dolby_atmos,
        ),
        DEEPFIELD(
            displayName = "DeepField Audio",
            description = "Vivo's spatial audio enhancement",
            iconRes = null,
            useCustomIcon = true,
        ),
        DIRAC(
            displayName = "Dirac Audio",
            description = "Premium sound enhancement",
            iconRes = null,
            useCustomIcon = true,
        ),
        ANDROID_SPATIAL(
            displayName = "Spatial Audio",
            description = "Android system spatial audio",
            iconRes = null,
            useCustomIcon = true,
        ),
        MI_SOUND(
            displayName = "Mi Sound",
            description = "Xiaomi audio enhancement",
            iconRes = null,
            useCustomIcon = true,
        ),
        SONY_360RA(
            displayName = "360 Reality Audio",
            description = "Sony spatial sound",
            iconRes = null,
            useCustomIcon = true,
        ),
        GENERIC_SPATIAL(
            displayName = "Spatial Audio",
            description = "Enhanced audio experience",
            iconRes = null,
            useCustomIcon = true,
        ),
        NONE(
            displayName = "",
            description = "",
            iconRes = null,
        ),
    }

    /**
     * Cached detection result to avoid repeated checks.
     */
    private var cachedType: SpatialAudioType? = null

    /**
     * Detects the spatial audio system available on this device.
     * Results are cached after first call.
     */
    fun detect(context: Context): SpatialAudioType {
        cachedType?.let { return it }

        val detected = performDetection(context)
        cachedType = detected
        return detected
    }

    /**
     * Forces re-detection (useful for testing or device changes).
     */
    fun refresh(context: Context): SpatialAudioType {
        cachedType = null
        return detect(context)
    }

    private fun performDetection(context: Context): SpatialAudioType {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()

        // NEW PRIORITY ORDER:
        // 1. Check brand-specific systems that override everything (Vivo DeepField, OnePlus Dirac)
        // 2. Then check Dolby Atmos for brands that use it (Samsung, Xiaomi, etc.)
        // 3. Finally fallback to Android Spatializer or generic
        
        // Priority 1: Vivo/iQOO → ALWAYS show DeepField (even if other systems exist)
        if (brand in listOf("vivo", "iqoo")) {
            return SpatialAudioType.DEEPFIELD
        }

        // Priority 2: OnePlus/Oppo/Realme → Check for Dirac first
        if (brand in listOf("oneplus", "oppo", "realme")) {
            return if (hasDiracService(context)) {
                SpatialAudioType.DIRAC
            } else {
                // No Dirac? Check if has Dolby
                if (hasEAC3Decoder()) SpatialAudioType.DOLBY_ATMOS
                else SpatialAudioType.GENERIC_SPATIAL
            }
        }

        // Priority 3: Check for Dolby Atmos support (E-AC-3 codec)
        // This covers Samsung, Xiaomi, Motorola, and other brands with Dolby
        val hasDolby = hasEAC3Decoder()
        if (hasDolby) {
            // Xiaomi devices with Dolby might also have Mi Sound branding
            if (brand == "xiaomi" || brand == "redmi" || brand == "poco") {
                return SpatialAudioType.DOLBY_ATMOS // Show Dolby, not Mi Sound
            }
            return SpatialAudioType.DOLBY_ATMOS
        }

        // Priority 4: Android 13+ Spatializer (only if no Dolby and no brand-specific system)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val audioManager = context.getSystemService<AudioManager>()
            try {
                val spatializer = audioManager?.spatializer
                if (spatializer?.isAvailable == true) {
                    return SpatialAudioType.ANDROID_SPATIAL
                }
            } catch (e: Exception) {
                // Spatializer API not available
            }
        }

        // Priority 5: Other brand-specific systems (without Dolby)
        return when {
            // Xiaomi devices (without Dolby - rare case)
            brand == "xiaomi" || brand == "redmi" || brand == "poco" -> {
                SpatialAudioType.MI_SOUND
            }

            // Sony devices
            brand == "sony" -> {
                if (has360RASupport(context)) SpatialAudioType.SONY_360RA
                else SpatialAudioType.GENERIC_SPATIAL
            }

            // No spatial audio available
            else -> SpatialAudioType.NONE
        }
    }

    /**
     * Checks if device has E-AC-3 (Dolby Atmos) decoder.
     */
    private fun hasEAC3Decoder(): Boolean {
        return try {
            val codecs = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            codecs.any { info ->
                !info.isEncoder && info.supportedTypes.any { 
                    it.equals("audio/eac3", ignoreCase = true) 
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if Dirac audio service is installed.
     */
    private fun hasDiracService(context: Context): Boolean {
        return try {
            // Check for Dirac ACS (Audio Control System) package
            val pm = context.packageManager
            pm.getApplicationInfo("com.dirac.acs", 0)
            true
        } catch (e: Exception) {
            // Also check for OnePlus Audio Tuner
            try {
                context.packageManager.getApplicationInfo("com.oneplus.sound.tuner", 0)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    /**
     * Checks if Sony 360 Reality Audio is supported.
     */
    private fun has360RASupport(context: Context): Boolean {
        return try {
            // Check for Sony headphones app
            val pm = context.packageManager
            pm.getApplicationInfo("com.sony.songpal.mdr", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns a user-friendly description of the detected system.
     */
    fun getCapabilityDescription(context: Context): String {
        val type = detect(context)
        return when (type) {
            SpatialAudioType.NONE -> "No spatial audio enhancement detected"
            else -> "${type.displayName} available on this device"
        }
    }

    /**
     * Opens the device's native audio effects/spatial audio settings.
     * Uses the same method as YouTube Music (audio effect control panel with session ID).
     * This is the CORRECT way that works on all OEMs.
     * 
     * @param audioSessionId The audio session ID from the active player (optional, but recommended)
     * @return true if successfully opened, false if not available
     */
    fun openNativeSettings(context: Context, audioSessionId: Int = 0): Boolean {
        val type = detect(context)
        
        // Method 1: Open Audio Effect Control Panel (works for ALL devices)
        // This is what YouTube Music, Spotify, etc. use
        try {
            val intent = android.content.Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL")
            
            // Pass the audio session ID so system knows which app is requesting
            if (audioSessionId != 0) {
                intent.putExtra("android.media.extra.AUDIO_SESSION", audioSessionId)
            }
            
            // Also pass package name
            intent.putExtra("android.media.extra.PACKAGE_NAME", context.packageName)
            intent.putExtra("android.media.extra.CONTENT_TYPE", android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            // If that fails, try device-specific fallbacks
            android.util.Log.w("DeviceAudioCapabilities", "Audio effect panel failed: ${e.message}")
        }
        
        // Method 2: Device-specific fallbacks
        return when (type) {
            SpatialAudioType.DEEPFIELD -> {
                // Vivo-specific fallbacks
                try {
                    val intent = android.content.Intent()
                    intent.setClassName(
                        "com.android.settings",
                        "com.android.settings.Settings\$SoundSettingsActivity"
                    )
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    true
                } catch (e: Exception) {
                    openSoundSettings(context)
                }
            }
            
            SpatialAudioType.DIRAC -> {
                // OnePlus/Oppo fallback: Try to open Dirac app
                try {
                    val intent = context.packageManager.getLaunchIntentForPackage("com.dirac.acs")
                    if (intent != null) {
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                        true
                    } else {
                        openSoundSettings(context)
                    }
                } catch (e: Exception) {
                    openSoundSettings(context)
                }
            }
            
            SpatialAudioType.DOLBY_ATMOS -> {
                // Samsung/Xiaomi fallback: Try to open Dolby app
                try {
                    val intent = context.packageManager.getLaunchIntentForPackage("com.dolby.daxappui")
                        ?: context.packageManager.getLaunchIntentForPackage("com.dolby.atmos")
                    if (intent != null) {
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                        true
                    } else {
                        openSoundSettings(context)
                    }
                } catch (e: Exception) {
                    openSoundSettings(context)
                }
            }
            
            else -> openSoundSettings(context)
        }
    }
    
    /**
     * Fallback: Opens general sound settings.
     */
    private fun openSoundSettings(context: Context): Boolean {
        return try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
