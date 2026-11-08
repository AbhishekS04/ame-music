package com.abhishek.ame.music.data

import com.abhishek.ame.music.BuildConfig
import com.abhishek.ame.music.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * One in-app announcement, read from a static JSON file.
 *
 * Deliberately the whole payload: there is no audience, device, version,
 * country or account field to fill in, because nothing about the caller is
 * ever read or sent. Devices decide for themselves whether to show it by
 * comparing [id] with the last id they showed.
 */
@Serializable
data class Announcement(
    val id: Int,
    val title: String = "",
    val message: String = "",
    @SerialName("action_url") val actionUrl: String? = null,
    @SerialName("action_label") val actionLabel: String? = null,
)

/**
 * Zero-tracking in-app announcements.
 *
 * One plain HTTPS GET to a static file (see `BuildConfig.ANNOUNCEMENT_URL`)
 * once per launch. The request carries no cookie, no account, no install id,
 * no query string and no custom header identifying the app. It uses its own
 * interceptor-free [client] rather than the app's shared [Http] stack, so no
 * WebDAV credential interceptor can ever attach to it.
 *
 * What the server side learns is therefore exactly what any web request
 * reveals: an IP and a time, in the host's own access log. There is no
 * analytics endpoint, no database, and no per-device record. The only state
 * kept is [AppSettings.lastSeenAnnouncementId], on this device.
 *
 * Failure is silent by design. An offline launch, a 404, malformed JSON or a
 * notice already shown all leave [pending] null and the app carries on.
 */
object Announcements {

    /** Refuses absurd bodies rather than letting a bad host balloon memory. */
    private const val MAX_BODY_CHARS = 16 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * A bare client, deliberately not [Http.client].
     *
     * The shared client carries the app's WebDAV credential interceptor and
     * a pool used by streaming. This request must never be able to pick up a
     * credential, a cookie or any other ambient state, so it gets its own
     * interceptor-free client with the default no-cookie jar.
     */
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _pending = MutableStateFlow<Announcement?>(null)

    /** The newest unread notice, or null. Exactly one can be pending at a time. */
    val pending = _pending.asStateFlow()

    suspend fun check() {
        withContext(Dispatchers.IO) {
            val url = BuildConfig.ANNOUNCEMENT_URL.trim()
            // Only HTTPS, and only when the build was configured with a URL.
            if (!url.startsWith("https://")) return@withContext

            val lastSeen = AppSettings.lastSeenAnnouncementId.value
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    // Revalidate every launch; this file is tiny and changes rarely.
                    .header("Cache-Control", "no-cache")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body
                    if (body.contentLength() > MAX_BODY_CHARS) return@use
                    val text = body.string()
                    if (text.length > MAX_BODY_CHARS) return@use

                    val notice = json.decodeFromString<Announcement>(text).sanitized()
                    if (notice.id > lastSeen && notice.message.isNotBlank()) {
                        _pending.value = notice
                    }
                }
            }
        }
    }

    /**
     * Marks [notice] as seen and clears it. Called from the dialog's dismiss
     * and from its action button, so a notice can never be shown twice.
     */
    fun dismiss(notice: Announcement) {
        AppSettings.setLastSeenAnnouncementId(notice.id)
        _pending.value = null
    }

    /**
     * Keeps a hostile or sloppy JSON file from becoming a way to render
     * arbitrary text or open a non-HTTPS link. The action URL is optional and
     * only ever handed to an external browser.
     */
    private fun Announcement.sanitized() = copy(
        id = id.coerceAtLeast(0),
        title = title.trim().take(120),
        message = message.trim().take(4000),
        actionUrl = actionUrl?.trim()?.takeIf(::isSafeActionUrl),
        actionLabel = actionLabel?.trim()?.take(40)?.ifBlank { null },
    )

    private fun isSafeActionUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
