package com.example.poster.crash

import com.example.poster.model.CrashReport

/**
 * Somewhere to put a crash, written by a process that is about to die.
 *
 * A crashing app cannot make a network request: the handler runs while the
 * process is being torn down, so anything asynchronous is a coin flip. The
 * report is written to a file, synchronously, and sent on the next launch —
 * which is why a crash that stops somebody opening the app again never
 * arrives, and why the first thing to check is still the device in their hand.
 *
 * One report is kept, not a queue. A crash loop would otherwise fill the disk
 * with the same stack trace, and the first one is the one worth having.
 */
interface CrashStore {
    fun save(report: CrashReport)
    fun pending(): CrashReport?
    fun clear()
}

/**
 * Installs the handler that catches what nothing else did.
 *
 * Platform-specific because the two runtimes disagree about what an uncaught
 * failure even is — see the actual implementations for what each one does and
 * does not catch.
 */
expect fun installCrashHandler(store: CrashStore, describe: () -> DeviceDescription)

/** The bits of "where did this happen" that are not about a person. */
data class DeviceDescription(
    val platform: String,
    val osVersion: String,
    val device: String,
    val appVersion: String,
)

/** Shared by both platforms: turn a throwable into the thing we store. */
fun crashReportOf(
    throwable: Throwable,
    device: DeviceDescription,
    guid: String,
    occurredAt: String,
): CrashReport = CrashReport(
    guid = guid,
    type = throwable::class.simpleName ?: "Throwable",
    message = throwable.message?.take(CrashReport.MAX_FIELD),
    stack = throwable.stackTraceToString().take(CrashReport.MAX_STACK),
    platform = device.platform,
    osVersion = device.osVersion,
    device = device.device,
    appVersion = device.appVersion,
    occurredAt = occurredAt,
)
