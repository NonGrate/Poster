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
// The feature catalogue (keys, defaults, dependencies, docs text) is
// buildSrc/src/main/kotlin/PosterFeatures.kt — one declaration per feature.


// The colour roles live in buildSrc/PosterPalette.kt, shared with composeApp's build script.

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
            key.startsWith("feature.") && !PosterFeatures.knows(key) ||
                key.startsWith("color.") && !PosterPalette.knows(key)
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
            val features = PosterFeatures.resolve(props)
            PosterFeatures.all.forEach { feature ->
                appendLine("    const val ${feature.constName}: Boolean = ${features.getValue(feature.key)}")
            }
            appendLine()
            appendLine("    /** The keys that are on, for diagnostics (the admin panel, a support request). */")
            appendLine("    val ENABLED: List<String> = listOf(${features.filterValues { it }.keys.joinToString { "\"$it\"" }})")
            appendLine("}")
        })

        fun argb(hex: String): String = "0xFF${PosterPalette.normalise(hex).drop(1)}"
        val palette = PosterPalette.resolve(props)
        pkgDir.resolve("BrandPalette.kt").writeText(buildString {
            appendLine("package com.example.poster.config")
            appendLine()
            appendLine("/** GENERATED from poster.properties — do not edit. ARGB as Long, for Color(Long). */")
            appendLine("object BrandPalette {")
            for (mode in PosterPalette.modes) {
                appendLine("    object ${mode.replaceFirstChar { it.uppercase() }} {")
                PosterPalette.roles.forEach { role ->
                    appendLine("        const val $role: Long = ${argb(palette.getValue(mode).getValue(role))}")
                }
                appendLine("    }")
            }
            appendLine("}")
        })
        // The same values as plain text, for the scripts (store captions, icons)
        // and anything else that cannot read Kotlin.
        outDir.get().asFile.resolve("../../palette.properties").writeText(buildString {
            appendLine("# GENERATED from poster.properties by :shared:generatePosterConfig — do not edit.")
            for (mode in PosterPalette.modes) PosterPalette.roles.forEach { role ->
                appendLine("$mode.$role=${palette.getValue(mode).getValue(role)}")
            }
        })
        // iOS reads its accent from an asset catalogue, not from Kotlin: keep the
        // one colour set there in step with the primary.
        fun rgb(hex: String): String {
            val v = hex.drop(1).toInt(16)
            return listOf(v shr 16 and 0xFF, v shr 8 and 0xFF, v and 0xFF).map { "0x%02X".format(it) }.let {
                """{ "color-space" : "srgb", "components" : { "alpha" : "1.000", "blue" : "${it[2]}", "green" : "${it[1]}", "red" : "${it[0]}" } }"""
            }
        }
        rootProject.file("iosApp/iosApp/Assets.xcassets/AccentColor.colorset/Contents.json").writeText(
            """{
  "colors" : [
    { "color" : ${rgb(palette.getValue("light").getValue("primary"))}, "idiom" : "universal" },
    { "appearances" : [ { "appearance" : "luminosity", "value" : "dark" } ], "color" : ${rgb(palette.getValue("dark").getValue("primary"))}, "idiom" : "universal" }
  ],
  "info" : { "author" : "poster.properties (generated by :shared:generatePosterConfig)", "version" : 1 }
}
"""
        )

        // The feature catalogue as a page, from the same declarations as the constants.
        rootProject.file("docs/Features.md").writeText(PosterFeatures.markdown())

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

    // Class files for Java 21 whatever JDK runs the build (the server ships this jar).
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

    // Opt-in (feature.web): the browser. Same property composeApp reads.
    if (posterProperties.getProperty("feature.web", "false").trim().toBoolean()) {
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
        wasmJs { browser() }
    }

    sourceSets {
        androidMain.dependencies {
            // Preferences on Android; iOS, the JVM and the browser have their own stores.
            implementation(libs.androidx.datastore)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.android)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        if (posterProperties.getProperty("feature.web", "false").trim().toBoolean()) {
            getByName("wasmJsMain").dependencies {
                // localStorage for preferences and the session (docs/Web.md).
                implementation(libs.kotlinx.browser)
            }
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
