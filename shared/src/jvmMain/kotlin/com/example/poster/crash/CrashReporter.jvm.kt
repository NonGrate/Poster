package com.example.poster.crash

/**
 * The server shares this module and so has to provide one, but it does not
 * report crashes to itself: it has logs, and a process that dies gets
 * restarted by the platform running it.
 */
actual fun installCrashHandler(store: CrashStore, describe: () -> DeviceDescription) = Unit
