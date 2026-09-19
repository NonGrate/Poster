package com.example.poster.util

import platform.Foundation.NSBundle

actual fun appVersionLabel(): String {
    val info = NSBundle.mainBundle.infoDictionary
    val name = info?.get("CFBundleShortVersionString") as? String ?: "?"
    val code = info?.get("CFBundleVersion") as? String ?: "?"
    return "$name ($code)"
}
