plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinxSerialization)
    application
}

group = "com.example.poster"
version = "1.0.0"
application {
    mainClass.set("com.example.poster.ApplicationKt")
    val developmentMode = providers.gradleProperty("ktor.development").orElse("false")
    val databasePath = providers.gradleProperty("poster.database").orElse("post.db")
    applicationDefaultJvmArgs = listOf(
        "-Dio.ktor.development=${developmentMode.get()}",
        "-Dposter.database=${databasePath.get()}",
    )

    // The runtime image is built from this distribution and nothing else, so a
    // script that is only in the repository is not on the server. The backup
    // task runs /app/scripts/backup-database.sh, which is this line.
    applicationDistribution.from("$rootDir/scripts/backup-database.sh") {
        into("scripts")
        filePermissions { unix("0755") }
    }
}


// Shared artwork lives at the repo root, because the store listing and the app
// want it too — not only this server. Adding it as a resource directory keeps one
// file rather than a copy per consumer.
sourceSets["main"].resources.srcDir("../assets")

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.server.sessions)
    implementation(libs.bouncycastle)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.sqldelight.sqlite.driver)
    implementation(libs.kotlinx.datetime)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    // Sending mail through a provider's HTTP API rather than SMTP: no daemon on
    // the box, and no outbound port 25 for a cloud host to block.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
}

tasks.withType<Test>().configureEach {
    // Server tests run the way the server runs: with the schema's cascades
    // actually firing. Set here rather than by the code under test, so it does
    // not depend on which test happened to start a module first.
    systemProperty("poster.enforceForeignKeys", "true")
}
