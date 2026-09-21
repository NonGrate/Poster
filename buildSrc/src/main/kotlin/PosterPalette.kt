import java.util.Locale
import java.util.Properties
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The colour palette from `poster.properties`.
 *
 * Two seeds are enough: `color.primary` and `color.accent`. Every Material 3
 * role for the light and the dark scheme is derived from them (plus an optional
 * `color.tertiary` and `color.neutral`), the way Material's theme builder does
 * it, with tones: a role is the seed's hue and saturation at a fixed lightness.
 * The derivation is HSL, not HCT, so a very light seed (yellow) gets a darker
 * "primary" than you might expect — override the role then.
 *
 * Any explicit `color.light.<role>` / `color.dark.<role>` wins over the
 * derived value, so a palette from Material Theme Builder can be pasted in
 * whole, or one role corrected.
 *
 * Every "on" colour is checked against what it sits on: below 4.5:1 the build
 * fails and names the pair, because that is the one thing a template cannot
 * let a colour choice silently break.
 */
object PosterPalette {
    val roles = listOf(
        "primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "inversePrimary",
        "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
        "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
        "error", "onError", "errorContainer", "onErrorContainer",
        "background", "onBackground", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
        "surfaceTint", "outline", "outlineVariant", "scrim", "inverseSurface", "inverseOnSurface",
        "surfaceBright", "surfaceDim", "surfaceContainerLowest", "surfaceContainerLow",
        "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest",
    )
    val seeds = listOf("primary", "accent", "tertiary", "neutral")
    val modes = listOf("light", "dark")

    const val DEFAULT_PRIMARY = "#4F46E5"
    const val DEFAULT_ACCENT = "#F59E0B"

    /** True for a key this object reads: a seed or an explicit role. */
    fun knows(key: String): Boolean {
        val parts = key.split(".")
        return parts.size == 2 && parts[0] == "color" && parts[1] in seeds ||
            parts.size == 3 && parts[0] == "color" && parts[1] in modes && parts[2] in roles
    }

