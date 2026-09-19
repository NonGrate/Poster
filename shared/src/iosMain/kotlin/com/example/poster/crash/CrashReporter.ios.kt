package com.example.poster.crash

import com.example.poster.model.CrashReport
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import kotlin.experimental.ExperimentalNativeApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** One file, rewritten each time. See [CrashStore] for why only one. */
@OptIn(ExperimentalForeignApi::class)
class FileCrashStore : CrashStore {
    private val path: String
        get() {
            val documents = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory,
                NSUserDomainMask,
                true,
            ).first() as String
            return "$documents/pending-crash.json"
        }

    override fun save(report: CrashReport) {
        // Swallows its own failure: a reporter that throws inside a crash
        // handler replaces the crash being reported with its own.
        runCatching {
            (Json.encodeToString(CrashReport.serializer(), report) as NSString)
                .writeToFile(path, true, NSUTF8StringEncoding, null)
        }
    }

    override fun pending(): CrashReport? = runCatching {
        val contents = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
            ?: return null
        Json.decodeFromString(CrashReport.serializer(), contents)
    }.getOrNull()

    override fun clear() {
        runCatching { NSFileManager.defaultManager.removeItemAtPath(path, null) }
    }
}

/**
 * Kotlin/Native calls this hook for an exception that reaches the top of a
 * Kotlin call stack, which is what a Kotlin bug looks like — the `\p{L}` regex
 * that killed this app at startup would have been caught here.
 *
 * It does not catch a Swift or Objective-C exception, and it does not catch a
 * signal: an index out of bounds in Swift, or a memory fault, ends the process
 * without Kotlin being told. Those need a signal handler, which is the line
 * between this and a real crash service.
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalUuidApi::class)
actual fun installCrashHandler(store: CrashStore, describe: () -> DeviceDescription) {
    setUnhandledExceptionHook { throwable ->
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
        // The hook replaces termination, so the process has to be ended here or
        // the app carries on in whatever state the failure left it.
        throw throwable
    }
}
