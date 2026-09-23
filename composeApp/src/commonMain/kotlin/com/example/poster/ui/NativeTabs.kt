package com.example.poster.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * When the platform owns the tab bar (iOS with feature.liquidNavBar: a SwiftUI
 * TabView, which is the system's Liquid Glass bar on iOS 26), each tab hosts its
 * own [MainScreen] pinned to one route, and changing tabs from inside Compose —
 * "Browse the feed", a notification opening a post — has to ask the host.
 * Android never sets [switcher]; the docked or floating bar is Compose's own.
 */
object NativeTabs {
    var switcher: ((route: String) -> Unit)? = null

    val active: Boolean get() = switcher != null
}

// The native iOS 26 glass bar floats over the content, which now scrolls behind
// it (the Swift host stops reserving space for it). Scroll screens add this at
// the bottom so the last item clears the bar: tab bar (~49pt) + home indicator
// (~34pt), rounded up for breathing room.
// ponytail: fixed height; on a home-button device (no indicator) it over-pads
// by ~34dp of harmless scroll slack. Read the real safe area if that matters.
val NativeTabBarInset: Dp = 88.dp