    /** mode -> role -> "#RRGGBB", derived then overridden by explicit keys. */
    fun resolve(props: Properties): Map<String, Map<String, String>> {
        val primary = Hsl.parse(props.getProperty("color.primary") ?: DEFAULT_PRIMARY)
        val accent = Hsl.parse(props.getProperty("color.accent") ?: DEFAULT_ACCENT)
        // A third hue between the two, quieter, unless given.
        val tertiary = props.getProperty("color.tertiary")?.let(Hsl::parse)
            ?: primary.copy(h = (primary.h + 300) % 360, s = min(primary.s, 0.45))
        // Surfaces carry a hint of the primary hue, as Material's neutrals do.
        val neutral = props.getProperty("color.neutral")?.let(Hsl::parse) ?: primary.copy(s = 0.08)
        val neutralVariant = neutral.copy(s = min(neutral.s + 0.08, 0.2))
        val error = Hsl.parse("#B3261E")

        fun tone(seed: Hsl, light: Int, dark: Int) = seed.at(light / 100.0) to seed.at(dark / 100.0)

        /**
         * A surface and the text on it, both from one seed, for one mode.
         *
         * The text is whichever of the two candidate tones reads better (an
         * amber or a lime seed gets dark text, as Material's builder does). If
         * even that is under 4.5:1 — a saturated teal at tone 30 is brighter
         * than its number says — the surface moves away from the text, a step
         * at a time, until the pair reads.
         */
        fun readable(seed: Hsl, surfaceTone: Int, textTone: Int, otherTextTone: Int): Pair<String, String> {
            var bg = surfaceTone / 100.0
            val towardsDark = textTone > 50
            while (true) {
                val surface = seed.at(bg)
                val preferred = seed.at(textTone / 100.0)
                val other = seed.at(otherTextTone / 100.0)
                val text = when {
                    contrast(preferred, surface) >= 4.5 -> preferred
                    contrast(other, surface) >= 4.5 -> other
                    else -> listOf(preferred, other).maxBy { contrast(it, surface) }
                }
                if (contrast(text, surface) >= 4.5 || bg <= 0.0 || bg >= 1.0) return surface to text
                bg = if (towardsDark) bg - 0.02 else bg + 0.02
            }
        }
        fun both(seed: Hsl, light: Triple<Int, Int, Int>, dark: Triple<Int, Int, Int>): Pair<Pair<String, String>, Pair<String, String>> =
            readable(seed, light.first, light.second, light.third) to readable(seed, dark.first, dark.second, dark.third)
        fun surfaces(p: Pair<Pair<String, String>, Pair<String, String>>) = p.first.first to p.second.first
        fun texts(p: Pair<Pair<String, String>, Pair<String, String>>) = p.first.second to p.second.second

        // (surface tone, preferred text tone, other text tone) for light and dark.
        // In light mode the seed itself is the colour when text reads on it —
        // the developer asked for that colour, not a darker cousin — else tone 40.
        fun mainFor(seed: Hsl): Pair<Triple<Int, Int, Int>, Triple<Int, Int, Int>> {
            val own = seed.hex()
            val readsAsIs = contrast(seed.at(1.0), own) >= 4.5 || contrast(seed.at(0.10), own) >= 4.5
            val lightTone = if (readsAsIs && seed.l in 0.25..0.65) (seed.l * 100).toInt() else 40
            return Triple(lightTone, 100, 10) to Triple(80, 20, 98)
        }
        val container = Triple(90, 10, 100) to Triple(30, 90, 10)
        val primaryP = both(primary, mainFor(primary).first, mainFor(primary).second); val primaryC = both(primary, container.first, container.second)
        val secondaryP = both(accent, mainFor(accent).first, mainFor(accent).second); val secondaryC = both(accent, container.first, container.second)
        val tertiaryP = both(tertiary, mainFor(tertiary).first, mainFor(tertiary).second); val tertiaryC = both(tertiary, container.first, container.second)
        val errorP = both(error, mainFor(error).first, mainFor(error).second); val errorC = both(error, container.first, container.second)
        val surfaceP = both(neutral, Triple(98, 10, 100), Triple(6, 90, 10))
        val variantP = both(neutralVariant, Triple(90, 30, 10), Triple(30, 80, 98))
        val inverseP = both(neutral, Triple(20, 95, 100), Triple(90, 20, 10))
        val derived: Map<String, Pair<String, String>> = mapOf(
            "primary" to surfaces(primaryP), "onPrimary" to texts(primaryP),
            "primaryContainer" to surfaces(primaryC), "onPrimaryContainer" to texts(primaryC),
            "inversePrimary" to tone(primary, 80, 40),
            "secondary" to surfaces(secondaryP), "onSecondary" to texts(secondaryP),
            "secondaryContainer" to surfaces(secondaryC), "onSecondaryContainer" to texts(secondaryC),
            "tertiary" to surfaces(tertiaryP), "onTertiary" to texts(tertiaryP),
            "tertiaryContainer" to surfaces(tertiaryC), "onTertiaryContainer" to texts(tertiaryC),
            "error" to surfaces(errorP), "onError" to texts(errorP),
            "errorContainer" to surfaces(errorC), "onErrorContainer" to texts(errorC),
            "background" to surfaces(surfaceP), "onBackground" to texts(surfaceP),
            "surface" to surfaces(surfaceP), "onSurface" to texts(surfaceP),
            "surfaceVariant" to surfaces(variantP), "onSurfaceVariant" to texts(variantP),
            "surfaceTint" to surfaces(primaryP),
            "outline" to tone(neutralVariant, 50, 60),
            "outlineVariant" to tone(neutralVariant, 80, 30),
            "scrim" to ("#000000" to "#000000"),
            "inverseSurface" to surfaces(inverseP), "inverseOnSurface" to texts(inverseP),
            "surfaceBright" to tone(neutral, 98, 24),
            "surfaceDim" to tone(neutral, 87, 6),
            "surfaceContainerLowest" to tone(neutral, 100, 4),
            "surfaceContainerLow" to tone(neutral, 96, 10),
            "surfaceContainer" to tone(neutral, 94, 12),
            "surfaceContainerHigh" to tone(neutral, 92, 17),
            "surfaceContainerHighest" to tone(neutral, 90, 22),
        )
        val resolved = modes.associateWith { mode ->
            roles.associateWith { role ->
                val explicit = props.getProperty("color.$mode.$role")?.trim()
                val pair = derived.getValue(role)
                (explicit ?: if (mode == "light") pair.first else pair.second).let(::normalise)
            }
        }
        // Readable text on every surface that carries text, both schemes.
        val pairs = listOf(
            "onPrimary" to "primary", "onPrimaryContainer" to "primaryContainer",
            "onSecondary" to "secondary", "onSecondaryContainer" to "secondaryContainer",
            "onTertiary" to "tertiary", "onTertiaryContainer" to "tertiaryContainer",
            "onError" to "error", "onErrorContainer" to "errorContainer",
            "onBackground" to "background", "onSurface" to "surface",
            "onSurfaceVariant" to "surfaceVariant", "inverseOnSurface" to "inverseSurface",
        )
        for ((mode, palette) in resolved) for ((fg, bg) in pairs) {
            val ratio = contrast(palette.getValue(fg), palette.getValue(bg))
            require(ratio >= 4.5) {
                "poster.properties: $mode $fg (${palette[fg]}) on $bg (${palette[bg]}) has contrast %.1f:1, below 4.5:1 — pick a different seed or set color.$mode.$fg".format(ratio)
            }
        }
        return resolved
    }

