plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

// Convenience task to run the local Ktor backend used by Android emulator integration tests.
tasks.register("runLocalBackend") {
    group = "application"
    description = "Runs :server:run so local app/instrumentation tests can connect to backend on localhost:8080"
    dependsOn(":server:run")
}
