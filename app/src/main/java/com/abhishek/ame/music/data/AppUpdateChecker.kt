package com.abhishek.ame.music.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.abhishek.ame.music.BuildConfig
import com.abhishek.ame.music.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.File

/**
 * Ame ships as a sideloaded APK off GitHub Releases rather than through
 * a store, so there's nothing to push an update notice on its own — this
 * polls the repo's releases and compares tags against the running build.
 *
 * Supports both Stable and Beta update channels, on-demand checking,
 * background APK downloading into the app cache, and system package
 * installer handoff.
 */
object AppUpdateChecker {

    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        val apkUrl: String?,
        /** The release's own Markdown body, shown as this update's "what's new". */
        val notes: String?,
        val isBeta: Boolean = false,
        val publishedAt: String? = null,
    )

    private const val CACHE_SUBDIR = "updates"

    private const val PHONE_APK_PREFIX = "ame-"
    private const val TV_APK_PREFIX = "ame-tv-"

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/AbhishekS04/ame-music/releases/latest"
    private const val ALL_RELEASES_URL =
        "https://api.github.com/repos/AbhishekS04/ame-music/releases?per_page=20"

    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available = _available.asStateFlow()

    private val _latestStable = MutableStateFlow<UpdateInfo?>(null)
    val latestStable = _latestStable.asStateFlow()

    private val _latestBeta = MutableStateFlow<UpdateInfo?>(null)
    val latestBeta = _latestBeta.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking = _isChecking.asStateFlow()

    /** Where this update's APK download currently stands, for progress rows. */
    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Downloading(val fraction: Float) : DownloadState
        data class Ready(val file: File) : DownloadState
        data class Failed(val message: String) : DownloadState
    }

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download = _download.asStateFlow()

    /** Set from the UI thread when the user cancels; polled between network reads. */
    @Volatile
    private var downloadCancelled = false

    /**
     * Reads the repository's published releases to find the latest Stable and Beta builds.
     */
    suspend fun check(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (_isChecking.value && !force) return@withContext
        _isChecking.value = true
        runCatching {
            val request = Request.Builder()
                .url(ALL_RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

            Http.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val raw = response.body.string()
                    val releases = json.parseToJsonElement(raw).jsonArray

                    var foundStable: UpdateInfo? = null
                    var foundBeta: UpdateInfo? = null

                    for (element in releases) {
                        val obj = element as? JsonObject ?: continue
                        val isDraft = obj["draft"]?.jsonPrimitive?.booleanOrNull ?: false
                        if (isDraft) continue

                        val isPrerelease = obj["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false
                        val tag = obj["tag_name"]?.jsonPrimitive?.contentOrNull
                            ?.trim()
                            ?.removePrefix("v")
                            ?.removePrefix("V") ?: continue
                        val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val isBetaRelease = isPrerelease ||
                            tag.contains("beta", ignoreCase = true) ||
                            name.contains("beta", ignoreCase = true)

                        val info = UpdateInfo(
                            version = tag,
                            releaseUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            apkUrl = apkAssetUrl(obj),
                            notes = obj["body"]?.jsonPrimitive?.contentOrNull,
                            isBeta = isBetaRelease,
                            publishedAt = obj["published_at"]?.jsonPrimitive?.contentOrNull,
                        )

                        if (isBetaRelease && foundBeta == null) {
                            foundBeta = info
                        } else if (!isBetaRelease && foundStable == null) {
                            foundStable = info
                        }
                        if (foundStable != null && foundBeta != null) break
                    }

                    if (foundStable != null) _latestStable.value = foundStable
                    if (foundBeta != null) _latestBeta.value = foundBeta
                }
            }
        }.onFailure {
            // Fallback to latest endpoint if all releases failed or rate limited
            runCatching {
                val req = Request.Builder()
                    .url(LATEST_RELEASE_URL)
                    .header("Accept", "application/vnd.github+json")
                    .build()
                Http.client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val obj = json.parseToJsonElement(resp.body.string()).jsonObject
                        val tag = obj["tag_name"]?.jsonPrimitive?.contentOrNull
                            ?.trim()
                            ?.removePrefix("v")
                            ?.removePrefix("V") ?: return@use
                        val info = UpdateInfo(
                            version = tag,
                            releaseUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            apkUrl = apkAssetUrl(obj),
                            notes = obj["body"]?.jsonPrimitive?.contentOrNull,
                            isBeta = false,
                            publishedAt = obj["published_at"]?.jsonPrimitive?.contentOrNull,
                        )
                        _latestStable.value = info
                    }
                }
            }
        }

        refreshAvailableForChannel(AppSettings.updateChannel.value)
        _isChecking.value = false
    }

    /**
     * Updates [_available] for the chosen channel.
     */
    fun refreshAvailableForChannel(channel: AppSettings.UpdateChannel) {
        val target = when (channel) {
            AppSettings.UpdateChannel.STABLE -> _latestStable.value
            AppSettings.UpdateChannel.BETA -> _latestBeta.value ?: _latestStable.value
        }
        if (target != null && isNewer(target.version, BuildConfig.VERSION_NAME)) {
            _available.value = target
        } else {
            _available.value = null
        }
    }

    /**
     * Wipes any APK left over from a previous run.
     */
    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        File(context.cacheDir, CACHE_SUBDIR).listFiles()?.forEach { it.delete() }
    }

    /**
     * Returns the download URL for this device's architecture or universal APK.
     */
    private fun apkAssetUrl(release: JsonObject): String? = runCatching {
        val prefix = BuildConfig.UPDATE_APK_PREFIX
        val isPhoneBuild = prefix.equals(PHONE_APK_PREFIX, ignoreCase = true)
        val apks = release["assets"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { asset ->
                val name = asset["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val isTvAsset = name.startsWith(TV_APK_PREFIX, ignoreCase = true)
                val ours = name.startsWith(prefix, ignoreCase = true) &&
                    !(isPhoneBuild && isTvAsset) &&
                    name.endsWith(".apk", ignoreCase = true) &&
                    asset["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
                if (ours) name.removeSuffix(".apk").removeSuffix(".APK") to url else null
            }
            .orEmpty()
        // Prefer device's specific architecture APK (e.g. arm64-v8a) first for optimal size and performance;
        // fallback to universal build (no `_ABI` suffix) if no matching ABI split was uploaded.
        Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi ->
            apks.firstOrNull { (name, _) -> name.endsWith("_$abi", ignoreCase = true) }?.second
        } ?: apks.firstOrNull { (name, _) -> '_' !in name }?.second
    }.getOrNull()

    /**
     * Streams the APK into the app cache, reporting progress through [download].
     */
    suspend fun downloadApk(context: Context, specificInfo: UpdateInfo? = null): Unit = withContext(Dispatchers.IO) {
        val info = specificInfo ?: _available.value ?: return@withContext
        val url = info.apkUrl ?: return@withContext
        downloadCancelled = false
        _download.value = DownloadState.Downloading(0f)

        runCatching {
            val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "ame-${info.version}.apk")

            val request = Request.Builder().url(url).build()
            Http.client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                val body = response.body ?: error("Empty download body")
                val total = body.contentLength().takeIf { it > 0 }

                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var readTotal = 0L
                        while (true) {
                            if (downloadCancelled) {
                                _download.value = DownloadState.Idle
                                return@withContext
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            readTotal += read
                            total?.let {
                                _download.value =
                                    DownloadState.Downloading((readTotal.toFloat() / it).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            _download.value = DownloadState.Ready(target)
        }.onFailure { error ->
            _download.value = if (downloadCancelled) {
                DownloadState.Idle
            } else {
                DownloadState.Failed(error.message ?: "Download failed")
            }
        }
    }

    /** Stops an in-flight download; the next read loop sees this and bails. */
    fun cancelDownload() {
        downloadCancelled = true
    }

    /** Back to square one after a failure, so the dialog offers Download again. */
    fun resetDownload() {
        _download.value = DownloadState.Idle
    }

    /**
     * Hands a downloaded APK to the system installer.
     */
    fun installApk(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val opened = runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
            if (opened) return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                ),
        )
    }

    /** A version split into its numeric dotted parts and pre-release suffix info. */
    private data class ParsedVersion(
        val parts: List<Int>,
        val isPreRelease: Boolean,
        val preReleaseNum: Int = 0,
    )

    private fun parseVersion(raw: String): ParsedVersion {
        val dash = raw.indexOf('-')
        val base = if (dash >= 0) raw.substring(0, dash) else raw
        val isPre = dash >= 0
        var preNum = 0
        if (isPre) {
            val suffix = raw.substring(dash + 1)
            preNum = suffix.filter { it.isDigit() }.toIntOrNull() ?: 0
        }
        return ParsedVersion(
            parts = base.split(".").map { it.toIntOrNull() ?: 0 },
            isPreRelease = isPre,
            preReleaseNum = preNum,
        )
    }

    /**
     * Numeric, dot-separated comparison with pre-release handling.
     */
    fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        if (c.isPreRelease && !l.isPreRelease) return true
        if (!c.isPreRelease && l.isPreRelease) return false
        if (c.isPreRelease && l.isPreRelease) {
            return l.preReleaseNum > c.preReleaseNum
        }
        return false
    }
}
