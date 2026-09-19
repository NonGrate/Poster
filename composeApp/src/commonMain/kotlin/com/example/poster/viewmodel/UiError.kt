package com.example.poster.viewmodel

/**
 * Lightweight representation of an error that can be observed by the UI.
 */
/**
 * A failure worth reacting to.
 *
 * [message] is diagnostic, not copy: the only screen that reads this state uses
 * it as a yes/no and shows its own localized string. Anything the user reads
 * comes from string resources, so these are deliberately not translated — if a
 * screen ever needs to print one, it should carry a resource instead.
 */
data class UiError(
    val message: String,
    val cause: Throwable? = null
)
