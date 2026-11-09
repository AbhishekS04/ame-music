package com.abhishek.ame.music.desktop.auth

import com.abhishek.ame.music.data.innertube.Innertube
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** A Google login kept on disk, with the identities it can act as. */
@Serializable
data class AccountSession(
    val accountId: String,
    val cookie: String,
    val name: String = "",
    val email: String = "",
    val profiles: List<Profile> = emptyList(),
    val activeProfileId: String? = null,
)

/** One Personal or Brand identity under a login. */
@Serializable
data class Profile(
    val profileId: String,
    val name: String,
    val handle: String = "",
    val avatar: String? = null,
    val pageId: String? = null,
    val dataSyncId: String? = null,
    val authUser: String? = null,
    val isBrandAccount: Boolean = false,
)

/**
 * The desktop's YouTube sessions.
 *
 * The phone's AuthStore keeps these in SharedPreferences and captures them from
 * a WebView. There is no SharedPreferences here, and no WebView: the cookie
 * comes from the browser on this machine ([BrowserCookies]) or is pasted in.
 * What it does with the cookie is the part that matters, and that is entirely
 * shared — [Innertube.cookie] plus [Innertube.adoptSessionScope], the same pair
 * the phone writes when its sign-in page hands a session back.
 *
 * Persisted to a JSON file under the user's config directory rather than the
 * keyring: the keyring needs a prompt per read, which would ask for a password
 * on every launch. The file is chmod 600, which is what a session cookie's
 * blast radius justifies without making launch unusable.
 */
object SessionStore {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val dir: File = File(
        System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: "${System.getProperty("user.home")}/.config",
        "ame",
    )

    private val file: File = File(dir, "sessions.json")

    private val _sessions = MutableStateFlow<List<AccountSession>>(emptyList())
    val sessions: StateFlow<List<AccountSession>> = _sessions.asStateFlow()

    private val _activeAccountId = MutableStateFlow<String?>(null)
    val activeAccountId: StateFlow<String?> = _activeAccountId.asStateFlow()

    /** Who is signed in, for the sidebar and the player. */
    val active: AccountSession? get() = _sessions.value.firstOrNull { it.accountId == _activeAccountId.value }

    /** The identity within [active] that requests act as. */
    fun activeProfile(session: AccountSession): Profile? =
        session.profiles.firstOrNull { it.profileId == session.activeProfileId } ?: session.profiles.firstOrNull()

    val isSignedIn: Boolean get() = active != null

    fun load() {
        val saved = runCatching {
            if (file.isFile) json.decodeFromString<List<AccountSession>>(file.readText()) else emptyList()
        }.getOrDefault(emptyList())
        _sessions.value = saved
        _activeAccountId.value = saved.firstOrNull()?.accountId
        applyToInnertube()
    }

    /**
     * Installs [cookie] as a session.
     *
     * The identity is read from the session itself rather than taken on trust:
     * [Innertube.ensureSessionScope] asks YouTube who the cookie acts as, and a
     * cookie that cannot answer is rejected here instead of failing later at the
     * first browse. The name and avatar come from the account menu, so the
     * sidebar can say who is signed in.
     */
    suspend fun signIn(cookie: String, source: String? = null): Result<AccountSession> = runCatching {
        val cleaned = cookie.trim()
        require(cleaned.isNotBlank()) { "The cookie is empty." }
        require(cleaned.contains("SAPISID") || cleaned.contains("PAPISID")) {
            "That does not look like a YouTube session: it has no SAPISID."
        }

        Innertube.cookie = cleaned
        // Asks YouTube who this is. Throws if the cookie is dead.
        Innertube.ensureSessionScope()

        val who = readIdentity()
        val account = AccountSession(
            accountId = sha256(cleaned).take(24),
            cookie = cleaned,
            name = who?.first.orEmpty(),
            email = who?.second.orEmpty(),
        )
        val merged = _sessions.value.filterNot { it.accountId == account.accountId } + account
        _sessions.value = merged
        _activeAccountId.value = account.accountId
        persist()
        account
    }

    /**
     * The signed-in account's name and email, from YouTube's own account menu.
     *
     * Best effort: the session is valid without it, and a menu that answers in an
     * unexpected shape should not fail a sign-in that already worked.
     */
    private suspend fun readIdentity(): Pair<String, String>? = runCatching {
        val menu = Innertube.accountMenu()
        val text = menu.toString()
        // The menu nests the name and email as ordinary label runs. Take the
        // first two printable runs that look like a name and an address.
        val runs = Regex("\"text\":\"([^\"]{2,60})\"").findAll(text)
            .map { it.groupValues[1] }
            .toList()
        val email = runs.firstOrNull { it.contains('@') }.orEmpty()
        val name = runs.firstOrNull { !it.contains('@') && it.isNotBlank() }.orEmpty()
        if (name.isBlank() && email.isBlank()) null else name to email
    }.getOrNull()

    /** Switches which saved login requests act as. */
    fun select(accountId: String) {
        if (_sessions.value.none { it.accountId == accountId }) return
        _activeAccountId.value = accountId
        applyToInnertube()
        persist()
    }

    /** Picks the identity — personal or brand channel — within the active login. */
    fun selectProfile(profileId: String) {
        val account = active ?: return
        val updated = account.copy(activeProfileId = profileId)
        _sessions.value = _sessions.value.map { if (it.accountId == account.accountId) updated else it }
        applyToInnertube()
        persist()
    }

    fun signOut() {
        _sessions.value = emptyList()
        _activeAccountId.value = null
        Innertube.cookie = null
        persist()
    }

    fun remove(accountId: String) {
        _sessions.value = _sessions.value.filterNot { it.accountId == accountId }
        if (_activeAccountId.value == accountId) _activeAccountId.value = _sessions.value.firstOrNull()?.accountId
        applyToInnertube()
        persist()
    }

    /** Writes the active session into the shared client. */
    private fun applyToInnertube() {
        val account = active
        if (account == null) {
            Innertube.cookie = null
            return
        }
        Innertube.cookie = account.cookie
        val profile = activeProfile(account)
        if (profile != null) {
            Innertube.selectChannel(profile.pageId, profile.dataSyncId, profile.authUser)
        }
    }

    private fun persist() {
        runCatching {
            dir.mkdirs()
            file.writeText(json.encodeToString(_sessions.value))
            // The cookie is a password in all but name.
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
