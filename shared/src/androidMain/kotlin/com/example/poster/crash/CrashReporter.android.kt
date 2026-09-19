package com.example.poster.crash

import android.content.Context
import com.example.poster.model.CrashReport
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** One file, rewritten each time. See [CrashStore] for why only one. */
class FileCrashStore(context: Context) : CrashStore {
    private val file = File(context.filesDir, "pending-crash.json")

    override fun save(report: CrashReport) {
        // Synchronous, and swallowing its own failure: this runs inside a
        // handler for a process that is already going down, and a reporter that
        // throws would replace the crash being reported with its own.
        runCatching { file.writeText(Json.encodeToString(CrashReport.serializer(), report)) }
    }

    override fun pending(): CrashReport? = runCatching {
        if (!file.exists()) null else Json.decodeFromString(CrashReport.serializer(), file.readText())
    }.getOrNull()

    override fun clear() {
        runCatching { file.delete() }
    }
}

/**
 * Android hands every uncaught exception to one handler, so this catches
 * everything the JVM considers a crash. What it does not catch is a native
 * crash — a SIGSEGV in a library — which kills the process without the JVM
 * being asked.
 */
@OptIn(ExperimentalUuidApi::class)
actual fun installCrashHandler(store: CrashStore, describe: () -> DeviceDescription) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching {
            store.save(
                crashReportOf(
                    throwable = throwable,
                    device = describe(),
                    guid = Uuid.random().toString(),
                    occurredAt = Clock.System.now().toString(),
                ),
            )
        }
        // Then let Android do what it was going to do. Swallowing this would
        // leave the app in a half-dead state rather than closing it.
        previous?.uncaughtException(thread, throwable)
    }
}
