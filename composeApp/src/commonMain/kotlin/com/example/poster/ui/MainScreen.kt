package com.example.poster.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import com.example.poster.ui.components.SideNavigation
import poster.composeapp.generated.resources.two_pane_empty
import com.example.poster.ui.components.ContentMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import com.example.poster.ui.screens.NotificationsScreen
import com.example.poster.notification.rememberNotificationPermissionRequest
import com.example.poster.notification.PushRegistrar
import androidx.compose.ui.Alignment
import androidx.compose.runtime.CompositionLocalProvider
import com.example.poster.ui.liquid.rememberGlassBackdrop
import com.example.poster.ui.liquid.glassBackdropSource
import com.example.poster.ui.liquid.LocalGlassBackdrop
import com.example.poster.ui.liquid.LocalBottomBarInset
import com.example.poster.ui.liquid.LiquidNavBarInset
import com.example.poster.ui.liquid.LiquidNavBar
import com.example.poster.config.Features
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.example.poster.ui.platform.AdaptiveBackHandler
import com.example.poster.ui.platform.adaptiveEdgeSwipeBack
import com.example.poster.ui.platform.SyncSystemAppearance
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.poster.navigation.Screen
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import com.example.poster.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.nav_favorites
import poster.composeapp.generated.resources.nav_home
import poster.composeapp.generated.resources.nav_my_posts
import poster.composeapp.generated.resources.nav_settings
import com.example.poster.ui.screens.GroupsScreen
import com.example.poster.ui.screens.DetailsScreen
import com.example.poster.ui.screens.FavoritesScreen
import com.example.poster.ui.screens.LoginScreen
import com.example.poster.ui.screens.HomeScreen
import com.example.poster.ui.screens.MyPostsScreen
import com.example.poster.ui.screens.FeedbackScreen
import com.example.poster.ui.screens.ProfileScreen
import com.example.poster.ui.screens.SettingsScreen
import com.example.poster.ui.components.AppLinkHandler
import com.example.poster.ui.components.VerifyEmailDialog
import com.example.poster.ui.platform.AdaptiveNavBar
import com.example.poster.ui.platform.NavDestination
import com.example.poster.util.AppPreferences
import com.example.poster.viewmodel.AccountViewModel
import com.example.poster.viewmodel.GroupViewModel
import com.example.poster.model.Post
import com.example.poster.ui.components.ReadableWidth
import com.example.poster.viewmodel.PostsViewModel
import com.example.poster.viewmodel.ThemeViewModel
import com.example.poster.network.GroupApi
import kotlinx.coroutines.launch
import com.example.poster.preview.rememberPreviewGraph

/**
 * What is pushed over the tabs. One at a time, so the four booleans that could
 * all be true at once are one thing that cannot. The open post is not here:
 * the two-pane layout shows it *beside* the tab content rather than over it.
 */
private enum class Overlay { Profile, Groups, Feedback, Notifications }

