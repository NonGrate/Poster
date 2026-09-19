# In-app purchases (RevenueCat)

"Support the developer" at the bottom of Settings: one-off tips and a monthly
subscription, through [RevenueCat](https://www.revenuecat.com/) (`purchases-kmp`).
The repository holds the wiring and none of the pricing — products, offering
and paywall text for the *store* are configured in the RevenueCat dashboard; the
tier names shown in the app are string resources.

## Turn it off

`feature.support=false` in `poster.properties`. That removes the Settings
section **and the RevenueCat SDK from the Android build** — the three files that
import it are swapped for stubs (`composeApp/src/billing/{enabled,disabled}`; see
[`Configuration.md`](Configuration.md)). On iOS, also remove the
`purchases-ios-spm` package from the Xcode project if you want it gone.

A blank key is also a supported state: the section is hidden and nothing else
cares. That is how the `e2e` flavour ships, so the instrumented tests never see
a payments backend.

## Turn it on

### Keys — never in the repository

```properties
# ~/.gradle/gradle.properties  (outside the repo)
posterRevenueCatKey=test_xxxxxxxxxxxxxxxxxxxxxxxx      # Android; later goog_...
```
```
# iosApp/Configuration/Local.xcconfig  (git-ignored)
POSTER_REVENUECAT_KEY=appl_xxxxxxxxxxxxxxxxxxxxxxxx
```

The Android release build **refuses** a key starting with `test_` — RevenueCat's
Test Store key must never reach a store build. Debug builds accept it, which is
what it is for.

### Dashboard

1. Create the products. With the Test Store this needs no Google Play or App
   Store setup; that is what the `test_` key is for.
2. Put them in an **offering** and make it current. Until then the sheet says
   there is nothing to offer, which is the honest answer.
3. Create the **entitlement** named exactly as `SupportRepository.SUPPORTER_ENTITLEMENT`
   (`"Poster supporter"` — rename in `composeApp/src/billing/enabled/kotlin/.../SupportRepository.kt`)
   and attach the subscription product. One-off tips grant nothing: a coffee
   unlocks nothing, it says thank you.
4. Tier names: the app maps product ids to localized names in
   `SupportPaywall.kt` (`tierLabels`: `coffee`, `coffee_and_snack`, `lunch`,
   `dinner`, `coffee_subscription`). A product with no entry falls back to the
   dashboard title. Prices always come from the store, in the buyer's currency.

Google gives a subscription's id as `product:base_plan`; `tierIdOf()` strips the
base plan so the same name applies (`TierIdTest`).

### The remote switch

`GET /config` returns `paymentsEnabled`, from the server's `POSTER_PAYMENTS_ENABLED`.
The paywall shows tiers but refuses to buy while it is false, and logs the tap
(`POST /support/interest`) so you learn which tiers people reach for before you
can legally take money. Flip the variable and restart — no app release.

### Before a store release

- Replace the Test Store key with the real Play (`goog_`) / App Store (`appl_`) key.
- Products must exist in Play Console / App Store Connect and the app must be on
  a testing track before a real purchase can complete.
- The subscription disclosure and Terms/Privacy links on the sheet are what
  store review looks for; they are already there and point at `/terms`, `/privacy`.

## Screenshots without a store

`-PposterDemoPaywall=true` (Android) or the `POSTER_DEMO_PAYWALL=1` launch
environment (iOS UI test) shows the sheet with placeholder tiers and inert
buttons. The screenshot scripts pass it. Never set in a shipped build.

## Files

- `composeApp/src/billing/enabled/.../billing/SupportRepository.kt` — the SDK
  calls: configure, identify, offering, purchase, restore, entitlement checks.
- `.../viewmodel/SupportViewModel.kt` — state for Settings; `.../ui/components/SupportPaywall.kt` — the sheet and Customer Center.
- `composeApp/src/commonMain/.../billing/TierId.kt`, `SupportDemoTier.kt` — shared with the stubs.
- `composeApp/build.gradle.kts` — key property, `test_` guard, source-set swap.
- `server/.../Application.kt` — `/config`, `/support/interest`.
