package com.example.poster.model

import kotlinx.serialization.Serializable

/**
 * What the server says the app may do right now.
 *
 * Deliberately small. This is not a feature-flag system and should not become
 * one — it exists because payments have a legal switch-on date nobody can
 * predict, and shipping a build to wait for it is worse than asking.
 *
 * Defaults are the safe answer, so a server too old to know the field, or a
 * device that cannot reach one, behaves as though payments are off. Offering to
 * take money when we are not sure we may is the failure worth avoiding.
 */
@Serializable
data class RemoteConfig(
    val paymentsEnabled: Boolean = false,
)
