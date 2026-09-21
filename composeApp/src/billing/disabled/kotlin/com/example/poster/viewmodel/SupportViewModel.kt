package com.example.poster.viewmodel

import com.example.poster.billing.SupportDemoTier
import com.example.poster.billing.SupportRepository
import com.example.poster.repository.SessionRepository
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The stand-in for `feature.support=false`: the same surface the Settings
 * screen reads, permanently reporting "not available". See the enabled twin
 * in src/billing/enabled for the real thing.
 */
class SupportViewModel(
    @Suppress("UNUSED_PARAMETER") support: SupportRepository,
    @Suppress("UNUSED_PARAMETER") session: SessionRepository,
    dispatchers: DispatcherProvider,
    @Suppress("UNUSED_PARAMETER") demoPaywall: Boolean = false,
) : ScopedViewModel(dispatchers) {
    val demoTiers: List<SupportDemoTier> = emptyList()
    val available: Boolean get() = false
    val offering: StateFlow<Any?> = MutableStateFlow(null)
    val isSupporter: StateFlow<Boolean> = MutableStateFlow(false)
    val hasSubscription: StateFlow<Boolean> = MutableStateFlow(false)
    val activeSubscriptions: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    val renewalDates: StateFlow<Map<String, Long>> = MutableStateFlow(emptyMap())
    val subscriptionWillRenew: StateFlow<Boolean> = MutableStateFlow(true)
    val error: StateFlow<UiError?> = MutableStateFlow(null)
    val purchasing: StateFlow<Boolean> = MutableStateFlow(false)
    val justSupported: StateFlow<Boolean> = MutableStateFlow(false)

    fun refresh() {}
    fun onPurchaseCompleted() {}
    fun acknowledgeThanks() {}
    fun buy(@Suppress("UNUSED_PARAMETER") pkg: Any) {}
    fun restore() {}
    fun acknowledgeError() {}
}
