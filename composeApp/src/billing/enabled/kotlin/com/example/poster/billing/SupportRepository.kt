package com.example.poster.billing

import com.revenuecat.purchases.kmp.LogLevel
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.configure
import com.revenuecat.purchases.kmp.ktx.awaitCustomerInfo
import com.revenuecat.purchases.kmp.ktx.awaitOfferings
import com.revenuecat.purchases.kmp.ktx.awaitPurchase
import com.revenuecat.purchases.kmp.ktx.awaitRestore
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.Offering
import com.revenuecat.purchases.kmp.models.Package
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * Buying the person who made this a coffee.
 *
 * Everything about what is on offer — how many tiers, what they cost, what the
 * paywall says — comes from the offering RevenueCat serves, not from constants
 * here. Prices differ by country and change without an app release, so a list
 * of them in the source would be wrong somewhere on the day it was written.
 *
 * [enabled] is false when no API key was built in. That is the ordinary state
 * of the test flavor and of anybody's checkout before they have a key, and it
 * has to be a state the app can be in rather than a crash: the screen says
 * support is unavailable and the rest of the app is untouched.
 */
class SupportRepository(
    private val apiKey: String,
    private val dispatchers: DispatcherProvider,
) {
    val enabled: Boolean get() = apiKey.isNotBlank()

    private var configured = false

    /**
     * Starts the SDK, once, for this person.
     *
     * [appUserId] ties purchases to the account rather than to the device, so
     * somebody who supports on their phone is still a supporter on their
     * tablet. Null hands RevenueCat an anonymous id, which is right before
     * anybody has signed in.
     */
    fun start(appUserId: String?) {
        if (!enabled || configured) return
        configured = true
        // Loud on a Test Store key, quiet otherwise. A build carrying one is a
        // development build by definition — and when a purchase misbehaves,
        // the SDK's own log is the only place that says why.
        Purchases.logLevel = if (apiKey.startsWith("test_")) LogLevel.DEBUG else LogLevel.WARN
        Purchases.configure(apiKey = apiKey) {
            this.appUserId = appUserId
        }
    }

    /** Signing in after the SDK started moves the purchases to that account. */
    suspend fun identify(appUserId: String) = runCatchingSupport {
        if (Purchases.sharedInstance.appUserID != appUserId) {
            Purchases.sharedInstance.logIn(appUserId, onError = {}, onSuccess = { _, _ -> })
        }
    }

    /**
     * What is on offer, or null when nothing is.
     *
     * A null offering is not an error: it is what an account with no products
     * configured yet looks like, and the screen says so rather than showing an
     * empty paywall.
     */
    suspend fun currentOffering(): Result<Offering?> = runCatchingSupport {
        Purchases.sharedInstance.awaitOfferings().current
    }

    /**
     * Buys one tier.
     *
     * A cancelled purchase comes back as a failure like any other, because from
     * here it is the same thing: nothing was bought. The caller decides whether
     * that is worth saying out loud — it is not.
     */
    suspend fun purchase(pkg: Package): Result<CustomerInfo> = runCatchingSupport {
        Purchases.sharedInstance.awaitPurchase(packageToPurchase = pkg).customerInfo
    }

    suspend fun customerInfo(): Result<CustomerInfo> = runCatchingSupport {
        Purchases.sharedInstance.awaitCustomerInfo()
    }

    /**
     * Purchases made on another device, or before this one was reinstalled.
     *
     * Stores require this to exist for anything non-consumable, and it costs
     * one call.
     */
    suspend fun restore(): Result<CustomerInfo> = runCatchingSupport {
        Purchases.sharedInstance.awaitRestore()
    }

    /**
     * Whether this person is a supporter right now.
     *
     * Read off the entitlement rather than off a purchase this app remembers:
     * RevenueCat is the one that knows about renewals, refunds and expiry, and
     * an app that decides for itself will be wrong the first time any of those
     * happen.
     */
    fun isSupporter(info: CustomerInfo): Boolean =
        info.entitlements[SUPPORTER_ENTITLEMENT]?.isActive == true

    /**
     * Whether there is a subscription to manage.
     *
     * A coffee is a one-off and has nothing to cancel; the monthly does. The
     * Customer Center is only worth offering to the second kind.
     */
    fun hasSubscription(info: CustomerInfo): Boolean = info.activeSubscriptions.isNotEmpty()

    /**
     * The store product ids the person is subscribed to right now.
     *
     * Used to mark a tier on the paywall as one they already have, so it reads
     * as active instead of as one more thing to buy. Store-shaped ids (Google's
     * carry a "product:base_plan" suffix), matched leniently against a package.
     */
    fun activeSubscriptions(info: CustomerInfo): Set<String> = info.activeSubscriptions

    /**
     * When each held subscription next renews, as epoch milliseconds by store id.
     *
     * From RevenueCat's own dates — it is the one that tracks renewals and
     * refunds — so the paywall can say "renews on…" without this app guessing a
     * cycle. Epoch millis rather than the SDK's time type so nothing experimental
     * leaks past the billing layer; the UI turns it into a local date.
     */
    @OptIn(kotlin.time.ExperimentalTime::class)
    fun subscriptionRenewals(info: CustomerInfo): Map<String, Long> =
        info.allExpirationDates.entries
            .mapNotNull { (id, expires) -> expires?.let { id to it.toEpochMilliseconds() } }
            .toMap()

    /**
     * Whether the support subscription is set to renew.
     *
     * False once it has been cancelled: it stays active to the end of the paid
     * period but will not charge again, so the paywall says "active until" that
     * date rather than "renews on" it. Defaults to true when there is no
     * subscription, which is the harmless reading for a coffee or nobody.
     */
    fun subscriptionWillRenew(info: CustomerInfo): Boolean =
        info.entitlements[SUPPORTER_ENTITLEMENT]?.willRenew != false

    /**
     * Anything the SDK throws is a failed attempt, not a crash.
     *
     * A store can be unreachable, a purchase can be cancelled mid-flow, and a
     * device can have payments disabled entirely. None of that is exceptional
     * enough to take an app down over a donation.
     *
     * On the main thread, not IO. Billing needs an Activity and builds a
     * Handler to talk to it, and a Handler cannot be built on a thread with no
     * Looper — starting a purchase from a background thread died with "Can't
     * create handler inside thread that has not called Looper.prepare()", and
     * then every later tap was refused with "the operation is already in
     * progress" because the first one never finished. The SDK does its own
     * network work off this thread; it only needs to be *called* from here.
     */
    private suspend fun <T> runCatchingSupport(block: suspend () -> T): Result<T> =
        withContext(dispatchers.main) { runCatching { block() } }

    companion object {
        /** The entitlement configured in RevenueCat for people who support the app. */
        const val SUPPORTER_ENTITLEMENT = "Poster supporter"
    }
}
