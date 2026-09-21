# What R8 must not rename or remove.
#
# Everything here is something that is looked up by name at runtime, which is
# exactly what R8 cannot see. The rule of thumb: if a library finds a class or a
# member from a string, obfuscating it turns a working feature into a runtime
# failure that no compiler warned about — and in a release build, one that only
# shows up on somebody else's phone.

# --- kotlinx.serialization ------------------------------------------------
#
# The plugin generates a $serializer for every @Serializable class and finds it
# reflectively. Losing it does not fail to compile; it fails when the first
# post is parsed, on a device, in the field.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.poster.**$$serializer { *; }
-keepclassmembers class com.example.poster.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
# The models themselves: their field names are the wire format. Renaming them
# renames the JSON, and the server stops recognising its own API.
-keep class com.example.poster.model.** { *; }

# --- Ktor client ----------------------------------------------------------
#
# Engines and plugins are resolved through service loading and reflection.
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# --- Koin -----------------------------------------------------------------
#
# Dependencies are looked up by type at runtime; the generic signatures are how
# it tells them apart — so those attributes stay. Koin ships its own consumer
# rules in the AAR, so a blanket keep of the whole package is redundant and only
# blocks R8 from shrinking it.
-keepattributes Signature, *Annotation*
-dontwarn org.koin.**

# --- SQLDelight -----------------------------------------------------------
#
# Generated code, not reflected on by name; SQLDelight ships consumer rules.
-dontwarn app.cash.sqldelight.**

# --- Google sign-in through Credential Manager ----------------------------
#
# The credential type is matched on a type string, and the token is read out of
# a Bundle by key. Both are strings R8 cannot follow.
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**

# --- RevenueCat -----------------------------------------------------------
#
# The Purchases SDK ships its own consumer rule, `-keep class com.revenuecat.**
# { *; }`, which keeps the whole package (it is ~two thirds of everything R8
# leaves alone). We cannot un-keep what a consumer rule keeps, so our own blanket
# keep here was pure duplication — removed. This does not shrink RevenueCat; the
# SDK's rule still keeps all of it, which is why Play's optimization score is
# capped while the CustomerCenter UI is a dependency.
-dontwarn com.revenuecat.purchases.**

# --- Coroutines -----------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# --- Crash reports --------------------------------------------------------
#
# The panel shows a stack trace to somebody trying to understand a fault. An
# obfuscated one is unreadable, and the mapping file is uploaded to Play rather
# than kept here — so keep the line numbers, and keep the original file name
# available for the trace to refer to.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
