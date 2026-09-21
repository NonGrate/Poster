package com.example.poster.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revenuecat.purchases.kmp.models.Offering
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.ui.revenuecatui.CustomerCenter
import com.example.poster.billing.SupportDemoTier
import com.example.poster.billing.tierIdOf
import com.example.poster.invite.InviteLink
import com.example.poster.theme.Spacing
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.cancel
import poster.composeapp.generated.resources.settings_support_restore
import poster.composeapp.generated.resources.settings_support_unavailable
import poster.composeapp.generated.resources.support_paywall_body
import poster.composeapp.generated.resources.support_paywall_title
import poster.composeapp.generated.resources.support_privacy
import poster.composeapp.generated.resources.support_section_monthly
import poster.composeapp.generated.resources.support_section_onetime
import poster.composeapp.generated.resources.months_short
import poster.composeapp.generated.resources.support_active_until
import poster.composeapp.generated.resources.support_renews_on
import poster.composeapp.generated.resources.support_subscription_disclosure
import poster.composeapp.generated.resources.support_tier_active
import poster.composeapp.generated.resources.support_terms
import poster.composeapp.generated.resources.support_tier_coffee
import poster.composeapp.generated.resources.support_tier_coffee_and_snack
import poster.composeapp.generated.resources.support_tier_lunch
import poster.composeapp.generated.resources.support_tier_dinner
import poster.composeapp.generated.resources.support_tier_coffee_subscription

/**
 * What each tier is called, here rather than in the RevenueCat dashboard.
 *
 * These are jokes, and a joke needs a translator who knows how the rest of the
 * app talks — not a tab in a dashboard that neither the Russian strings file
 * nor the architecture check can see. Prices are deliberately absent: those
 * come from the store, already in the reader's own currency, and a number
 * written here would be the American one and wrong everywhere else.
 *
 * A product with no entry falls back to the name the dashboard gives it, so a
 * tier added there still appears — it simply arrives unnamed until somebody
 * writes it a line.
 */
private val tierLabels: Map<String, StringResource> = mapOf(
    "coffee" to Res.string.support_tier_coffee,
    "coffee_and_snack" to Res.string.support_tier_coffee_and_snack,
    "lunch" to Res.string.support_tier_lunch,
    "dinner" to Res.string.support_tier_dinner,
    "coffee_subscription" to Res.string.support_tier_coffee_subscription,
)

/**
 * Where the legal pages live. A store review of a paid sheet expects both to be
 * one tap away and to actually resolve — a dead link fails review as surely as a
 * missing one. Built off the app's one origin so the domain has a single home.
 */
private val TERMS_URL = InviteLink.WEB_ORIGIN + "/terms"
private val PRIVACY_URL = InviteLink.WEB_ORIGIN + "/privacy"

