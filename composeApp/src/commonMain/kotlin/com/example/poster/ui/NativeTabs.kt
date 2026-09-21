package com.example.poster.ui

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
