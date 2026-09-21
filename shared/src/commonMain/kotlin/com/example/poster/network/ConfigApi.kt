package com.example.poster.network

import com.example.poster.model.RemoteConfig

/**
 * What the server says the app may do. See [RemoteConfig].
 *
 * Never throws. A server that cannot be reached, or one too old to answer,
 * gives the defaults — which are the cautious ones.
 */
interface ConfigApi {
    suspend fun appConfig(): RemoteConfig = RemoteConfig()

    /**
     * Records that somebody reached for a tier while payments were off.
     *
     * Best effort, like reporting a post: somebody who has just tried to give
     * money should not be handed a network failure on top of being told the
     * feature is not ready.
     */
    suspend fun noteSupportInterest(tier: String) = Unit
}
