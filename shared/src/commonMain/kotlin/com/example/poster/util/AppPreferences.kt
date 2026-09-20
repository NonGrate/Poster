package com.example.poster.util

import com.example.poster.model.PostDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.poster.model.PostVisibility
import com.example.poster.model.User
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch

class AppPreferences(private val dataStore: PlatformDataStore) {
    companion object {

        val SESSION_KEYS = listOf(
            "user_id",
            "cached_user",
            "known_group_ids",
            "default_post_visibility",
            "dark_theme",
            "recent_tags",
            "daily_reminder_enabled",
            "daily_reminder_minutes",
            "post_draft",
        )

        /**
         * When the reminder goes off if nobody has said otherwise.
         *
         * Minutes since midnight. Evening, because a post list is something
         * people come back to at the end of a day rather than the start of one.
         */
        const val DEFAULT_REMINDER_MINUTES = 21 * 60
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val KEY_DARK_THEME = "dark_theme"
    private val KEY_FOLLOW_SYSTEM_THEME = "follow_system_theme"
    private val KEY_USER_ID = "user_id"
    private val KEY_DEFAULT_VISIBILITY = "default_post_visibility"
    private val KEY_KNOWN_GROUPS = "known_group_ids"
    private val KEY_CACHED_USER = "cached_user"
    private val KEY_REMINDER_ENABLED = "daily_reminder_enabled"
    private val KEY_REMINDER_MINUTES = "daily_reminder_minutes"
    private val KEY_DEVICE_ID = "device_id"
    private val KEY_PUSH_TOKEN = "push_token"
    private val KEY_PUSH_PROMPTED = "push_prompted"
    private val KEY_POST_DRAFT = "post_draft"

    /** The push token the server was last told about, so signing out can withdraw it. */
    suspend fun pushToken(): String? = dataStore.getString(KEY_PUSH_TOKEN, null)
    suspend fun setPushToken(token: String?) = dataStore.putString(KEY_PUSH_TOKEN, token)

    /** Whether the one-time notification permission prompt has been shown on this install. */
    suspend fun pushPrompted(): Boolean = dataStore.getString(KEY_PUSH_PROMPTED, null) != null
    suspend fun setPushPrompted() = dataStore.putString(KEY_PUSH_PROMPTED, "1")

    /**
     * A stable, anonymous id for this install, minted once and kept.
     *
     * A random UUID, not any hardware identifier — those are off-limits and not
     * ours to read. It groups this install's diagnostic events, including the
     * anonymous ones sent before anyone signs in. Deliberately NOT cleared on
     * sign-out (see [clear]): the point is that it survives, so one person's
     * events on one device stay grouped across sessions.
     */
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    suspend fun deviceId(): String {
        dataStore.getString(KEY_DEVICE_ID, null)?.let { return it }
        val fresh = kotlin.uuid.Uuid.random().toString()
        dataStore.putString(KEY_DEVICE_ID, fresh)
        return fresh
    }

    private val _darkTheme = MutableStateFlow(false)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    // True (default) = follow the device's light/dark setting; false = use the
    // manual [darkTheme] choice. The dark/light selector only matters when this
    // is off.
    private val _followSystemTheme = MutableStateFlow(true)
    val followSystemTheme: StateFlow<Boolean> = _followSystemTheme.asStateFlow()

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()


    /**
     * The daily reminder, off until somebody asks for it.
     *
     * Off rather than on: a notification nobody asked for is the fastest way to
     * have notifications turned off wholesale, and this is the only one the app
     * sends.
     */
    private val _reminderEnabled = MutableStateFlow(false)
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()

    /** When it goes off, as minutes since midnight. */
    private val _reminderMinutes = MutableStateFlow(DEFAULT_REMINDER_MINUTES)
    val reminderMinutes: StateFlow<Int> = _reminderMinutes.asStateFlow()

    // Public by default: most posts are meant to be seen, and the setting is
    // there for people who would rather start from the other end.
    private val _defaultVisibility = MutableStateFlow(PostVisibility.PUBLIC)
    val defaultVisibility: StateFlow<String> = _defaultVisibility.asStateFlow()

    // The first read is asynchronous, so a write can happen while it is in
    // flight. Without this, a stored id read late could overwrite the account
    // someone had just signed into.
    private var darkThemeWritten = false
    private var followSystemThemeWritten = false
    private var userIdWritten = false
    private var defaultVisibilityWritten = false
    private var reminderWritten = false

    /** The unsent post, if any (feature.drafts). Goes with the session. */
    suspend fun postDraft(): PostDraft? =
        dataStore.getString(KEY_POST_DRAFT, null)
            ?.let { runCatching { Json.decodeFromString(PostDraft.serializer(), it) }.getOrNull() }

    suspend fun setPostDraft(draft: PostDraft?) {
        dataStore.putString(KEY_POST_DRAFT, draft?.let { Json.encodeToString(PostDraft.serializer(), it) })
    }

    suspend fun cachedUser(): User? =
        dataStore.getString(KEY_CACHED_USER, null)
            ?.let { runCatching { Json.decodeFromString(User.serializer(), it) }.getOrNull() }

    suspend fun setCachedUser(user: User?) {
        dataStore.putString(KEY_CACHED_USER, user?.let { Json.encodeToString(User.serializer(), it) })
    }

    /**
     * The stored reminder, read straight from disk rather than from the flow.
     *
     * For callers that exist before the flows are warm — the boot receiver
     * constructs this class and asks immediately, and [init] fills the flows
     * asynchronously, so reading them there would say "off" every time.
     */
    suspend fun storedReminder(): Pair<Boolean, Int> {
        val enabled = dataStore.getBoolean(KEY_REMINDER_ENABLED, false)
        val minutes = dataStore.getString(KEY_REMINDER_MINUTES, null)
            ?.toIntOrNull()?.takeIf { it in 0..1439 }
            ?: DEFAULT_REMINDER_MINUTES
        return enabled to minutes
    }

    suspend fun knownGroupIds(): Set<String>? =
        dataStore.getString(KEY_KNOWN_GROUPS, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.toSet()

    suspend fun setKnownGroupIds(ids: Set<String>) {
        dataStore.putString(KEY_KNOWN_GROUPS, ids.joinToString(","))
    }

    init {
        scope.launch {
            val storedTheme = dataStore.getBoolean(KEY_DARK_THEME, false)
            val storedFollowSystem = dataStore.getBoolean(KEY_FOLLOW_SYSTEM_THEME, true)
            val storedUserId = dataStore.getString(KEY_USER_ID, null)
            val storedVisibility = dataStore.getString(KEY_DEFAULT_VISIBILITY, PostVisibility.PUBLIC)
            if (!darkThemeWritten) _darkTheme.value = storedTheme
            if (!followSystemThemeWritten) _followSystemTheme.value = storedFollowSystem
            if (!userIdWritten) _userId.value = storedUserId
            if (!defaultVisibilityWritten) {
                _defaultVisibility.value = storedVisibility ?: PostVisibility.PUBLIC
            }
            val storedReminder = dataStore.getBoolean(KEY_REMINDER_ENABLED, false)
            val storedMinutes = dataStore.getString(KEY_REMINDER_MINUTES, null)
            if (!reminderWritten) {
                _reminderEnabled.value = storedReminder
                _reminderMinutes.value =
                    storedMinutes?.toIntOrNull()?.takeIf { it in 0..1439 } ?: DEFAULT_REMINDER_MINUTES
            }
        }
    }


    fun isDarkTheme(): Boolean = _darkTheme.value
    fun setDarkTheme(enabled: Boolean) {
        darkThemeWritten = true
        _darkTheme.value = enabled
        scope.launch { dataStore.putBoolean(KEY_DARK_THEME, enabled) }
    }

    fun isFollowSystemTheme(): Boolean = _followSystemTheme.value
    fun setFollowSystemTheme(enabled: Boolean) {
        followSystemThemeWritten = true
        _followSystemTheme.value = enabled
        scope.launch { dataStore.putBoolean(KEY_FOLLOW_SYSTEM_THEME, enabled) }
    }

    fun getUserId(): String? = _userId.value
    fun setUserId(id: String?) {
        userIdWritten = true
        _userId.value = id
        scope.launch { dataStore.putString(KEY_USER_ID, id) }
    }

    /**
     * Records the reminder. Writing both at once because they are one setting:
     * a time with the reminder off, or a reminder with no time, is half a
     * state that something downstream has to interpret.
     */
    fun setReminder(enabled: Boolean, minutesSinceMidnight: Int) {
        reminderWritten = true
        val minutes = minutesSinceMidnight.coerceIn(0, 1439)
        _reminderEnabled.value = enabled
        _reminderMinutes.value = minutes
        scope.launch {
            dataStore.putBoolean(KEY_REMINDER_ENABLED, enabled)
            dataStore.putString(KEY_REMINDER_MINUTES, minutes.toString())
        }
    }

    fun setDefaultVisibility(visibility: String) {
        defaultVisibilityWritten = true
        _defaultVisibility.value = visibility
        scope.launch { dataStore.putString(KEY_DEFAULT_VISIBILITY, visibility) }
    }

    fun clear() {
        userIdWritten = true
        darkThemeWritten = true
        followSystemThemeWritten = true
        defaultVisibilityWritten = true
        // The reminder goes with the session. It is a standing instruction to
        // interrupt somebody's evening, and whoever signs in next did not give
        // it. Cancelling the alarm itself is the caller's job — see where
        // clear() is called.
        reminderWritten = true
        _userId.value = null
        _darkTheme.value = false
        _followSystemTheme.value = true
        _defaultVisibility.value = PostVisibility.PUBLIC
        _reminderEnabled.value = false
        _reminderMinutes.value = DEFAULT_REMINDER_MINUTES
        scope.launch {
            dataStore.putBoolean(KEY_REMINDER_ENABLED, false)
            dataStore.putString(KEY_REMINDER_MINUTES, null)
            dataStore.putString(KEY_USER_ID, null)
            dataStore.putBoolean(KEY_DARK_THEME, false)
            dataStore.putBoolean(KEY_FOLLOW_SYSTEM_THEME, true)
            dataStore.putString(KEY_DEFAULT_VISIBILITY, PostVisibility.PUBLIC)
            dataStore.putString(KEY_KNOWN_GROUPS, null)
            dataStore.putString(KEY_CACHED_USER, null)
            dataStore.putString(KEY_POST_DRAFT, null)
        }
    }
}
