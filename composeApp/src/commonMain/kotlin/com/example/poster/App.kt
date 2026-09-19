package com.example.poster

import androidx.compose.runtime.*
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.example.poster.ui.MainScreen
import com.example.poster.viewmodel.AccountViewModel
import com.example.poster.viewmodel.FavoritesViewModel
import com.example.poster.viewmodel.PostsViewModel
import com.example.poster.viewmodel.ThemeViewModel

// Helper class to access Koin dependencies
class AppDependencies : KoinComponent {
    val postsViewModel: PostsViewModel by inject()
    val accountViewModel: AccountViewModel by inject()
    val favoritesViewModel: FavoritesViewModel by inject()
    val themeViewModel: ThemeViewModel by inject()
}

@Composable
@Preview
fun App(fixedTab: String? = null) {
    // Get dependencies from Koin
    val dependencies = remember { AppDependencies() }

    // Set up the main UI
    MainScreen(
        postsViewModel = dependencies.postsViewModel,
        userViewModel = dependencies.accountViewModel,
        favoritesViewModel = dependencies.favoritesViewModel,
        themeViewModel = dependencies.themeViewModel,
        fixedTab = fixedTab,
    )
}
