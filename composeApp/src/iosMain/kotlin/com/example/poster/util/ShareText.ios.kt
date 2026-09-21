package com.example.poster.util

import androidx.compose.runtime.Composable
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

@Composable
actual fun rememberShareText(): (String) -> Unit = { text ->
    // keyWindow on UIApplication is deprecated and nil under scenes, so the
    // window is found through the connected scene instead.
    val root = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        .firstOrNull { window -> window.isKeyWindow() }
        ?.rootViewController
    // ponytail: no popover source, which iPad would need; the app is iPhone-only today.
    root?.presentViewController(
        UIActivityViewController(listOf(text), null),
        animated = true,
        completion = null,
    )
}
