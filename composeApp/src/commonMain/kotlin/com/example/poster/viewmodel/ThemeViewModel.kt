package com.example.poster.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.poster.util.AppPreferences
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for managing UI theme state.
 */
class ThemeViewModel(
    private val appPreferences: AppPreferences,
    dispatchers: DispatcherProvider
) : ScopedViewModel(dispatchers) {

    var darkThemeEnabled by mutableStateOf(appPreferences.isDarkTheme())
        private set

    /**
     * When true (default) the app follows the device's light/dark setting; the
     * manual [darkThemeEnabled] choice applies only when this is off. The screen
     * resolves the two — see MainScreen — because "is the system dark" is a
     * composable question, not one this ViewModel can answer.
     */
    var followSystemTheme by mutableStateOf(appPreferences.isFollowSystemTheme())
        private set

    init {
        scope.launch {
            appPreferences.darkTheme.collectLatest {
                darkThemeEnabled = it
            }
        }
        scope.launch {
            appPreferences.followSystemTheme.collectLatest {
                followSystemTheme = it
            }
        }
    }

    fun toggleDarkTheme() {
        setDarkTheme(!darkThemeEnabled)
    }

    fun setDarkTheme(enabled: Boolean) {
        appPreferences.setDarkTheme(enabled)
    }

    fun setFollowSystem(enabled: Boolean) {
        appPreferences.setFollowSystemTheme(enabled)
    }
}