@Composable
fun MainScreen(
    postsViewModel: PostsViewModel = koinInject(),
    userViewModel: AccountViewModel = koinInject(),
    themeViewModel: ThemeViewModel = koinInject(),
    /**
     * Set when the platform owns the tab bar (see [NativeTabs]): this instance
     * shows one tab only, draws no bar, and asks the host to switch tabs. The
     * Home instance is the one that handles links, invites and pushes, so four
     * hosted instances do not each pop the same dialog.
     */
    fixedTab: String? = null,
) {
    val primary = fixedTab == null || fixedTab == Screen.Main.route
    val isLoggedIn by userViewModel.isLoggedInState.collectAsState()
    val currentUser by userViewModel.userState.collectAsState()
    val groupApi: GroupApi = koinInject()
    val postApi: com.example.poster.network.PostApi = koinInject()
    val groupViewModel: GroupViewModel = koinInject()

    val appPreferences: AppPreferences = koinInject()
    // The side rail's width on wide screens, remembered across launches.
    var railExpanded by remember { mutableStateOf(false) }
    val navScope = rememberCoroutineScope()
    LaunchedEffect(Unit) { railExpanded = appPreferences.navRailExpanded() }
    val myGroups by groupViewModel.groups.collectAsState()

    // Follow the device unless the person turned that off and picked a mode.
    val darkTheme = if (themeViewModel.followSystemTheme) {
        isSystemInDarkTheme()
    } else {
        themeViewModel.darkThemeEnabled
    }
    // Keep the system chrome (iOS status bar) in step with the app's own theme.
    SyncSystemAppearance(darkTheme)
    AppTheme(darkTheme = darkTheme) {
        var selectedTab by remember { mutableStateOf(fixedTab ?: Screen.Main.route) }
        // Above the tabs: an id opens the post, an [Overlay] opens a screen.
        var detailsPostId by remember { mutableStateOf<String?>(null) }
        var overlay by remember { mutableStateOf<Overlay?>(null) }
        // Registers this device for pushes after sign-in and withdraws it on sign-out.
        val pushRegistrar: PushRegistrar = koinInject()
        LaunchedEffect(Unit) { if (primary) pushRegistrar.start() }
        // Android 13+ needs permission to *show* pushes; ask once, after the first sign-in.
        val askNotifications = rememberNotificationPermissionRequest()
        LaunchedEffect(isLoggedIn) {
            if (primary && Features.PUSH_NOTIFICATIONS && isLoggedIn && !appPreferences.pushPrompted()) {
                appPreferences.setPushPrompted()
                askNotifications { }
            }
        }
        // The post form (add / edit), hosted here rather than in each screen so
        // it renders above the Scaffold and covers the nav bar.
        var postForm by remember { mutableStateOf<PostFormRequest?>(null) }
        val contentStateHolder = rememberSaveableStateHolder()

        // Back used to fall straight through to the system and close the app,
        // even with a post open or a non-Home tab selected. Now it unwinds what
        // is on screen — the open overlay first, then back to Home — and only
        // leaves the app when there is nothing left to go back to.
        AdaptiveBackHandler(
            enabled = postForm != null || detailsPostId != null || overlay != null ||
                selectedTab != Screen.Main.route
        ) {
            when {
                postForm != null -> postForm = null
                detailsPostId != null -> detailsPostId = null
                overlay != null -> overlay = null
                else -> if (fixedTab == null) selectedTab = Screen.Main.route
            }
        }

        // A link tapped from an email: confirming an address, or setting a new
        // password. Above the tabs, because it is what the person came to do.
        if (primary) AppLinkHandler(userViewModel)

        // A shared post link (poster://post/TOKEN). Resolve the token to
        // the public post, cache it so Details can show it, and open it.
        // Guarded on being signed in — the app is behind login, so the link
        // waits here until then rather than being consumed and lost.
        val pendingAppLink by com.example.poster.invite.PendingAppLink.link.collectAsState()
        LaunchedEffect(pendingAppLink, isLoggedIn) {
            val p = pendingAppLink
            if (Features.SHARING && p is com.example.poster.invite.AppLink.Post && isLoggedIn) {
                val post = runCatching { postApi.postByShareToken(p.token) }.getOrNull()
                if (post != null) {
                    postsViewModel.cacheSharedPost(post)
                    detailsPostId = post.guid
                }
                com.example.poster.invite.PendingAppLink.consume()
            }
        }

        // Shown when the server declines a write for want of a confirmed
        // address — at the moment it happens, which is when it means something.
        val needsVerifiedEmail by postsViewModel.needsVerifiedEmail.collectAsState()
        if (needsVerifiedEmail) {
            VerifyEmailDialog(
                onDismiss = { postsViewModel.acknowledgeVerificationNeeded() },
                accountViewModel = userViewModel,
            )
        }

        MainInvites(
            primary = primary,
            currentUser = currentUser,
            groupApi = groupApi,
            groupViewModel = groupViewModel,
            postsViewModel = postsViewModel,
            appPreferences = appPreferences,
        )

        if (!isLoggedIn) {
            LoginScreen(
                accountViewModel = userViewModel,
                onLoginSuccess = { selectedTab = Screen.Main.route }
            )
        } else {
            // Boxed so the post form can render above the Scaffold — over the
            // nav bar included, which a Dialog would not cover on iOS.
            // Picking a tab leaves whatever was pushed on top of it; without
            // this, Details/Profile/Groups stayed up over every tab.
            val selectTab: (String) -> Unit = { route ->
                detailsPostId = null
                overlay = null
                // With a host-owned bar this instance cannot show another tab; ask the host.
                if (fixedTab != null) NativeTabs.switcher?.invoke(route) else selectedTab = route
            }
            // The floating bar shows the content through itself, so the content
            // is recorded as it draws (see LiquidGlass.kt). Null with the docked bar.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // From Material's medium window (600 dp: tablets in portrait, a
                // half-screen desktop window) the tabs move to a rail on the left and
                // neither bottom bar is drawn; from the expanded window (840 dp) the
                // content splits in two as well. The host that owns the tabs
                // (fixedTab) keeps its own bar.
                val wideNav = maxWidth >= RailMinWidth && fixedTab == null
                val floatingBar = Features.LIQUID_NAV_BAR && fixedTab == null && !wideNav
                val backdrop = if (floatingBar) rememberGlassBackdrop() else null
                CompositionLocalProvider(
                    LocalGlassBackdrop provides backdrop,
                    LocalBottomBarInset provides when {
                        fixedTab != null -> NativeTabBarInset
                        floatingBar -> LiquidNavBarInset
                        else -> 0.dp
                    },
                ) {
                Row(modifier = Modifier.fillMaxSize()) {
                if (wideNav) {
                    SideNavigation(
                        destinations = mainDestinations(),
                        selectedRoute = selectedTab,
                        onSelect = selectTab,
                        expanded = railExpanded,
                        onToggleExpanded = {
                            railExpanded = !railExpanded
                            navScope.launch { appPreferences.setNavRailExpanded(railExpanded) }
                        },
                    )
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    bottomBar = {
                        if (!wideNav && !Features.LIQUID_NAV_BAR && fixedTab == null) BottomNavigationBar(selectedTab, selectTab)
                    },
                    // Under a native bar, don't reserve the bottom inset — the
                    // content scrolls behind the glass bar, clearing it via
                    // LocalBottomBarInset instead. Keep the top (status bar).
                    contentWindowInsets = if (fixedTab != null) {
                        ScaffoldDefaults.contentWindowInsets
                            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    } else {
                        ScaffoldDefaults.contentWindowInsets
                    },
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .padding(paddingValues)
                            .then(if (backdrop != null) Modifier.glassBackdropSource(backdrop) else Modifier)
                            // Left-edge swipe closes the open overlay — the iOS pop
                            // gesture, the page following the finger. No-op on
                            // Android. Only when an overlay is up; a swipe should
                            // not jump between tabs.
                            .adaptiveEdgeSwipeBack(
                                enabled = detailsPostId != null || overlay != null,
                                onBack = {
                                    when {
                                        detailsPostId != null -> detailsPostId = null
                                        else -> overlay = null
                                    }
                                },
                            )
                    ) {
                      // Centre and cap the content on a wide screen (tablet); a
                      // no-op on a phone. The Scaffold background still fills the
                      // gutters, and the bottom bar stays full-width below.
                      // Wide (tablet landscape, desktop): the list on the left, the
                      // post on the right, so reading does not leave the feed. On a
                      // phone the post replaces the list as before. Profile, groups,
                      // feedback and notifications keep the whole width on both.
                      BoxWithConstraints {
                        val twoPane = maxWidth >= TwoPaneMinWidth
                        ReadableWidth(maxWidth = if (twoPane) TwoPaneMaxWidth else ContentMaxWidth) {
                        val openDetails = detailsPostId
                        val tabContent: @Composable () -> Unit = {
                            contentStateHolder.SaveableStateProvider(selectedTab) {
                                when (selectedTab) {
                                    Screen.Main.route -> HomeScreen(
                                        onPostClick = { detailsPostId = it.guid },
                                        onNotifications = { overlay = Overlay.Notifications },
                                        onJoinGroup = { overlay = Overlay.Groups },
                                        onEditPost = { postForm = PostFormRequest.Edit(it) },
                                    )
                                    // Every list of posts opens the same details
                                    // screen. A card that is tappable on one screen and
                                    // inert on the next teaches people it is not
                                    // tappable at all.
                                    Screen.MyPosts.route -> MyPostsScreen(
                                        onPostClick = { detailsPostId = it.guid },
                                        onAddPost = { postForm = PostFormRequest.Add },
                                        onEditPost = { postForm = PostFormRequest.Edit(it) },
                                    )
                                    Screen.Favorites.route -> if (Features.LIKES) FavoritesScreen(
                                        onPostClick = { detailsPostId = it.guid },
                                        onBrowseFeed = { selectTab(Screen.Main.route) },
                                    )
                                    Screen.Settings.route -> SettingsScreen(
                                        onEditProfile = { overlay = Overlay.Profile },
                                        onManageGroups = { overlay = Overlay.Groups },
                                        onFeedback = { overlay = Overlay.Feedback },
                                        onNotifications = { overlay = Overlay.Notifications },
                                    )
                                }
                            }
                        }
                        val details: (@Composable () -> Unit)? = openDetails?.let { id ->
                            {
                                DetailsScreen(
                                    postId = id,
                                    onBack = { detailsPostId = null },
                                    onEditPost = { postForm = PostFormRequest.Edit(it) },
                                )
                            }
                        }
                        when {
                            overlay == Overlay.Profile -> ProfileScreen(onBack = { overlay = null })
                            Features.GROUPS && overlay == Overlay.Groups ->
                                GroupsScreen(onBack = { overlay = null })
                            Features.FEEDBACK && overlay == Overlay.Feedback ->
                                FeedbackScreen(onBack = { overlay = null })
                            Features.PUSH_NOTIFICATIONS && overlay == Overlay.Notifications -> NotificationsScreen(
                                onBack = { overlay = null },
                                onOpenPost = { overlay = null; detailsPostId = it },
                            )
                            twoPane -> Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(0.45f).fillMaxHeight()) { tabContent() }
                                VerticalDivider()
                                Box(modifier = Modifier.weight(0.55f).fillMaxHeight().testTag("detail_pane")) {
                                    details?.invoke() ?: EmptyPane()
                                }
                            }
                            details != null -> details()
                            else -> tabContent()
                        }
                        }
                      }
                    }
                }
                }  // Row
                }
                if (floatingBar) {
                    LiquidNavBar(
                        destinations = mainDestinations(),
                        selectedRoute = selectedTab,
                        onSelect = selectTab,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                MainPostForm(
                    request = postForm,
                    isLoggedIn = isLoggedIn,
                    currentUser = currentUser,
                    groups = myGroups,
                    postsViewModel = postsViewModel,
                    appPreferences = appPreferences,
                    onDismiss = { postForm = null },
                )
            }
        }
    }
}

