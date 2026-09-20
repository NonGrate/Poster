import java.util.Properties
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}


// ---------------------------------------------------------------------------
// poster.properties, read here for the things Kotlin constants cannot reach:
// the manifest (deep-link scheme and host), the window colours behind the
// system bars, and which billing source set to compile.
// ---------------------------------------------------------------------------
val posterProperties: Properties = Properties().apply {
    rootProject.file("poster.properties").inputStream().use(::load)
}
fun posterFeature(name: String): Boolean =
    posterProperties.getProperty("feature.$name", "true").trim().toBoolean()
val posterScheme: String = posterProperties.getProperty("app.scheme", "poster").trim()
val posterWebHost: String = posterProperties.getProperty("app.webOrigin", "https://poster.example.com")
    .trim().substringAfter("://").substringBefore("/")
val supportEnabled = posterFeature("support")
val pushEnabled = posterFeature("pushNotifications")
// Opt-in, default off: a desktop target changes dependency resolution for
// everybody, and RevenueCat (feature.support) has no desktop SDK.
val desktopEnabled = posterProperties.getProperty("feature.desktop", "false").trim().toBoolean()
if (desktopEnabled && supportEnabled) {
    throw GradleException("feature.desktop=true needs feature.support=false: the billing SDK has no desktop build. See docs/Desktop.md.")
}

/**
 * The window colours (status/navigation bar, window background) as Android
 * resources, from the same palette the Compose theme uses. Generated so the
 * bars cannot drift from the paper they sit on. A task class rather than an ad
 * hoc task because AGP's variant API wants a DirectoryProperty to wire into.
 */
