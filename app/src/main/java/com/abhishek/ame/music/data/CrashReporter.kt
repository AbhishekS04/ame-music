package com.abhishek.ame.music.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.abhishek.ame.music.BuildConfig
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Privacy-first crash reports.
 *
 * A report is written **locally** when the app crashes and is only ever sent
 * when the listener chooses Share and picks a destination. There is no upload
 * endpoint, no analytics SDK, no install id and no automatic network call.
 *
 * The report contains the app version, Android version/API level, device
 * manufacturer/model, ABI, locale, the stack trace and a best-effort logcat
 * excerpt. Android 11+ also contributes historical process exits (native
 * crashes, ANRs, low-memory kills) through [ActivityManager].
 *
 * Automatic upload would necessarily reveal the user's IP and device to a
 * server; this design keeps the user in control instead.
 */
object CrashReporter {

    private const val DIR = "crash-reports"
    private const val PREFS = "crash_reports"
    private const val KEY_ACKNOWLEDGED = "acknowledged"
    private const val MAX_TEXT_CHARS = 64 * 1024

    /**
     * Installs the uncaught-exception handler and imports historical exits.
     * Call once, as early as possible in Application.onCreate.
     */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { writeUncaught(app, thread, error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }

        Thread({ runCatching { collectHistorical(app) } }, "crash-report-history").start()
    }

    /** Newest report on disk, acknowledged or not. */
    fun latest(context: Context): File? = reports(context).firstOrNull()

    /** Newest report the listener has not dismissed yet. */
    fun pending(context: Context): File? =
        reports(context).firstOrNull { it.name !in acknowledged(context) }

    /** Stops the startup prompt for this report without deleting it. */
    fun acknowledge(context: Context, report: File) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(KEY_ACKNOWLEDGED, acknowledged(context) + report.name)
            .apply()
    }

    /**
     * Opens the system chooser with the report as both text and a file. The
     * listener decides where it goes; the app never sends it anywhere itself.
     */
    fun share(context: Context, report: File): Boolean {
        val text = runCatching { report.readText() }.getOrElse { return false }
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", report)
        }.getOrNull()

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Ame crash report (${BuildConfig.VERSION_NAME})")
            putExtra(Intent.EXTRA_TEXT, text)
            if (uri != null) {
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("crash report", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val chooser = Intent.createChooser(send, "Share crash report")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    private fun reports(context: Context): List<File> =
        File(context.filesDir, DIR)
            .listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    private fun acknowledged(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_ACKNOWLEDGED, emptySet())
            .orEmpty()

    private fun writeUncaught(context: Context, thread: Thread, error: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val report = buildString {
            header(context, "Uncaught exception")
            appendLine()
            appendLine("Thread: ${thread.name} (id ${thread.id})")
            appendLine()
            appendLine(error.stackTraceToString())
        }
        File(dir, "crash-${System.currentTimeMillis()}.txt").writeText(report)
    }

    private fun collectHistorical(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { Historical.collect(context) }
        }
    }

    private fun header(context: Context, kind: String) = buildString {
        appendLine("Ame crash report")
        appendLine("Kind: $kind")
        appendLine("Time: ${iso(System.currentTimeMillis())}")
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Package: ${context.packageName}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Locale: ${Locale.getDefault().toLanguageTag()}")
        appendLine()
        appendLine("Logcat:")
        appendLine(readLogcat())
    }

    /**
     * Best effort: modern Android restricts an app's access to logcat, so an
     * empty or unavailable excerpt is normal and is stated in the report.
     */
    private fun readLogcat(): String = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "300")
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(1, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            return@runCatching "logcat: timed out"
        }
        process.inputStream.bufferedReader().use { it.readText() }
            .take(MAX_TEXT_CHARS)
            .ifBlank { "logcat: empty or restricted on this Android version" }
    }.getOrElse { "logcat unavailable: ${it.message}" }

    private fun readLimited(input: InputStream): String =
        runCatching {
            input.bufferedReader().use { it.readText() }.take(MAX_TEXT_CHARS)
        }.getOrDefault("trace unavailable")

    private fun iso(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)
            .apply { timeZone = TimeZone.getDefault() }
            .format(Date(ms))

    /** API 30+ only; kept in its own object so older devices never load it. */
    @RequiresApi(Build.VERSION_CODES.R)
    private object Historical {
        fun collect(context: Context) {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val now = System.currentTimeMillis()
            val exits = manager.getHistoricalProcessExitReasons(
                context.packageName,
                0,
                10,
            )

            for (info in exits) {
                val reason = info.reason
                if (reason !in REASONS) continue
                val stamp = info.timestamp
                // Ignore the current process start; it has not exited.
                if (stamp <= 0 || now - stamp < 5_000) continue

                val file = File(dir, "exit-$stamp-$reason.txt")
                if (file.exists()) continue

                val report = buildString {
                    appendLine("Ame crash report")
                    appendLine("Kind: ${reasonName(reason)}")
                    appendLine("Time: ${iso(stamp)}")
                    appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("Package: ${context.packageName}")
                    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                    appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
                    appendLine("Locale: ${Locale.getDefault().toLanguageTag()}")
                    appendLine("Process: ${info.processName ?: context.packageName}")
                    info.description?.takeIf { it.isNotBlank() }?.let {
                        appendLine("Description: $it")
                    }
                    appendLine()
                    val trace = runCatching { info.traceInputStream }.getOrNull()
                    if (trace == null) {
                        appendLine("Trace: not available")
                    } else {
                        appendLine("Trace:")
                        appendLine(readLimited(trace))
                    }
                }
                file.writeText(report)
            }
        }

        private val REASONS = setOf(
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR,
        )

        private fun reasonName(reason: Int): String = when (reason) {
            ApplicationExitInfo.REASON_CRASH -> "crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
            ApplicationExitInfo.REASON_SIGNALED -> "signaled"
            else -> "reason $reason"
        }
    }
}
