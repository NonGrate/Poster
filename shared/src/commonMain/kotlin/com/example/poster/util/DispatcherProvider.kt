package com.example.poster.util

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Simple holder for coroutine dispatchers used across repositories and view models.
 */
data class DispatcherProvider(
    val main: CoroutineDispatcher,
    val io: CoroutineDispatcher
)
