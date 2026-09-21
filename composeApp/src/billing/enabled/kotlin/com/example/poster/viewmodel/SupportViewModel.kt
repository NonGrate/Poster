package com.example.poster.viewmodel

import com.revenuecat.purchases.kmp.models.Offering
import com.revenuecat.purchases.kmp.models.Package
import com.example.poster.billing.SupportDemoTier
import com.example.poster.billing.SupportRepository
import com.example.poster.repository.SessionRepository
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * What the Settings screen knows about supporting the app.
 *
 * Deliberately quiet about failure. Somebody who cannot reach the store, or
 * whose device has payments turned off, should see an entry that does not work
 * rather than an error demanding attention: nothing they came to the app to do
 * has gone wrong. The one failure worth naming is a purchase that was attempted
 * and refused, which [error] carries.
 */
class SupportViewModel(
    private val support: SupportRepository,
    private val session: SessionRepository,
    dispatchers: DispatcherProvider,
    /** Screenshots-only: show the paywall with placeholder tiers, no billing. */
    private val demoPaywall: Boolean = false,
) : ScopedViewModel(dispatchers) {

    /**
     * Placeholder tiers for the demo paywall (screenshots before the store
     * account exists). Empty in every real build. Ids match [tierLabels] in the
     * paywall so the localized names are used; prices are illustrative.
     */
    val demoTiers: List<SupportDemoTier> = if (demoPaywall) {
        listOf(
            SupportDemoTier("coffee", "$2.99"),
            SupportDemoTier("coffee_and_snack", "$4.99"),
            SupportDemoTier("lunch", "$9.99"),
            SupportDemoTier("dinner", "$19.99"),
            SupportDemoTier("coffee_subscription", "$2.99 / mo"),
        )
    } else {
        emptyList()
    }

    /**
     * False in builds with no API key — the entry is not shown then. The demo
     * flag forces it on for screenshots, where the placeholder tiers stand in
     * for a store that is not connected yet.
     */
    val available: Boolean get() = support.enabled || demoPaywall

    private val _offering = MutableStateFlow<Offering?>(null)
    val offering: StateFlow<Offering?> = _offering.asStateFlow()

    /** True once RevenueCat says the supporter entitlement is active. */
    private val _isSupporter = MutableStateFlow(false)
    val isSupporter: StateFlow<Boolean> = _isSupporter.asStateFlow()

    /** True when there is a subscription to manage, which a coffee is not. */
    private val _hasSubscription = MutableStateFlow(false)
    val hasSubscription: StateFlow<Boolean> = _hasSubscription.asStateFlow()

    /** Store ids of the subscriptions held now, so the paywall can mark them active. */
    private val _activeSubscriptions = MutableStateFlow<Set<String>>(emptySet())
    val activeSubscriptions: StateFlow<Set<String>> = _activeSubscriptions.asStateFlow()

    /** Next renewal per held subscription, epoch millis by store id, for the paywall. */
    private val _renewalDates = MutableStateFlow<Map<String, Long>>(emptyMap())
    val renewalDates: StateFlow<Map<String, Long>> = _renewalDates.asStateFlow()

    /** False once the subscription is cancelled: the date then reads "active until", not "renews". */
    private val _subscriptionWillRenew = MutableStateFlow(true)
    val subscriptionWillRenew: StateFlow<Boolean> = _subscriptionWillRenew.asStateFlow()

    private val _error = MutableStateFlow<UiError?>(null)
    val error: StateFlow<UiError?> = _error.asStateFlow()

    init {
        if (support.enabled) {
            scope.launch {
                // Purchases belong to the account, not the handset, so the SDK
                // is told who is signed in and told again when that changes.
                session.user.collectLatest { user ->
                    support.start(user?.guid)
                    if (user != null) support.identify(user.guid)
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        if (!support.enabled) return
        scope.launch {
            support.currentOffering().onSuccess { _offering.value = it }
            support.customerInfo().onSuccess {
                _isSupporter.value = support.isSupporter(it)
                _hasSubscription.value = support.hasSubscription(it)
                _activeSubscriptions.value = support.activeSubscriptions(it)
                _renewalDates.value = support.subscriptionRenewals(it)
                _subscriptionWillRenew.value = support.subscriptionWillRenew(it)
            }
        }
    }

    /**
     * Called when the paywall reports a completed purchase.
     *
     * The paywall does the buying; this only asks the store what changed, so
     * there is one place that decides whether somebody is a supporter and it is
     * the entitlement rather than anything this app inferred.
     */
    fun onPurchaseCompleted() = refresh()

    /** True while a purchase is in flight, so the tiers cannot be tapped twice. */
    private val _purchasing = MutableStateFlow(false)
    val purchasing: StateFlow<Boolean> = _purchasing.asStateFlow()

    /** True the moment a purchase lands, so the screen can say thank you once. */
    private val _justSupported = MutableStateFlow(false)
    val justSupported: StateFlow<Boolean> = _justSupported.asStateFlow()

    fun acknowledgeThanks() {
        _justSupported.value = false
    }

    /**
     * Buys a tier.
     *
     * A failure here is usually somebody changing their mind, which is not an
     * error worth a dialog. Only a genuine refusal is reported, and telling the
     * two apart is not something the store makes easy — so the message says
     * nothing was charged, which is true either way.
     */
    fun buy(pkg: Package) {
        if (_purchasing.value) return
        scope.launch {
            _purchasing.value = true
            try {
                support.purchase(pkg)
                    .onSuccess { info ->
                        _isSupporter.value = support.isSupporter(info)
                        _hasSubscription.value = support.hasSubscription(info)
                        _activeSubscriptions.value = support.activeSubscriptions(info)
                        _renewalDates.value = support.subscriptionRenewals(info)
                        _subscriptionWillRenew.value = support.subscriptionWillRenew(info)
                        // A coffee grants no entitlement, so thanks cannot be
                        // read off one. It is said because a purchase happened.
                        _justSupported.value = true
                    }
                    .onFailure {
                        // The store's own words, because "it didn't work" is
                        // not something anybody can act on — and this is the
                        // one place a purchase can fail for a dozen reasons.
                        _error.value = UiError(it.message ?: "Purchase did not complete", it)
                    }
            } finally {
                _purchasing.value = false
            }
        }
    }

    /** Purchases made on another device, or before a reinstall. */
    fun restore() {
        if (!support.enabled) return
        scope.launch {
            support.restore()
                .onSuccess {
                    _isSupporter.value = support.isSupporter(it)
                    _hasSubscription.value = support.hasSubscription(it)
                    _activeSubscriptions.value = support.activeSubscriptions(it)
                    _renewalDates.value = support.subscriptionRenewals(it)
                    _subscriptionWillRenew.value = support.subscriptionWillRenew(it)
                }
                .onFailure { _error.value = UiError("Unable to restore purchases", it) }
        }
    }

    fun acknowledgeError() {
        _error.value = null
    }
}
