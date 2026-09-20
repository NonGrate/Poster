package com.example.poster.crash

/** The browser reports its own errors to the console; nothing to hook here. */
actual fun installCrashHandler(store: CrashStore, describe: () -> DeviceDescription) = Unit
