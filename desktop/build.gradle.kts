import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * The Linux desktop app.
 *
 * The data layer (shared/jvmSharedMain) and the entire Compose UI
 * (sharedUi/jvmSharedMain) already compile for the JVM — they were written for
 * a desktop that this fork never checked in. What lives here is only what those
 * two cannot supply on their own: the window, the audio engine, and the few
 * host interfaces the shared UI reads (AppUiHost, PlayerHost, PlayerSettingsSource).
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

// Kotlin emits 17 (shared/sharedUi are compiled at 17); Java must match it, or
// Gradle refuses the task pair.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":sharedUi"))

    // What the desktop resolves; sharedUi's own build pins this for the same
    // reason (see the CMP_DESKTOP comment in sharedUi/build.gradle.kts).
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    // The runtime artifact the shared module is compileOnly on
    // (shared/build.gradle.kts), supplied here as it says to.
    implementation("com.microsoft.onnxruntime:onnxruntime:1.20.0")

    // Coil's network transport. sharedUi depends on coil-compose only, and the
    // phone brings the OkHttp fetcher itself (app/build.gradle.kts); without it
    // every artwork request has no fetcher and the shelves draw empty cards.
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // Reading the session this machine's browser already has:
    // Firefox and Chromium keep their cookies in SQLite.
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    /*
     * The runtime artifact shared/ is compileOnly on, for its fallback stream
     * resolver — "the two applications supply the runtime artifact themselves"
     * (shared/build.gradle.kts). Without it, StreamResolver's `init` throws
     * NoClassDefFoundError on the first play, because NewPipe's Downloader is
     * what it registers as the extractor's network layer.
     *
     * The phone re-jars this to strip a duplicate `Utils` class (see
     * app/build.gradle.kts), which is a *dexing* constraint: D8 refuses the same
     * type twice when it merges everything into one dex set. There is no dex on
     * the JVM, and the duplicate class simply resolves to whichever jar comes
     * first, so the plain artifact is correct here.
     */
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
}

compose.desktop {
    application {
        mainClass = "com.abhishek.ame.music.desktop.MainKt"
        // The RPM is the deliverable. AppImage is the same binary in a
        // single file, useful for testing without installing.
        nativeDistributions {
            targetFormats(TargetFormat.Rpm, TargetFormat.AppImage)
            packageName = "ame"
            packageVersion = "0.1.1"
            description = "Aesthetic YouTube Music client"
            vendor = "Ame"
            licenseFile.set(project.file("../LICENSE"))

            linux {
                // Registers ame:// so party invites open the app, as the phone's
                // intent filter does. This DSL has no `mimeTypes`; a file
                // association is how the scheme handler is declared.
                fileAssociation(
                    extension = "party",
                    description = "Ame party invite",
                    mimeType = "x-scheme-handler/ame",
                )
                shortcut = true
                menuGroup = "AudioVideo;Audio;Player;"
                appCategory = "AudioVideo"
                rpmLicenseType = "GPL-3.0-or-later"
            }
        }
    }
}
