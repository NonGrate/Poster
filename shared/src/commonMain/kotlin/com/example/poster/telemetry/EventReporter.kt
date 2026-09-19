package com.example.poster.telemetry

import com.example.poster.config.Features
import com.example.poster.getPlatform
import com.example.poster.model.AppEvent
import com.example.poster.model.AppEventName
import com.example.poster.model.AppEventSeverity
import com.example.poster.network.EventApi
import com.example.poster.util.AppPreferences
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Reports diagnostic events, fire-and-forget.
 *
 * A call to [report] returns immediately; the send happens on a background
 * coroutine and a failure is dropped (the API already swallows it). This is
 * telemetry, not a transaction — the caller must never wait on it or care
 * whether it landed.
 *
 * The device id is read once and kept: the first report mints it (via
 * [AppPreferences.deviceId]) and every later one reuses it, so all of an
 * install's events carry the same id without touching disk each time.
 *
 * [name] is always one of [AppEventName]; nothing here takes a free string.
 */
class EventReporter(
    private val api: EventApi,
    private val preferences: AppPreferences,
    private val appVersion: String,
    dispatchers: DispatcherProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val platform = getPlatform().name

    // Not volatile: at worst two early reports both read the same persisted id
    // before this is set, which is harmless — the id on disk is the same either way.
    private var cachedDeviceId: String? = null

    /**
     * @param name one of [AppEventName].
     * @param detail small non-personal facts only (a duration, a count) — never
     *   anything a person typed.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun report(name: String, severity: String = AppEventSeverity.INFO, detail: String? = null) {
        if (!Features.TELEMETRY) return
        scope.launch {
            val deviceId = cachedDeviceId ?: preferences.deviceId().also { cachedDeviceId = it }
            api.send(
                AppEvent(
                    guid = Uuid.random().toString(),
                    deviceId = deviceId,
                    name = name,
                    severity = severity,
                    detail = detail?.take(AppEvent.MAX_FIELD),
                    platform = platform,
                    appVersion = appVersion,
                    occurredAt = Clock.System.now().toString(),
                ),
            )
        }
    }
}
