package com.example.poster

import com.example.poster.uploads.UploadStore
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UploadStoreTest {
    private val directory = Files.createTempDirectory("poster-uploads")
    private val store = UploadStore(directory)

    @Test
    fun savedBytesComeBackUnderAnUnguessableId() {
        val id = store.save(byteArrayOf(1, 2, 3), "jpg")
        assertTrue(id.matches(Regex("[0-9a-f]{32}\\.jpg")), "id was $id")
        assertEquals(listOf<Byte>(1, 2, 3), Files.readAllBytes(assertNotNull(store.file(id))).toList())
        assertEquals("image/jpeg", store.contentType(id))
    }

    @Test
    fun aPathIsNotAnId() {
        // The id goes straight into a path; anything that is not exactly an id
        // must resolve to nothing rather than to a file outside the directory.
        assertNull(store.file("../etc/passwd"))
        assertNull(store.file("post.db"))
        assertFalse(store.delete("../post.db"))
    }

    @Test
    fun theSweepTakesOldOrphansAndLeavesTheRest() {
        val kept = store.save(byteArrayOf(1), "png")
        val freshOrphan = store.save(byteArrayOf(2), "png")
        val oldOrphan = store.save(byteArrayOf(3), "png")
        val leftover = directory.resolve("abandoned.part").also { Files.write(it, byteArrayOf(4)) }
        val longAgo = FileTime.from(Instant.now().minus(Duration.ofDays(2)))
        Files.setLastModifiedTime(directory.resolve(kept), longAgo)
        Files.setLastModifiedTime(directory.resolve(oldOrphan), longAgo)
        Files.setLastModifiedTime(leftover, longAgo)

        val removed = store.sweepOrphans(Duration.ofDays(1)) { it == kept }

        assertEquals(2, removed)
        assertNotNull(store.file(kept), "a referenced image was swept")
        assertNotNull(store.file(freshOrphan), "an upload still within its grace period was swept")
        assertNull(store.file(oldOrphan))
        assertFalse(Files.exists(leftover))
    }
}
