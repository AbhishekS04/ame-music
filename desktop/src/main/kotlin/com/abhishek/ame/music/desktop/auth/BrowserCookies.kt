package com.abhishek.ame.music.desktop.auth

import java.io.File
import java.sql.DriverManager
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Finds the YouTube session this machine's browser already has.
 *
 * The phone's answer to "sign in" is a WebView the user types into. A desktop
 * has something better: the browser they are signed in to YouTube with right
 * now, whose cookie jar is a database on disk. Reading it means no second login.
 *
 * Only the Cookie header is taken — `SAPISID`, `SID`, `HSID`, `SSID`, `APISID`
 * and the `__Secure-*` variants. `Innertube` in shared/ builds Google's
 * SAPISIDHASH from them, so nothing here signs, mints or stores a token.
 *
 * Two very different jobs, because the two browser families store cookies in
 * very different ways:
 *
 * - **Firefox** keeps them in the clear in `moz_cookies`. Read and done.
 * - **Chromium** (Chrome, Brave, Edge, Vivaldi) encrypts with AES-128-CBC under
 *   a key from the desktop keyring, and appends the cookie's own host hash to
 *   the plaintext. Both are handled below; see [derivedKey] and [decrypt].
 *
 * Which one has a session is not guessable — a machine can be signed into
 * Google in one browser and YouTube in another, and Firefox's Google cookies
 * are scoped `.google.com` without YouTube ever having been visited. So every
 * store is tried and the first that yields the cookies a YouTube session needs
 * wins.
 */
object BrowserCookies {

    /** The cookie names a YouTube Music session is made of. */
    private val WANTED = listOf(
        "SAPISID", "__Secure-3PAPISID", "APISID", "SID", "HSID", "SSID",
        "__Secure-1PSID", "__Secure-3PSID", "__Secure-1PAPISID",
        "__Secure-3PSIDTS", "__Secure-1PSIDTS", "SIDCC",
        "__Secure-1PSIDCC", "__Secure-3PSIDCC", "LOGIN_INFO",
    )

    /** What a usable session has to contain; without it YouTube answers as a guest. */
    private const val REQUIRED = "SAPISID"

    data class Found(val cookie: String, val source: String)

    /** Every store on this machine, Firefox first because it needs no keyring. */
    private fun candidates(): List<Pair<String, File>> {
        val home = System.getProperty("user.home") ?: return emptyList()
        return buildList {
            File(home, ".mozilla/firefox").listFiles()
                ?.filter { it.isDirectory }
                ?.forEach { dir ->
                    File(dir, "cookies.sqlite").takeIf { it.isFile }
                        ?.let { add("Firefox (${dir.name})" to it) }
                }
            val chromium = listOf(
                ".config/google-chrome" to "Chrome",
                ".config/chromium" to "Chromium",
                ".config/BraveSoftware/Brave-Browser" to "Brave",
                ".config/BraveSoftware/Brave-Origin" to "Brave",
                ".config/microsoft-edge" to "Edge",
                ".config/vivaldi" to "Vivaldi",
            )
            for ((root, label) in chromium) {
                File(home, root).listFiles()
                    ?.filter { it.isDirectory }
                    ?.forEach { profile ->
                        File(profile, "Cookies").takeIf { it.isFile }
                            ?.let { add("$label (${profile.name})" to it) }
                    }
            }
        }
    }

    /**
     * The first usable YouTube session on this machine, or null.
     *
     * A store holding Google cookies but no YouTube one is skipped, not
     * returned empty: Firefox is regularly signed into Google this way, and
     * handing that over would fail at the first request instead of here.
     */
    fun findYouTubeSession(): Found? {
        for ((label, file) in candidates()) {
            val cookie = read(file) ?: continue
            if (cookie.contains(REQUIRED)) return Found(cookie, label)
        }
        return null
    }

