package com.example.poster.util

import androidx.compose.runtime.Composable
import kotlinx.browser.window

/** The bundle carries no version of its own; the page can pass one in `<meta name="poster-version">`. */
actual fun appVersionLabel(): String =
    window.document.querySelector("meta[name=poster-version]")?.getAttribute("content")?.takeIf { it.isNotBlank() } ?: "web"

/** No share sheet in the browser: the link goes to the clipboard. */
@Composable
actual fun rememberShareText(): (String) -> Unit = { text -> window.navigator.clipboard.writeText(text) }
