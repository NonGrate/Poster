# Liquid design

Two flags in `poster.properties`, both off by default:

| Flag | What it changes |
|---|---|
| `feature.liquidDesign` | Shapes a step rounder everywhere (`theme/LiquidShapes.kt`: capsule chips and buttons, 24 dp cards, 28 dp sheets); post cards become translucent panes with a hairline edge instead of lifted solid sheets. Both platforms. |
| `feature.liquidNavBar` | **Android**: the docked tab bar is replaced by a floating capsule of glass over the content, with a soft "lens" that slides to the chosen tab; screens leave room under it (`LocalBottomBarInset`). **iOS**: the Swift shell builds a SwiftUI `TabView` with one Compose view controller per tab — the system's own bar, which is Liquid Glass on iOS 26 and the standard bar before. |

They are independent: try the bar with Material shapes, or the shapes with the
docked bar. Test tags are the same either way (`feed_tab`, `my_posts_tab`, …),
so the instrumented suite runs unchanged.

## How the glass works

Compose has no backdrop filter. `ui/liquid/LiquidGlass.kt` does what a glass
library would do, in forty lines:

1. `Modifier.glassBackdropSource(backdrop)` on the screen content records
   every draw into a `GraphicsLayer` (and draws it as usual).
2. `Modifier.liquidGlass(backdrop, shape, tint)` on a surface draws that layer
   again, shifted to its own position, through a blur `RenderEffect`, then a
   tint and a one-pixel highlight.
3. The backdrop bumps a frame counter each draw; glass surfaces read it, so
   they redraw when the content under them scrolls.

Platform notes:

- **iOS**: full blur (Skia).
- **Android 12+**: full blur. **Android 8–11**: `RenderEffect` is unavailable,
  Compose ignores it, and the bar is a translucent tinted capsule — still
  legible, still floating.
- Bottom sheets and dialogs live in their own windows on Android, so they
  cannot see the recorded layer; they get the rounder shapes, not the glass.
- On iOS the bar *is* native: `ContentView.swift` builds a `TabView` when
  `nativeTabs()` says so and somebody is signed in; `IosTabs.kt` supplies the
  routes, titles, SF Symbols and a switcher Compose calls for "browse the
  feed" and notification taps. Each tab is `App(fixedTab = route)`: a
  `MainScreen` that draws no bar and, unless it is the Home tab, handles no
  links or pushes (so four instances do not pop the same dialog). Signed out,
  the single full-screen Compose view shows the login screen as before.

## Tuning

- Blur radius, tint alpha, highlight: parameters of `liquidGlass`; the bar
  sets them per light/dark in `LiquidNavBar.kt`.
- Bar height and gap: `LiquidNavBarInset` (64 + 10 + 12 dp) — change the bar
  and the inset together.
- Lens spring: `animateDpAsState(spring(dampingRatio = 0.78f, …))` in
  `LiquidNavBar.kt`.
- Card translucency: `PostCard.kt` (`0.72f` container alpha).

## Applying glass elsewhere

Anything drawn *over* the recorded content can be glass:

```kotlin
val backdrop = LocalGlassBackdrop.current
Box(Modifier.liquidGlass(backdrop, RoundedCornerShape(24.dp), MaterialTheme.colorScheme.surface)) { … }
```

`LocalGlassBackdrop` is null with the docked bar (nothing is recorded then);
`liquidGlass` degrades to a plain tinted surface in that case.
