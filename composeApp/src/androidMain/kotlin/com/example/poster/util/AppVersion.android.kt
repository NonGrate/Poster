package com.example.poster.util

import com.example.poster.BuildConfig

actual fun appVersionLabel(): String =
    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
