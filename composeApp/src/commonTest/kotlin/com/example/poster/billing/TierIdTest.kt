package com.example.poster.billing

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Naming a tier, whichever store the product came from.
 *
 * The paywall names its tiers itself — the labels are jokes and want a
 * translator, not a dashboard — and looks them up by product id. Google gives a
 * subscription's id as `product:base_plan` and everything else gives the bare
 * id, so the lookup missed for exactly one row and only on Play. The
 * subscription arrived as Google's own title: untranslated, carrying the app
 * name Google appends, and long enough to wrap the price onto two lines.
 *
 * Nothing failed. It read as a tier somebody had forgotten to name.
 */
class TierIdTest {

    @Test
    fun aPlaySubscriptionIsNamedByItsProductNotItsBasePlan() {
        assertEquals("coffee_subscription", tierIdOf("coffee_subscription:coffee-subscription"))
    }

    /** Every one-off product, and everything the Test Store serves. */
    @Test
    fun aBareIdIsAlreadyTheTier() {
        assertEquals("coffee", tierIdOf("coffee"))
        assertEquals("coffee_and_snack", tierIdOf("coffee_and_snack"))
        assertEquals("lunch", tierIdOf("lunch"))
        assertEquals("dinner", tierIdOf("dinner"))
        assertEquals("coffee_subscription", tierIdOf("coffee_subscription"))
    }

    /**
     * A second base plan on the same product is the same tier.
     *
     * Play allows several — monthly and yearly, say — and they are ways of
     * selling one thing. The app has one name for it.
     */
    @Test
    fun everyBasePlanOfAProductIsTheSameTier() {
        assertEquals(
            tierIdOf("coffee_subscription:monthly"),
            tierIdOf("coffee_subscription:yearly"),
        )
    }

    /** Nothing to take apart, nothing taken apart. */
    @Test
    fun anEmptyIdSurvives() {
        assertEquals("", tierIdOf(""))
    }
}
