package com.abhishek.ame.music.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.abhishek.ame.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Manages GitHub Releases update checking, APK downloading, and silent
 * package installer session staging.
 */
class Updater(context: Context) {

    private val app = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    /** The last whole percent handed to the notification; throttles notification redraws. */
    private var lastReported = -1

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    data class Release(
        val version: String,
        val url: String,
        val bytes: Long,
        val notes: String,
    )

    enum class UpdateChannel { STABLE, BETA }

    private val _channelReleases = MutableStateFlow<Map<UpdateChannel, Release>>(emptyMap())
    val channelReleases: StateFlow<Map<UpdateChannel, Release>> = _channelReleases.asStateFlow()

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(
            val version: String,
            val url: String,
            val bytes: Long,
            val notes: String = "",
        ) : State
        /** 0f..1f, or null while the server sends no Content-Length. */
        data class Downloading(val progress: Float?) : State
        /** Handed to system package installer. */
        data object Installing : State
        data class Failed(val reason: String) : State
    }

    /**
     * Checks GitHub API for updates.
     * @param installedVersionName Pass your BuildConfig.VERSION_NAME (e.g. "1.0.0" or "1.0.0-beta.1")
     */
    suspend fun check(
        channel: UpdateChannel = UpdateChannel.STABLE,
        installedVersionName: String = BuildConfig.VERSION_NAME,
    ) {
        _state.value = State.Checking
        val result = runCatching { withContext(Dispatchers.IO) { fetchRelease(channel) } }
            .getOrElse {
                android.util.Log.w(TAG, "check failed: $it")
                _state.value = State.Failed(it.message ?: "network")
                return
            }

        if (result != null) {
            _channelReleases.value += (channel to result)
        }

        val isInstalledBeta = installedVersionName.contains("-")
        val shouldOffer = when (channel) {
            UpdateChannel.STABLE -> {
                if (isInstalledBeta) {
                    result != null && result.version != installedVersionName
                } else {
                    result != null && isNewer(result.version, installedVersionName)
                }
            }
            UpdateChannel.BETA -> {
                if (!isInstalledBeta) {
                    result != null && (isNewer(result.version, installedVersionName) ||
                        result.version.substringBefore('-') == installedVersionName)
                } else {
                    result != null && isNewer(result.version, installedVersionName)
                }
            }
        }

        if (result == null || !shouldOffer) {
            _state.value = State.UpToDate
            return
        }
        _state.value = State.Available(result.version, result.url, result.bytes, result.notes)
    }

    /**
     * Initiates the background install process via [UpdateService].
     */
    fun install(update: State.Available) {
        if (!canInstall()) {
            _state.value = State.Failed(REASON_PERMISSION)
            return
        }
        _state.value = State.Downloading(null)
        UpdateService.start(app, update)
    }

    private fun fetchRelease(channel: UpdateChannel): Release? {
        val url = if (channel == UpdateChannel.STABLE) {
            "https://api.github.com/repos/$REPO/releases/latest"
        } else {
            "https://api.github.com/repos/$REPO/releases?per_page=10"
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val text = response.body?.string().orEmpty()
            val body = if (channel == UpdateChannel.STABLE) {
                json.parseToJsonElement(text) as? JsonObject
            } else {
                val array = json.parseToJsonElement(text) as? JsonArray
                val releases = array?.filterIsInstance<JsonObject>().orEmpty()
                releases.firstOrNull { it["prerelease"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true }
                    ?: releases.firstOrNull()
            } ?: return null

            val tag = body["tag_name"]?.jsonPrimitive?.content?.removePrefix("v") ?: return null
            val apks = (body["assets"] as? JsonArray)
                ?.filterIsInstance<JsonObject>()
                ?.filter { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                .orEmpty()

            fun named(part: String) = apks.firstOrNull {
                it["name"]?.jsonPrimitive?.content?.contains(part) == true
            }

            // Pick APK matching supported ABIs, fallback to universal
            val asset = Build.SUPPORTED_ABIS.firstNotNullOfOrNull(::named)
                ?: named("universal")
                ?: apks.firstOrNull()
                ?: return null

            val downloadUrl = asset["browser_download_url"]?.jsonPrimitive?.content ?: return null
            val size = asset["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val notes = body["body"]?.jsonPrimitive?.content.orEmpty().trim()
            return Release(tag, downloadUrl, size, notes)
        }
    }

    /**
     * Executes the download and pipes it into the Android PackageInstaller session.
     * Invoked from UpdateService.
     */
    suspend fun fetchAndInstall(
        url: String,
        version: String,
        bytes: Long,
        onProgress: (Float?) -> Unit,
    ) {
        val update = State.Available(version, url, bytes)
        _state.value = State.Downloading(null)
        onProgress(null)

        val apk = runCatching {
            withContext(Dispatchers.IO) { download(update, onProgress) }
        }.getOrElse {
            android.util.Log.w(TAG, "download failed: $it")
            _state.value = State.Failed(it.message ?: "download")
            return
        }

        runCatching { withContext(Dispatchers.IO) { commit(apk) } }
            .onSuccess { _state.value = State.Installing }
            .onFailure {
                android.util.Log.w(TAG, "install failed: $it")
                apk.delete()
                _state.value = State.Failed(it.message ?: "install")
            }
    }

    private fun download(update: State.Available, onProgress: (Float?) -> Unit): File {
        val target = File(app.cacheDir, "update.apk")
        target.delete()
        lastReported = -1

        val request = Request.Builder().url(update.url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("empty body")
            val total = body.contentLength().takeIf { it > 0 } ?: update.bytes
            var written = 0L

            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val progress = written.toFloat() / total
                            _state.value = State.Downloading(progress)
                            val step = (progress * 100).toInt()
                            if (step != lastReported) {
                                lastReported = step
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
        }
        return target
    }

    private fun commit(apk: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        )
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("update_stream", 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            session.commit(
                PendingIntent.getBroadcast(
                    app,
                    0,
                    Intent(app, InstallReceiver::class.java).setPackage(app.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ).intentSender,
            )
        }
        apk.delete()
    }

    /** Returns whether the user has granted REQUEST_INSTALL_PACKAGES permission. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || app.packageManager.canRequestPackageInstalls()

    /** Creates an Intent to prompt the user to allow unknown app installations. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${app.packageName}"))

    fun dismiss() {
        _state.value = State.Idle
    }

    companion object {
        private const val TAG = "Updater"

        const val REPO = "AbhishekS04/ame-music"

        const val REASON_PERMISSION = "permission"

        private fun parseVersion(v: String): Triple<List<Int>, Int, Int> {
            val clean = v.trim().removePrefix("v")
            val base = clean.substringBefore('-')
            val extra = clean.substringAfter('-', "")
            val parts = base.split('.').map { it.toIntOrNull() ?: 0 }
            val (preType, preNum) = if (extra.isBlank()) {
                Pair(999, 0)
            } else {
                val num = Regex("\\d+").find(extra)?.value?.toIntOrNull() ?: 1
                val type = when {
                    extra.contains("alpha", ignoreCase = true) -> 1
                    extra.contains("beta", ignoreCase = true) -> 2
                    extra.contains("rc", ignoreCase = true) -> 3
                    else -> 2
                }
                Pair(type, num)
            }
            return Triple(parts, preType, preNum)
        }

        /**
         * Numerically compares two SemVer versions with full pre-release suffix support.
         */
        fun isNewer(candidate: String, installed: String): Boolean {
            val (cParts, cType, cNum) = parseVersion(candidate)
            val (iParts, iType, iNum) = parseVersion(installed)
            for (i in 0 until maxOf(cParts.size, iParts.size)) {
                val c = cParts.getOrElse(i) { 0 }
                val inst = iParts.getOrElse(i) { 0 }
                if (c != inst) return c > inst
            }
            if (cType != iType) return cType > iType
            return cNum > iNum
        }
    }
}
