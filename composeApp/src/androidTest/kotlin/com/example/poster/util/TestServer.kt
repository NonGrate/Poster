package com.example.poster.util

import androidx.test.platform.app.InstrumentationRegistry

/**
 * Where the instrumented tests reach the local backend.
 *
 * The default 10.0.2.2 is the host machine's loopback as seen from an Android
 * emulator. A real device has no such alias, so run those with
 * `adb reverse tcp:8080 tcp:8080` and pass `-e serverHost 127.0.0.1`.
 */
object TestServer {
    val host: String = runCatching {
        InstrumentationRegistry.getArguments().getString("serverHost")
    }.getOrNull() ?: "10.0.2.2"

    const val port = 8080
}
