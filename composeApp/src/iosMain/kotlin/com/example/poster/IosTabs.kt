package com.example.poster

import androidx.compose.ui.window.ComposeUIViewController
import com.example.poster.auth.AppleSignInLauncher
import com.example.poster.auth.GoogleSignInLauncher
import com.example.poster.config.Features
import com.example.poster.navigation.Screen
import com.example.poster.repository.SessionRepository
import com.example.poster.ui.NativeTabs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.nav_favorites
import poster.composeapp.generated.resources.nav_home
import poster.composeapp.generated.resources.nav_my_posts
import poster.composeapp.generated.resources.nav_settings

/**
 * The native tab bar (feature.liquidNavBar on iOS): SwiftUI owns a `TabView`
 * — the system's Liquid Glass bar on iOS 26 — and each tab hosts one Compose
 * view controller pinned to a route. Everything Swift needs is here.
 */

/** Whether the Swift shell should build the TabView rather than one full-screen Compose view. */
fun nativeTabs(): Boolean = Features.LIQUID_NAV_BAR

/** The tab routes in order; the same list `MainScreen` draws in its own bars. */
fun tabRoutes(): List<String> = buildList {
    add(Screen.Main.route)
    add(Screen.MyPosts.route)
    if (Features.LIKES) add(Screen.Favorites.route)
    add(Screen.Settings.route)
}

/** The tab's title in the app's language. Read once per tab at launch, so blocking is fine. */
fun tabTitle(route: String): String = runBlocking {
    when (route) {
        Screen.MyPosts.route -> getString(Res.string.nav_my_posts)
        Screen.Favorites.route -> getString(Res.string.nav_favorites)
        Screen.Settings.route -> getString(Res.string.nav_settings)
        else -> getString(Res.string.nav_home)
    }
}

/** The test tag the Compose bars give the same tab, so one UI test drives both bars. */
fun tabTestTag(route: String): String = when (route) {
    Screen.MyPosts.route -> "my_posts_tab"
    Screen.Favorites.route -> "favourites_tab"
    Screen.Settings.route -> "settings_tab"
    else -> "feed_tab"
}

/** SF Symbol names, matching the Material glyphs the Compose bars use. */
fun tabSymbol(route: String): String = when (route) {
    Screen.MyPosts.route -> "doc.text"
    Screen.Favorites.route -> "heart"
    Screen.Settings.route -> "gearshape"
    else -> "house"
}

/** Swift installs how to change the selected tab; Compose calls it for "browse the feed" and the like. */
fun setNativeTabSwitcher(block: (String) -> Unit) {
    NativeTabs.switcher = block
}

/** Tells Swift whether somebody is signed in, so it shows the tabs or the single login screen. */
fun observeSignedIn(block: (Boolean) -> Unit) {
    val session = KoinPlatform.getKoin().get<SessionRepository>()
    CoroutineScope(Dispatchers.Main).launch { session.isLoggedIn.collect { block(it) } }
}

/** One tab's content: `MainScreen` pinned to [route], no bar of its own. Koin must already be started. */
fun TabViewController(route: String): UIViewController = ComposeUIViewController { App(fixedTab = route) }
