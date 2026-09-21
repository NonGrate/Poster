package com.example.poster.billing

/**
 * The tier a store product belongs to, whichever store it came from.
 *
 * Google gives a subscription's id as `product:base_plan` —
 * `coffee_subscription:coffee-subscription` — while the Test Store, and every
 * one-off product, gives the bare id. Looking a tier up by the raw id therefore
 * missed for exactly one row, and only on Play: the subscription fell back to
 * the store's own title and arrived as "Keep the coffee coming (Poster)",
 * untranslated, carrying the app name Google appends, and long enough to wrap
 * the price onto two lines.
 *
 * A base plan is a way of selling the product, not a different product, and the
 * app has one name for it either way.
 *
 * Out here rather than beside the paywall so it can be tested: the bug was
 * invisible until a real Play subscription existed, which is the worst possible
 * moment to find out.
 */
fun tierIdOf(storeProductId: String): String = storeProductId.substringBefore(':')