abstract class GeneratePosterResources : DefaultTask() {
    @get:InputFile
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val props = Properties().apply { source.get().asFile.inputStream().use(::load) }
        fun colorsXml(hex: String) = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <!-- GENERATED from poster.properties (the palette's background). -->
                <color name="poster_background">${hex.trim()}</color>
            </resources>
        """.trimIndent() + "\n"
        val palette = PosterPalette.resolve(props)
        val out = outputDir.get().asFile
        out.resolve("values").apply { mkdirs() }
            .resolve("colors.xml").writeText(colorsXml(palette.getValue("light").getValue("background")))
        out.resolve("values-night").apply { mkdirs() }
            .resolve("colors.xml").writeText(colorsXml(palette.getValue("dark").getValue("background")))
    }
}

val generatePosterResources = tasks.register<GeneratePosterResources>("generatePosterResources") {
    group = "poster"
    source.set(rootProject.file("poster.properties"))
    outputDir.set(layout.buildDirectory.dir("generated/poster/res"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(generatePosterResources, GeneratePosterResources::outputDir)
    }
}

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

/**
 * The number Play orders uploads by, and refuses to see twice.
 *
 * Counted from git rather than typed here, because a number that has to be
 * remembered is a number that gets forgotten until an upload is rejected for
 * being a duplicate — and the fix at that point is a rebuild. Commits only
 * accumulate, so this only goes up.
 *
 * Null when git could not be asked: a source download, a fresh template with no
 * history yet, or a clone with no .git. Debug builds then use 1 so the project
 * builds out of the box; a release build refuses (see the task-graph check).
 *
 * Override with -PposterVersionCode=N when a build must claim a particular
 * number: a shallow CI clone counts wrong, and a rewritten history counts
 * lower than something already uploaded.
 */
val versionCodeFromGit: Int? by lazy {
    runCatching {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val counted = process.inputStream.bufferedReader().readText().trim().toIntOrNull()
        if (process.waitFor() == 0 && counted != null && counted > 0) counted else null
    }.getOrNull()
}

val posterVersionCode: Int =
    providers.gradleProperty("posterVersionCode").orNull?.toIntOrNull() ?: versionCodeFromGit ?: 1

val posterVersionName: String =
    providers.gradleProperty("posterVersionName").orElse("1.0").get()

val remoteServerScheme = providers.gradleProperty("posterRemoteScheme").orElse("https")
val remoteServerHost = providers.gradleProperty("posterRemoteHost")
    .orElse("poster.example.com")
val remoteServerPort = providers.gradleProperty("posterRemotePort").orElse("443")

/**
 * The RevenueCat public SDK key, from a Gradle property rather than the repo.
 *
 * Public by design — it identifies the app to RevenueCat and is meant to sit in
 * the client — but a key still does not belong in version control, and the one
 * being used today is a Test Store key, which RevenueCat says must never reach
 * a store build. Empty means no key, and no key means the support screen says
 * so instead of pretending.
 */
val revenueCatKey = providers.gradleProperty("posterRevenueCatKey").orElse("")

/**
 * The Google Web OAuth client id.
 *
 * Public by nature — it ships inside the app and identifies which project a
 * token should be minted for — and it has to match what the server checks, so
 * it lives in gradle.properties rather than in somebody's private settings
 * where the two could silently drift apart. Empty means no Google sign-in and
 * no button.
 */
val googleClientId = providers.gradleProperty("posterGoogleClientId").orElse("")

/**
 * The Apple Sign in with Apple **Services ID**, used as the OAuth client id for
 * the Android browser flow. Public (it ships in the app); blank hides the Apple
 * button on Android. iOS needs none of this — it signs in natively against the
 * bundle id.
 */
val appleServiceId = providers.gradleProperty("posterAppleServiceId").orElse("")
// Firebase Cloud Messaging (feature.pushNotifications, Android). Public values
// from the Firebase console's app settings; no google-services.json needed.
val firebaseProjectId = providers.gradleProperty("posterFirebaseProjectId").orElse("")
val firebaseAppId = providers.gradleProperty("posterFirebaseAppId").orElse("")
val firebaseApiKey = providers.gradleProperty("posterFirebaseApiKey").orElse("")
val firebaseSenderId = providers.gradleProperty("posterFirebaseSenderId").orElse("")

/**
 * Screenshots only: show the support paywall with placeholder tiers when there
 * is no billing offering configured yet. Passed by the screenshot build; false
 * everywhere else, so a shipped build never carries it.
 */
val demoPaywall = providers.gradleProperty("posterDemoPaywall").orElse("false")

/**
 * A Test Store key must never be submitted to a store, so a release build that
 * carries one fails here rather than at the upload.
 *
 * Checked against the task graph rather than inside the release build type,
 * because that block is configured whatever you asked to build: the first
 * version of this refused to assemble a *debug* APK with a test key, which is
 * precisely the combination the key is for.
 */
/**
 * Where the signing key lives, or null when this machine has none.
 *
 * A file rather than gradle properties, because it holds passwords: it is
 * gitignored, and keeping it apart from the properties everybody edits makes
 * it harder to paste somewhere public by accident. Environment variables win
 * when they are set, which is how a build server signs without a file on disk.
 *
 * Null is the ordinary state. Most builds here are debug builds and nobody
 * needs the release key to work on the app.
 */
val signing: Map<String, String>? = run {
    val fromEnv = listOf(
        "POSTER_KEYSTORE", "POSTER_KEYSTORE_PASSWORD",
        "POSTER_KEY_ALIAS", "POSTER_KEY_PASSWORD",
    ).associateWith { System.getenv(it) }
    if (fromEnv.values.all { !it.isNullOrBlank() }) {
        return@run fromEnv.mapValues { it.value!! }
    }
    // The Keychain, before any file. A password stored here is not readable by
    // anything that merely has access to the disk: `security` asks macOS, and
    // macOS asks the person sitting there. That is a real boundary, and it is
    // the reason to prefer this over keystore.properties — which is only as
    // private as every process running as you, this build included.
    fromKeychain()?.let { return@run it }

    val file = rootProject.file("keystore.properties")
    if (!file.exists()) return@run null
    val properties = Properties().apply { file.inputStream().use(::load) }
    val storeFile = properties.getProperty("storeFile")
    val storePassword = properties.getProperty("storePassword")
    val keyAlias = properties.getProperty("keyAlias")
    val keyPassword = properties.getProperty("keyPassword")
    if (listOf(storeFile, storePassword, keyAlias, keyPassword).any { it.isNullOrBlank() }) {
        throw GradleException(
            "keystore.properties is missing one of storeFile, storePassword, keyAlias, keyPassword.",
        )
    }
    mapOf(
        "POSTER_KEYSTORE" to storeFile,
        "POSTER_KEYSTORE_PASSWORD" to storePassword,
        "POSTER_KEY_ALIAS" to keyAlias,
        "POSTER_KEY_PASSWORD" to keyPassword,
    )
}

/**
 * The signing password from the macOS Keychain, or null.
 *
 * Stored once with:
 *
 *     security add-generic-password -s poster-upload -a upload -w
 *
 * The keystore path and alias are not secret and come from gradle properties;
 * only the password is worth hiding, and only it is kept here.
 */
fun fromKeychain(): Map<String, String>? {
    val keystore = providers.gradleProperty("posterKeystore").orNull ?: return null
    val alias = providers.gradleProperty("posterKeyAlias").orNull ?: return null
    val service = providers.gradleProperty("posterKeychainService").orElse("poster-upload").get()
    val password = try {
        val process = ProcessBuilder("security", "find-generic-password", "-s", service, "-w")
            .redirectErrorStream(false)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        // A non-zero exit is the ordinary "no such item", not a failure worth
        // stopping the build for: the file and the environment are still to try.
        if (process.waitFor() != 0 || output.isEmpty()) return null
        output
    } catch (unavailable: Exception) {
        // Not a Mac, or no `security` on the path.
        return null
    }
    return mapOf(
        "POSTER_KEYSTORE" to keystore,
        "POSTER_KEYSTORE_PASSWORD" to password,
        "POSTER_KEY_ALIAS" to alias,
        "POSTER_KEY_PASSWORD" to password,
    )
}

gradle.taskGraph.whenReady {
    // Only an Android APK/AAB packaging task consumes the signing key, so only
    // those should demand it. Matching any "*Release*" task also snared the iOS
    // archive, which links a "linkReleaseFrameworkIos…" task through Gradle and
    // has nothing to do with the Android keystore — it signs through Xcode.
    val buildingRelease = allTasks.any { task ->
        (task.name.startsWith("assemble") || task.name.startsWith("bundle")) &&
            task.name.contains("Release")
    }
    // An unsigned release is not a release: it cannot be uploaded and cannot
    // be installed. Saying so here beats an APK that turns out to be useless
    // at the upload step, which is where this is otherwise discovered.
    if (buildingRelease && signing == null) {
        throw GradleException(
            "Release builds need the signing key. Create keystore.properties in the project root " +
                "(see docs/ReleaseSigning.md), or set POSTER_KEYSTORE, POSTER_KEYSTORE_PASSWORD, " +
                "POSTER_KEY_ALIAS and POSTER_KEY_PASSWORD.",
        )
    }
    // Git could not be asked — a source download, or a clone with no history —
    // and nobody passed a number. Shipping that would claim version 1 forever.
    if (buildingRelease && versionCodeFromGit == null &&
        providers.gradleProperty("posterVersionCode").orNull == null
    ) {
        throw GradleException(
            "versionCode could not be counted from git. Pass -PposterVersionCode=N " +
                "with a number higher than the last upload.",
        )
    }
    if (buildingRelease && revenueCatKey.get().startsWith("test_")) {
        throw GradleException(
            "posterRevenueCatKey is a Test Store key (test_...). " +
                "Release builds need the real Google Play key from RevenueCat.",
        )
    }
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)

    }

    listOf(
        // iosX64 dropped — see the note in shared/build.gradle.kts.
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
        // The purchases SDK talks to StoreKit through cinterop, which is an
        // experimental API the compiler refuses to use without being told.
        iosTarget.compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions.optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }
    }

    if (desktopEnabled) jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

    sourceSets {
        // Same pattern as billing: Firebase Messaging only when the feature is on.
        androidMain {
            kotlin.srcDir(if (pushEnabled) "src/push/enabled/kotlin" else "src/push/disabled/kotlin")
        }
        androidMain.dependencies {
            if (pushEnabled) implementation(libs.firebase.messaging)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.googleid)
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
        }

        // Billing is a swappable source set rather than an `if`: with support off
        // the RevenueCat SDK is not on the classpath at all, and the stub files
        // in src/billing/disabled satisfy the same names the Settings screen uses.
        commonMain {
            kotlin.srcDir(if (supportEnabled) "src/billing/enabled/kotlin" else "src/billing/disabled/kotlin")
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.navigation.compose)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            if (supportEnabled) {
                implementation(libs.purchases.core)
                implementation(libs.purchases.ui)
            }
            implementation(projects.shared)

            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }

        // Adds common test dependencies
        commonTest.dependencies {
            implementation(kotlin("test"))

            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }

        // Plain JVM tests for the Android-only code that has arithmetic in it.
        // Distinct from androidTest, which is instrumented: these need no
        // emulator, so they run on every build rather than when one is up.
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
        }

        if (desktopEnabled) {
            getByName("desktopMain").dependencies {
                implementation(compose.desktop.currentOs)
                // The JVM has no default Ktor engine on the classpath; CIO is the plain one.
                implementation(libs.ktor.client.cio)
                // Dispatchers.Main on the JVM is Swing's event thread.
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

if (desktopEnabled) {
    compose.desktop {
        application {
            mainClass = "com.example.poster.MainKt"
        }
    }
}

android {
    namespace = "com.example.poster"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.poster"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "com.example.poster.util.TestInstrumentationRunner"
        versionCode = posterVersionCode
        versionName = posterVersionName
        // Deep links: poster://join/CODE and https://<host>/join/CODE, from
        // poster.properties rather than typed into the manifest.
        manifestPlaceholders["posterScheme"] = posterScheme
        manifestPlaceholders["posterWebHost"] = posterWebHost
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = file(signing.getValue("POSTER_KEYSTORE"))
                storePassword = signing.getValue("POSTER_KEYSTORE_PASSWORD")
                keyAlias = signing.getValue("POSTER_KEY_ALIAS")
                keyPassword = signing.getValue("POSTER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            // R8, on for release only. Play wants a smaller download and asks
            // for the mapping file so crash reports come back readable; the
            // keep rules live in proguard-rules.pro, each with the reason it
            // is there.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Absent on a machine with no key. The task graph check above stops
            // a release build getting this far, so this is never silently
            // unsigned — it is either signed or it did not build.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    flavorDimensions += "server"
    productFlavors {
        create("remote") {
            dimension = "server"
            // No applicationIdSuffix: this is the flavour that ships, so its
            // package is the app's identity — com.example.poster, the same
            // string as the iOS bundle id. A suffix here would have put a
            // development detail in the store URL forever.
            versionNameSuffix = "-remote"
            buildConfigField("String", "SERVER_SCHEME", buildConfigString(remoteServerScheme.get()))
            buildConfigField("String", "SERVER_HOST", buildConfigString(remoteServerHost.get()))
            buildConfigField("Integer", "SERVER_PORT", remoteServerPort.get())
            buildConfigField("String", "REVENUECAT_API_KEY", buildConfigString(revenueCatKey.get()))
            buildConfigField("String", "GOOGLE_CLIENT_ID", buildConfigString(googleClientId.get()))
            buildConfigField("String", "APPLE_SERVICE_ID", buildConfigString(appleServiceId.get()))
            buildConfigField("Boolean", "DEMO_PAYWALL", demoPaywall.get())
            buildConfigField("String", "FIREBASE_PROJECT_ID", buildConfigString(firebaseProjectId.get()))
            buildConfigField("String", "FIREBASE_APP_ID", buildConfigString(firebaseAppId.get()))
            buildConfigField("String", "FIREBASE_API_KEY", buildConfigString(firebaseApiKey.get()))
            buildConfigField("String", "FIREBASE_SENDER_ID", buildConfigString(firebaseSenderId.get()))
        }
        create("e2e") {
            dimension = "server"
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            buildConfigField("String", "SERVER_SCHEME", "\"http\"")
            buildConfigField("String", "SERVER_HOST", "\"10.0.2.2\"")
            buildConfigField("Integer", "SERVER_PORT", "8080")
            // No billing in the test flavor. The instrumented suite runs
            // against a local server with no network beyond it, and a purchases
            // SDK reaching out on every launch would make every test slower and
            // occasionally wrong for reasons that have nothing to do with them.
            buildConfigField("String", "REVENUECAT_API_KEY", "\"\"")
            // No Google sign-in either. The instrumented suite has no browser,
            // no Play Services account and no business reaching Google, and a
            // button that cannot work would only change the login screen the
            // tests are written against.
            buildConfigField("String", "GOOGLE_CLIENT_ID", "\"\"")
            buildConfigField("String", "APPLE_SERVICE_ID", "\"\"")
            buildConfigField("Boolean", "DEMO_PAYWALL", "false")
            buildConfigField("String", "FIREBASE_PROJECT_ID", buildConfigString(firebaseProjectId.get()))
            buildConfigField("String", "FIREBASE_APP_ID", buildConfigString(firebaseAppId.get()))
            buildConfigField("String", "FIREBASE_API_KEY", buildConfigString(firebaseApiKey.get()))
            buildConfigField("String", "FIREBASE_SENDER_ID", buildConfigString(firebaseSenderId.get()))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.all {
            // A `runComposeUiTest` in commonTest is compiled into the Android
            // unit tests too, where there is no Android runtime: it dies on a
            // null `Build.FINGERPRINT` before the test body runs. Composing for
            // real needs a device, so these are covered by
            // iosSimulatorArm64Test and by the instrumented suite instead.
            it.exclude("**/PeriodicRefreshTest*")
        }
    }
}

// The instrumented and debug-only dependencies live here rather than inside
// androidTarget { }. Kotlin 2.4's Gradle plugin removed the target-level
// dependencies block, and these are Android configurations anyway — they were
// only ever reachable from there by the multiplatform plugin's courtesy.
dependencies {
    debugImplementation(compose.uiTooling)
    androidTestImplementation(libs.compose.ui.test.junit4.android)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.activity.compose)
    androidTestImplementation(libs.compose.ui.test.manifest)
    debugImplementation(libs.compose.ui.test.manifest)
}


/**
 * What a build would call itself, without building it.
 *
 * Play rejects an upload whose versionCode it has seen, and the number is
 * counted rather than written down, so being able to ask is worth four lines.
 */
tasks.register("printVersion") {
    val name = posterVersionName
    val code = posterVersionCode
    doLast { println("versionName=$name versionCode=$code") }
}
