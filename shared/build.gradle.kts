import java.util.Properties
import org.gradle.kotlin.dsl.implementation
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.sqldelight)
}


// ---------------------------------------------------------------------------
// poster.properties -> generated Kotlin (Features, BrandPalette, AppInfo).
//
// One file at the repo root decides what the app is called, which features
// are compiled in and what colours it wears; this turns it into constants the
// whole project (Android, iOS via the framework, and the server) reads.
// `const val` for the flags is the point: `if (Features.LIKES)` around a
// screen is dead code to the compiler and to R8 when the flag is false.
// ---------------------------------------------------------------------------
val posterProperties: Properties = Properties().apply {
    rootProject.file("poster.properties").inputStream().use(::load)
}

/** feature.<camelCase> -> CONST_NAME. Unknown keys are reported, not ignored. */
val posterFeatureKeys = listOf(
    "groups", "tags", "likes", "sharing", "postCompletion", "postVisibility",
    "dailyReminder", "feedback", "support", "reports", "crashReports", "telemetry",
    "emailVerificationRequired", "multiLanguage", "googleSignIn", "appleSignIn",
    "images", "liquidDesign", "liquidNavBar", "comments", "pushNotifications", "magicLink", "authors", "publicGroups", "follows", "bookmarks", "drafts", "offlineOutbox", "desktop",
)

/**
 * The flags a missing key leaves OFF, against the general rule below that a
 * missing key means on. These three are opt-ins that change the shape of a
 * build, and defaulting them on would add a desktop target and a different look
 * to anyone who deleted the line.
 *
 * `desktop` is also read straight out of poster.properties by composeApp's
 * build script, which decides whether to add the jvm("desktop") target at all.
 */
val posterOptInFeatures = setOf("desktop", "liquidDesign", "liquidNavBar")

val posterColorRoles = listOf(
    "primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "inversePrimary",
    "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
    "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
    "error", "onError", "errorContainer", "onErrorContainer",
    "background", "onBackground", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
    "surfaceTint", "outline", "outlineVariant", "scrim", "inverseSurface", "inverseOnSurface",
    "surfaceBright", "surfaceDim", "surfaceContainerLowest", "surfaceContainerLow",
    "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest",
)

fun String.toConstName(): String = replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()

val generatePosterConfig by tasks.registering {
    group = "poster"
    description = "Generates Features/BrandPalette/AppInfo from poster.properties"
    val source = rootProject.file("poster.properties")
    val outDir = layout.buildDirectory.dir("generated/poster/commonMain/kotlin")
    inputs.file(source)
    outputs.dir(outDir)
    doLast {
        val props = Properties().apply { source.inputStream().use(::load) }
        val unknown = props.stringPropertyNames().filter { key ->
            key.startsWith("feature.") && key.removePrefix("feature.") !in posterFeatureKeys ||
                key.startsWith("color.") && key.split(".").getOrNull(2) !in posterColorRoles
        }
        if (unknown.isNotEmpty()) {
            throw GradleException("poster.properties has keys nothing reads: $unknown")
        }
        val pkgDir = outDir.get().asFile.resolve("com/example/poster/config").apply { mkdirs() }

        pkgDir.resolve("Features.kt").writeText(buildString {
            appendLine("package com.example.poster.config")
            appendLine()
            appendLine("/** GENERATED from poster.properties — do not edit. Flip a flag there instead. */")
            appendLine("object Features {")
            posterFeatureKeys.forEach { key ->
                val default = if (key in posterOptInFeatures) "false" else "true"
                val enabled = props.getProperty("feature.$key", default).trim().toBoolean()
                appendLine("    const val ${key.toConstName()}: Boolean = $enabled")
            }
            appendLine("}")
        })

        fun argb(hex: String): String {
            val clean = hex.trim().removePrefix("#")
            require(clean.length == 6 && clean.all { it.isLetterOrDigit() }) { "bad colour: $hex" }
            return "0xFF${clean.uppercase()}"
        }
        pkgDir.resolve("BrandPalette.kt").writeText(buildString {
            appendLine("package com.example.poster.config")
            appendLine()
            appendLine("/** GENERATED from poster.properties — do not edit. ARGB as Long, for Color(Long). */")
            appendLine("object BrandPalette {")
            for (mode in listOf("light", "dark")) {
                appendLine("    object ${mode.replaceFirstChar { it.uppercase() }} {")
                posterColorRoles.forEach { role ->
                    val value = props.getProperty("color.$mode.$role")
                        ?: throw GradleException("poster.properties is missing color.$mode.$role")
                    appendLine("        const val $role: Long = ${argb(value)}")
                }
                appendLine("    }")
            }
            appendLine("}")
        })

        fun str(key: String, default: String) = props.getProperty(key, default).trim()
        pkgDir.resolve("AppInfo.kt").writeText(buildString {
            appendLine("package com.example.poster.config")
            appendLine()
            appendLine("/** GENERATED from poster.properties — do not edit. */")
            appendLine("object AppInfo {")
            appendLine("    const val NAME: String = \"${str("app.name", "Poster")}\"")
            appendLine("    const val SCHEME: String = \"${str("app.scheme", "poster")}\"")
            appendLine("    const val WEB_ORIGIN: String = \"${str("app.webOrigin", "https://poster.example.com").trimEnd('/')}\"")
            appendLine("}")
        })
    }
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // iosX64 — the Intel Mac simulator — is gone. Compose Multiplatform 1.12
    // no longer publishes for it, and it has not been buildable on this machine
    // for as long as this machine has been Apple Silicon. Real devices are
    // iosArm64; the simulator on any Mac made since 2020 is iosSimulatorArm64.
    iosArm64()
    iosSimulatorArm64()

    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.android)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        commonMain {
            kotlin.srcDir(generatePosterConfig)
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(libs.koin.core)

            implementation(libs.androidx.datastore)
            implementation(libs.androidx.datastore.preferences)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
        }
    }
}

sqldelight {
    databases {
        create("PostDatabase") {
            packageName.set("com.example.poster")
            srcDirs.setFrom("src/commonMain/sqldelight")

            // Migrations, from here on.
            //
            // The client database is created from the schema and never upgraded,
            // which was survivable while nothing read it and is not now that it
            // holds the feed, favorites and the offline copy. A device with an
            // older file keeps it, the version says 1 on both sides, so nothing
            // migrates and nothing complains — until a query names a column that
            // is not there.
            //
            // Adding a column now means: write `<version>.sqm` next to the .sq
            // files, run `./gradlew :shared:generatePostDatabaseSchema` to
            // record the new baseline, and commit both. verifyMigrations fails
            // the build if a migration does not produce the schema the .sq files
            // describe, so the two cannot drift apart.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

android {
    namespace = "com.example.poster.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
