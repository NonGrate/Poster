package com.example.poster.util

import androidx.compose.runtime.Composable
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** From the run configuration (`-Dposter.version=1.2.3`), or a plain word. */
actual fun appVersionLabel(): String = System.getProperty("poster.version") ?: "desktop"

/** No share sheet on the desktop: the text goes to the clipboard. */
@Composable
actual fun rememberShareText(): (String) -> Unit = { text ->
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}
