package com.example.poster.testing

import java.nio.file.Files

/**
 * Runs [block] against a throwaway database file, then puts `poster.database`
 * back the way it was.
 *
 * JVM-only, and so not in FakeApis.kt next door: the property is a JVM
 * property. Restoring rather than clearing matters because the tests share one
 * JVM — a test that clears it leaves whatever ran before pointing at the
 * developer's own database.
 */
fun <T> withTempDatabase(block: () -> T): T {
    val previous = System.getProperty("poster.database")
    val file = Files.createTempFile("poster-test", ".db").toFile()
    // Deleted so the driver creates it: an empty file is not a database.
    file.delete()
    file.deleteOnExit()
    System.setProperty("poster.database", file.path)
    return try {
        block()
    } finally {
        previous?.let { System.setProperty("poster.database", it) }
            ?: System.clearProperty("poster.database")
    }
}
