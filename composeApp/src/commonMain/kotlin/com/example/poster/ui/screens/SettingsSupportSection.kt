package com.example.poster.ui.screens

import com.example.poster.config.Features
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.poster.ui.components.SettingsRow
import com.example.poster.ui.platform.AdaptiveSettingsSection
import com.example.poster.viewmodel.SupportViewModel
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.cancel
import poster.composeapp.generated.resources.settings_support_action
import poster.composeapp.generated.resources.settings_support_body
import poster.composeapp.generated.resources.settings_support_manage
import poster.composeapp.generated.resources.settings_support_thanks
import poster.composeapp.generated.resources.settings_support_title
import kotlinx.coroutines.launch
import com.example.poster.ui.components.SupportPaywall
import com.example.poster.ui.components.SupportCustomerCenter

/**
 * Supporting the app: the paywall, the customer centre, and the two Settings
 * rows that open them. All of it is absent in a build with no store key.
 */
@Composable
internal fun SettingsSupportSection(supportViewModel: SupportViewModel) {
    var showPaywall by remember { mutableStateOf(false) }
    var showCustomerCenter by remember { mutableStateOf(false) }
    val isSupporter by supportViewModel.isSupporter.collectAsState()
    val hasSubscription by supportViewModel.hasSubscription.collectAsState()
    val activeSubscriptions by supportViewModel.activeSubscriptions.collectAsState()
    val renewalDates by supportViewModel.renewalDates.collectAsState()
    val subscriptionWillRenew by supportViewModel.subscriptionWillRenew.collectAsState()
    val offering by supportViewModel.offering.collectAsState()
    val purchasing by supportViewModel.purchasing.collectAsState()
    val supportError by supportViewModel.error.collectAsState()
    val justSupported by supportViewModel.justSupported.collectAsState()
    // Said once, and the sheet closes on it: a purchase that leaves the tiers
    // on screen looks like it did not happen.
    LaunchedEffect(justSupported) {
        if (justSupported) {
            showPaywall = false
            supportViewModel.acknowledgeThanks()
        }
    }
    // What is on offer can change without an app release, so it is asked for
    // when this screen opens rather than once at launch.
    LaunchedEffect(Unit) { if (Features.SUPPORT) supportViewModel.refresh() }
    if (showPaywall) {
        SupportPaywall(
            offering = offering,
            purchasing = purchasing,
            error = supportError?.message,
            onDismiss = { showPaywall = false },
            onBuy = { pkg -> supportViewModel.buy(pkg) },
            onRestore = { supportViewModel.restore() },
            demoTiers = supportViewModel.demoTiers,
            activeSubscriptionIds = activeSubscriptions,
            renewalDates = renewalDates,
            subscriptionWillRenew = subscriptionWillRenew,
        )
    }
    if (showCustomerCenter) {
        // Re-ask the store on the way out: a cancel done in there is exactly the
        // change the paywall and this screen need to stop showing "renews".
        SupportCustomerCenter(onDismiss = { showCustomerCenter = false; supportViewModel.refresh() })
    }

    // Supporting the app, above signing out and below everything that is
    // actually about using it. Absent entirely in builds with no API key,
    // rather than present and dead.
    if (Features.SUPPORT && supportViewModel.available) {
        AdaptiveSettingsSection(
            title = stringResource(Res.string.settings_support_title),
            modifier = Modifier.testTag("support_section"),
        ) {
            SettingsRow(
                label = if (isSupporter) {
                    stringResource(Res.string.settings_support_thanks)
                } else {
                    stringResource(Res.string.settings_support_action)
                },
                icon = Icons.Default.Favorite,
                supporting = stringResource(Res.string.settings_support_body),
                onClick = { showPaywall = true },
                modifier = Modifier.testTag("support_button"),
                chevron = true,
            )
            // Only when there is a subscription to manage. A coffee has
            // nothing to cancel, and a row that opens a screen about
            // nothing is worse than no row.
            if (hasSubscription) {
                SettingsRow(
                    label = stringResource(Res.string.settings_support_manage),
                    icon = Icons.Default.Person,
                    onClick = { showCustomerCenter = true },
                    modifier = Modifier.testTag("support_manage_button"),
                )
            }
        }
    }
}
