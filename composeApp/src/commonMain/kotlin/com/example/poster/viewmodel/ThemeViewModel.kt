package com.example.poster.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.poster.util.AppPreferences
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** The one theme setting the UI offers: follow the device, or force light/dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

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

    fun setDarkTheme(enabled: Boolean) {
        appPreferences.setDarkTheme(enabled)
    }

    fun setFollowSystem(enabled: Boolean) {
        appPreferences.setFollowSystemTheme(enabled)
    }

    /**
     * The two stored flags read as one setting, so the picker has a single
     * source of truth. Reading [followSystemTheme]/[darkThemeEnabled] here keeps
     * it observable: the selected item recomposes when either changes.
     */
    val themeMode: ThemeMode
        get() = when {
            followSystemTheme -> ThemeMode.SYSTEM
            darkThemeEnabled -> ThemeMode.DARK
            else -> ThemeMode.LIGHT
        }

    /** Sets both flags at once, so picking a mode cannot leave them disagreeing. */
    fun setThemeMode(mode: ThemeMode) {
        when (mode) {
            ThemeMode.SYSTEM -> setFollowSystem(true)
            ThemeMode.LIGHT -> { setFollowSystem(false); setDarkTheme(false) }
            ThemeMode.DARK -> { setFollowSystem(false); setDarkTheme(true) }
        }
    }
}
