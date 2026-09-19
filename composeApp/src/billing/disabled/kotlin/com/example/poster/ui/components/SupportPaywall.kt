package com.example.poster.ui.components

import androidx.compose.runtime.Composable
import com.example.poster.billing.SupportDemoTier

/** Never shown: the Settings screen hides the section when support is off. */
@Composable
fun SupportPaywall(
    offering: Any?,
    purchasing: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onBuy: (Any) -> Unit,
    onRestore: () -> Unit,
    activeSubscriptionIds: Set<String> = emptySet(),
    renewalDates: Map<String, Long> = emptyMap(),
    subscriptionWillRenew: Boolean = true,
    demoTiers: List<SupportDemoTier> = emptyList(),
) = Unit

@Composable
fun SupportCustomerCenter(onDismiss: () -> Unit) = Unit