/**
 * The tiers, in a sheet.
 *
 * Ours rather than RevenueCat's rendered paywall, so the words are the app's
 * own and translated with everything else. What is on offer still comes from
 * the offering, so adding or removing a tier is a dashboard change: this draws
 * whatever arrives, in the order it arrives.
 *
 * A null offering means nothing is configured yet. That is a real state with a
 * real answer, so it says so rather than showing an empty sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportPaywall(
    offering: Offering?,
    purchasing: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onBuy: (Package) -> Unit,
    onRestore: () -> Unit,
    /** Store ids of subscriptions held now: a matching tier reads "Active", not buyable. */
    activeSubscriptionIds: Set<String> = emptySet(),
    /** Next renewal per held subscription, epoch millis by store id, shown on the active tier. */
    renewalDates: Map<String, Long> = emptyMap(),
    /** False once cancelled: the active tier then says "active until", not "renews". */
    subscriptionWillRenew: Boolean = true,
    /**
     * Placeholder tiers for screenshots before a store account exists. When set,
     * the sheet draws these instead of the offering; the buttons are inert. Empty
     * in every real build.
     */
    demoTiers: List<SupportDemoTier> = emptyList(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Expand fully rather than to a half-height detent, so the tiers and the
        // restore link below them are all on screen at once; scrollable as a
        // fallback when there are more tiers than a short screen can hold.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // A plain black scrim rather than the themed one: the theme's scrim is a
        // warm grey that washes the cream screen behind it to a flat grey. Black
        // at low alpha just darkens, so the brand colour survives underneath.
        scrimColor = Color.Black.copy(alpha = 0.32f),
        modifier = Modifier.testTag("support_paywall"),
    ) {
        val uriHandler = LocalUriHandler.current
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        ) {
            if (offering == null && demoTiers.isEmpty()) {
                Text(
                    text = stringResource(Res.string.settings_support_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag("support_unavailable"),
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text(stringResource(Res.string.cancel)) }
                return@Column
            }

            Text(
                text = stringResource(Res.string.support_paywall_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(Res.string.support_paywall_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // One-time tips and the monthly subscription go under separate
            // headings. A store review of a paid sheet wants a recurring charge
            // told apart from a one-off before it is tapped — same reason the
            // subscription row carries its renewal terms in line.
            val disclosure = stringResource(Res.string.support_subscription_disclosure)
            if (demoTiers.isNotEmpty()) {
                // Screenshot placeholders: same layout, inert buttons, prices
                // that are illustrative rather than from a store.
                val oneTime = demoTiers.filter { !it.id.contains("subscription") }
                val monthly = demoTiers.filter { it.id.contains("subscription") }
                if (oneTime.isNotEmpty()) {
                    SupportSectionHeader(stringResource(Res.string.support_section_onetime))
                    oneTime.forEach { tier ->
                        val label = tierLabels[tier.id]?.let { stringResource(it) } ?: tier.id
                        SupportTierRow(label, tier.price, "support_tier_${tier.id}", true, {}, null)
                    }
                }
                if (monthly.isNotEmpty()) {
                    SupportSectionHeader(stringResource(Res.string.support_section_monthly))
                    monthly.forEach { tier ->
                        val label = tierLabels[tier.id]?.let { stringResource(it) } ?: tier.id
                        SupportTierRow(label, tier.price, "support_tier_${tier.id}", true, {}, disclosure)
                    }
                }
            } else {
                val packages = offering?.availablePackages.orEmpty()
                // A billing period is what makes a product a subscription — not
                // its name — so a subscription added under any id still lands in
                // Monthly with its terms shown, and a mis-named one-off does not.
                val oneTime = packages.filter { it.storeProduct.period == null }
                val monthly = packages.filter { it.storeProduct.period != null }
                if (oneTime.isNotEmpty()) {
                    SupportSectionHeader(stringResource(Res.string.support_section_onetime))
                    oneTime.forEach { pkg -> SupportPackageRow(pkg, !purchasing, onBuy, null) }
                }
                if (monthly.isNotEmpty()) {
                    SupportSectionHeader(stringResource(Res.string.support_section_monthly))
                    monthly.forEach { pkg ->
                        val active = isActiveSubscription(pkg, activeSubscriptionIds)
                        SupportPackageRow(pkg, !purchasing, onBuy, disclosure, active, renewalFor(pkg, renewalDates), subscriptionWillRenew)
                    }
                }
            }

            // A purchase that fails has to say so here, in front of the button
            // that was pressed. It used to set an error nothing rendered, so a
            // refused payment looked exactly like a tap that did nothing.
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().testTag("support_error"),
                )
            }

            // Down here rather than in Settings, because this is where somebody
            // wondering what happened to a purchase will look. Small, because
            // it is for the rare reinstall — but present, because a store
            // expects a way back to something already paid for.
            TextButton(
                onClick = onRestore,
                enabled = !purchasing,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("support_restore_button"),
            ) { Text(stringResource(Res.string.settings_support_restore)) }

            // A paid sheet has to carry its legal pages, not defer them to a
            // Settings screen two taps away. Both open in the browser.
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = { uriHandler.openUri(TERMS_URL) },
                    modifier = Modifier.testTag("support_terms"),
                ) { Text(stringResource(Res.string.support_terms), style = MaterialTheme.typography.bodySmall) }
                TextButton(
                    onClick = { uriHandler.openUri(PRIVACY_URL) },
                    modifier = Modifier.testTag("support_privacy"),
                ) { Text(stringResource(Res.string.support_privacy), style = MaterialTheme.typography.bodySmall) }
            }

            if (purchasing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(24.dp)
                        .testTag("support_purchasing"),
                )
            }
        }
    }
}

/** A group heading, in the quiet secondary colour — structure, not an accent. */
@Composable
private fun SupportSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs),
    )
}

/**
 * True when this package is one the person is already subscribed to.
 *
 * Store ids do not line up cleanly across platforms — Google appends a
 * ":base_plan" to the subscription id it reports as active — so the base id is
 * compared rather than the whole thing.
 */
