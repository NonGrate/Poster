package com.example.poster.viewmodel

import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.plus

/**
 * Base class providing a structured coroutine scope for shared view models.
 *
 * ## Lifetime
 *
 * [scope] lives as long as the instance. Every ViewModel here is a Koin
 * `single`, so in the app that means the whole process: work started in [scope]
 * outlives any screen that started it, which is what makes a load survive a tab
 * change. Nothing calls [clear] in the app today; it exists for tests and for
 * the day these stop being singletons.
 *
 * ## What that means when you write one
 *
 * - Work that must not outlive what asked for it belongs inside a
 *   `collectLatest` on the state that owns it, not in a bare `scope.launch` —
 *   the launch escapes cancellation. `FavoritesViewModel` learned this the hard
 *   way: a favorites load launched separately kept writing after sign-out
 *   (`BUGS.md` #11).
 * - A result that arrives late should be checked against current state before it
 *   is applied, because "cancelled" and "already returned" are different things.
 * - [dispatchers] is injected so tests can make main and IO immediate. Do not
 *   reach for `Dispatchers.Main` directly; it does not exist off-device.
 */
abstract class ScopedViewModel(
    protected val dispatchers: DispatcherProvider
) {
    private val job = SupervisorJob()
    protected val scope: CoroutineScope = CoroutineScope(job + dispatchers.main)

    open fun clear() {
        job.cancel()
    }
}