    fun normalise(hex: String): String {
        val clean = hex.trim().removePrefix("#")
        require(clean.length == 6 && clean.all { it in "0123456789abcdefABCDEF" }) { "bad colour: $hex" }
        return "#" + clean.uppercase(Locale.ROOT)
    }

    /** WCAG contrast ratio between two #RRGGBB colours. */
    fun contrast(a: String, b: String): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(hex: String): Double {
        val v = normalise(hex).drop(1).toInt(16)
        fun channel(c: Int): Double { val s = c / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channel(v shr 16 and 0xFF) + 0.7152 * channel(v shr 8 and 0xFF) + 0.0722 * channel(v and 0xFF)
    }

    /** Hue 0..360, saturation and lightness 0..1. */
    data class Hsl(val h: Double, val s: Double, val l: Double) {
        /** The same hue and saturation at another lightness. */
        fun at(lightness: Double): String = copy(l = lightness.coerceIn(0.0, 1.0)).hex()

        fun hex(): String {
            val c = (1 - abs(2 * l - 1)) * s
            val x = c * (1 - abs((h / 60.0) % 2 - 1))
            val m = l - c / 2
            val (r1, g1, b1) = when {
                h < 60 -> Triple(c, x, 0.0); h < 120 -> Triple(x, c, 0.0); h < 180 -> Triple(0.0, c, x)
                h < 240 -> Triple(0.0, x, c); h < 300 -> Triple(x, 0.0, c); else -> Triple(c, 0.0, x)
            }
            fun byte(v: Double) = ((v + m) * 255).toInt().coerceIn(0, 255)
            return "#%02X%02X%02X".format(byte(r1), byte(g1), byte(b1))
        }

        companion object {
            fun parse(hex: String): Hsl {
                val v = normalise(hex).drop(1).toInt(16)
                val r = (v shr 16 and 0xFF) / 255.0; val g = (v shr 8 and 0xFF) / 255.0; val b = (v and 0xFF) / 255.0
                val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
                val l = (mx + mn) / 2
                val s = if (d == 0.0) 0.0 else d / (1 - abs(2 * l - 1))
                val h = when {
                    d == 0.0 -> 0.0
                    mx == r -> 60 * (((g - b) / d) % 6)
                    mx == g -> 60 * ((b - r) / d + 2)
                    else -> 60 * ((r - g) / d + 4)
                }
                return Hsl((h + 360) % 360, s, l)
            }
        }
    }
}