/** The tabs, in order; both bars draw the same list. */
@Composable
private fun mainDestinations(): List<NavDestination> = buildList {
            add(NavDestination(Screen.Main.route, stringResource(Res.string.nav_home), Icons.Outlined.Home, Icons.Filled.Home, "feed_tab"))
            add(NavDestination(Screen.MyPosts.route, stringResource(Res.string.nav_my_posts), Icons.AutoMirrored.Outlined.Article, Icons.AutoMirrored.Filled.Article, "my_posts_tab"))
            // No likes, no list of liked posts: the tab goes with the feature.
            if (Features.LIKES) {
                add(NavDestination(Screen.Favorites.route, stringResource(Res.string.nav_favorites), Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite, "favourites_tab"))
            }
            add(NavDestination(Screen.Settings.route, stringResource(Res.string.nav_settings), Icons.Outlined.Settings, Icons.Filled.Settings, "settings_tab"))
        }

@Composable
private fun BottomNavigationBar(
    currentRoute: String,
    onTabSelected: (String) -> Unit
) {
    AdaptiveNavBar(
        destinations = mainDestinations(),
        selectedRoute = currentRoute,
        onSelect = onTabSelected,
    )
}

@Preview
@Composable
fun MainScreenPreview() {
    val graph = rememberPreviewGraph()
    MainScreen(
        postsViewModel = graph.postsViewModel,
        userViewModel = graph.accountViewModel,
        themeViewModel = graph.themeViewModel,
    )
}

@Preview
@Composable
fun MainScreenDarkPreview() {
    val graph = rememberPreviewGraph()
    AppTheme(darkTheme = true) {
        MainScreen(
            postsViewModel = graph.postsViewModel,
            userViewModel = graph.accountViewModel,
            themeViewModel = graph.themeViewModel,
        )
    }
}

/** From here up the tabs sit in a rail on the left instead of a bottom bar. Material's "medium" width. */
private val RailMinWidth = 600.dp
/** From here up the post opens beside the list rather than over it. Material's "expanded" width. */
private val TwoPaneMinWidth = 840.dp
/** Two readable columns, not one stretched one. */
private val TwoPaneMaxWidth = 1280.dp

/** The right pane before anything is picked. */
@Composable
private fun EmptyPane() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(Res.string.two_pane_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