private fun isActiveSubscription(pkg: Package, activeIds: Set<String>): Boolean =
    activeIds.any { it.substringBefore(":") == pkg.storeProduct.id.substringBefore(":") }

/** This package's next renewal, if the store reported one — matched on base id. */
private fun renewalFor(pkg: Package, renewals: Map<String, Long>): Long? {
    val base = pkg.storeProduct.id.substringBefore(":")
    return renewals.entries.firstOrNull { it.key.substringBefore(":") == base }?.value
}

/** A real store package as a tier row; label and price resolve from the store. */
@Composable
private fun SupportPackageRow(
    pkg: Package,
    enabled: Boolean,
    onBuy: (Package) -> Unit,
    disclosure: String?,
    active: Boolean = false,
    renewalMillis: Long? = null,
    willRenew: Boolean = true,
) {
    val product = pkg.storeProduct
    val label = tierLabels[tierIdOf(product.id)]?.let { stringResource(it) } ?: product.title
    // Straight from the store: the right currency, and right again the day the
    // price changes.
    SupportTierRow(label, product.price.formatted, "support_tier_${tierIdOf(product.id)}", enabled, { onBuy(pkg) }, disclosure, active, renewalMillis, willRenew)
}

/**
 * One tier: a titled, priced button, with the subscription's renewal terms in
 * line beneath it when [disclosure] is set — visible without tapping, which is
 * what a store review of a recurring charge asks for.
 *
 * When [active] the tier is one the person already holds: it stops being a
 * button and becomes a filled, un-tappable row with a tick and "Active" where
 * the price was, so a subscriber sees they have it rather than a prompt to buy
 * it again. Its subtitle then names the next renewal date instead of the
 * generic renewal terms.
 */
@Composable
private fun SupportTierRow(
    label: String,
    price: String,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    disclosure: String?,
    active: Boolean = false,
    renewalMillis: Long? = null,
    willRenew: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (active) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                // The same pill the buy buttons wear, so the active tier reads as
                // one of the set rather than a different kind of thing.
                shape = ButtonDefaults.outlinedShape,
                modifier = Modifier.fillMaxWidth().testTag(testTag),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = ButtonDefaults.MinHeight)
                        .padding(ButtonDefaults.ContentPadding),
                ) {
                    Text(label)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(Res.string.support_tier_active), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag(testTag),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(label)
                Text(price, style = MaterialTheme.typography.labelLarge)
            }
        }
        }
        // On the active tier the concrete date replaces the generic "renews
        // automatically until you cancel" — a subscriber wants the when, not the
        // terms they already agreed to. Once cancelled it will not charge again,
        // so the same date reads "active until" rather than "renews".
        val subtitle = if (active) {
            renewalMillis?.let { renewalLabel(it, willRenew) }
        } else {
            disclosure
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = Spacing.xs, top = Spacing.xxs)
                    .testTag("support_subscription_disclosure"),
            )
        }
    }
}

/**
 * "Renews 9 Oct 2026", or "Active until 9 Oct 2026" once cancelled — day,
 * localized short month, year, from an epoch millis.
 */
@Composable
private fun renewalLabel(epochMillis: Long, willRenew: Boolean): String {
    val date = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val months = stringResource(Res.string.months_short).split(",")
    val formatted = "${date.dayOfMonth} ${months.getOrElse(date.monthNumber - 1) { date.monthNumber.toString() }} ${date.year}"
    val template = if (willRenew) Res.string.support_renews_on else Res.string.support_active_until
    return stringResource(template, formatted)
}

/**
 * RevenueCat's Customer Center: the place to see or cancel a subscription.
 *
 * Theirs, unlike the tiers above, because this one is not copy — it is the
 * store's own account plumbing, and there is nothing here worth rewriting.
 * Only ever opened for somebody who has a subscription; a coffee has nothing
 * to manage.
 *
 * Full height, not a partial sheet. Customer Center is a whole native screen
 * with its own navigation bar; in a half-height sheet iOS lays its container
 * out at zero and the bar's height fights that ("Unable to simultaneously
 * satisfy constraints"). Expanded fully, and filling that space, the bar has
 * the room it asks for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportCustomerCenter(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.fillMaxSize().testTag("support_customer_center"),
    ) {
        CustomerCenter(
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
