package com.example.poster.util

import androidx.compose.runtime.Composable

/**
 * The system share sheet. Not one of the twelve adaptive divergences: this is a
 * platform capability with no shared implementation to diverge from.
 */
@Composable
expect fun rememberShareText(): (String) -> Unit