    /** Reads one store into a Cookie header, or null if it has no YouTube session. */
    private fun read(file: File): String? {
        val copied = copyOf(file) ?: return null

        val plain = runCatching {
            DriverManager.getConnection("jdbc:sqlite:${copied.absolutePath}").use { connection ->
                // Firefox's table, then Chromium's. The host column differs too.
                query(connection, "SELECT name, value FROM moz_cookies WHERE host LIKE '%youtube.com'")
                    .ifEmpty {
                        query(connection, "SELECT name, value FROM cookies WHERE host_key LIKE '%youtube.com'")
                    }
            }
        }.getOrDefault(emptyMap())

        // Chromium's `value` is empty for everything it encrypted; the
        // encrypted_value column is what holds the session there.
        val cookies = if (plain.containsKey(REQUIRED)) plain else decryptChromium(file)
        if (!cookies.containsKey(REQUIRED)) return null
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /**
     * A private copy of a browser's cookie database.
     *
     * The browser holds it open in WAL mode, and reading it in place fails with
     * "database is locked" while it runs. The prefix must be three characters or
     * more — `File.createTempFile` rejects anything shorter.
     */
    private fun copyOf(file: File): File? = runCatching {
        File.createTempFile("ame-cookies", ".sqlite").apply {
            deleteOnExit()
            file.copyTo(this, overwrite = true)
        }
    }.getOrNull()

    private fun query(connection: java.sql.Connection, sql: String): Map<String, String> = runCatching {
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                buildMap {
                    while (rows.next()) {
                        val name = rows.getString(1) ?: continue
                        val value = rows.getString(2) ?: continue
                        if (name in WANTED && value.isNotBlank() && !value.startsWith("v1")) put(name, value)
                    }
                }
            }
        }
    }.getOrDefault(emptyMap())

    /**
     * Chromium's encrypted cookies, decrypted.
     *
     * The key is `PBKDF2(chromeSafeStorage, "saltysalt", 1 iteration, 128 bits)`.
     * In newer versions the plaintext is prefixed with SHA-256 of the host key,
     * which has to be skipped — the value after it is printable, so that is the
     * test rather than a fixed offset.
     *
     * Returns empty when the keyring is locked or the key is absent, which is a
     * normal state on a headless or freshly-booted machine, not an error.
     */
    private fun decryptChromium(file: File): Map<String, String> {
        val secret = keyringSecret("chrome") ?: keyringSecret("chromium") ?: return emptyMap()
        val key = derivedKey(secret) ?: return emptyMap()

        val copied = copyOf(file) ?: return emptyMap()

        return runCatching {
            DriverManager.getConnection("jdbc:sqlite:${copied.absolutePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(
                        "SELECT name, encrypted_value FROM cookies WHERE host_key LIKE '%youtube.com'",
                    ).use { rows ->
                        buildMap {
                            while (rows.next()) {
                                val name = rows.getString(1) ?: continue
                                if (name !in WANTED) continue
                                val encrypted = rows.getBytes(2) ?: continue
                                decrypt(encrypted, key)?.let { put(name, it) }
                            }
                        }
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    /** The key from the desktop keyring, in the clear. Null when it can't be read. */
    private fun keyringSecret(application: String): String? = runCatching {
        val process = ProcessBuilder("secret-tool", "lookup", "application", application)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        output.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * Chromium derives its key from the keyring's *base64 text*, not its decoded
     * bytes, with a single PBKDF2 iteration. Both facts are load-bearing and
     * neither is documented; using the decoded bytes produces a key that
     * decrypts nothing.
     */
    private fun derivedKey(secret: String): ByteArray? = runCatching {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(PBEKeySpec(secret.toCharArray(), "saltysalt".toByteArray(), 1, 128))
            .encoded
    }.getOrNull()

    /** One AES-128-CBC value: `v10`/`v11` prefix, zero IV of spaces, optional host hash. */
    private fun decrypt(encrypted: ByteArray, key: ByteArray): String? = runCatching {
        if (encrypted.size <= 3) return null
        val iv = ByteArray(16).also { java.util.Arrays.fill(it, ' '.code.toByte()) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val plain = cipher.doFinal(encrypted.copyOfRange(3, encrypted.size))

        // SHA-256 of the host key sits in front of the value in current builds.
        if (plain.size > 32) {
            val tail = String(plain, 32, plain.size - 32, Charsets.UTF_8)
            if (tail.all { it.code in 0x20..0x7e }) return tail
        }
        String(plain, Charsets.UTF_8)
    }.getOrNull()
}
