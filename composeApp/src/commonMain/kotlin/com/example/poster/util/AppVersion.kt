package com.example.poster.util

/**
 * The build the app is, as "name (code)" — e.g. "1.0-remote (321)". Shown at the
 * bottom of Settings so a tester can say exactly which build they saw.
 */
expect fun appVersionLabel(): String
