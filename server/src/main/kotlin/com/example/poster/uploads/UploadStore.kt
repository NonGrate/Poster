package com.example.poster.uploads

import java.nio.file.Files
import java.nio.file.Path
import com.example.poster.domain.validation.ImageRules
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Where post images live: one directory of files named by a random id, nothing
 * else. The database only holds the id (`Post.image`), so backing up is
 * copying the directory next to the SQLite file, and the store knows nothing
 * about posts — the route decides who may see what.
 *
 * An id is 128 random bits plus the extension, so it cannot be guessed; that is
 * what lets a freshly uploaded image be fetched before the post that will carry
 * it exists (see [sweepOrphans] for how long that grace lasts).
 */
class UploadStore(private val directory: Path) {

    init {
        Files.createDirectories(directory)
    }

    /** Writes [bytes] under a new id and returns that id (`<hex>.<extension>`). */
    fun save(bytes: ByteArray, extension: String): String {
        require(extension.matches(Regex("[a-z0-9]{1,5}"))) { "bad extension" }
        val id = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) } + "." + extension
        // Write beside, then move: a crash mid-write leaves a temp file the
        // sweep removes, never a half image under a valid id.
        val temp = directory.resolve("$id.part")
        Files.write(temp, bytes)
        Files.move(temp, directory.resolve(id), StandardCopyOption.ATOMIC_MOVE)
        return id
    }

    /** The file for [id], or null when the id is malformed or nothing is there. */
    fun file(id: String): Path? {
        if (!ImageRules.isValidId(id)) return null
        return directory.resolve(id).takeIf { it.exists() && it.isRegularFile() }
    }

    fun delete(id: String): Boolean {
        val path = file(id) ?: return false
        return Files.deleteIfExists(path)
    }

    /**
     * Removes files older than [olderThan] that [isReferenced] does not claim,
     * and any leftover `.part` files. Uploads happen before the post is saved,
     * so a fresh unreferenced file is normal; an old one is a form that was
     * abandoned, and keeping it would keep an image nobody can see.
     */
    fun sweepOrphans(olderThan: Duration, isReferenced: (String) -> Boolean): Int {
        val cutoff = Instant.now().minus(olderThan)
        var removed = 0
        directory.listDirectoryEntries().forEach { path ->
            if (!path.isRegularFile()) return@forEach
            val stale = path.getLastModifiedTime().toInstant().isBefore(cutoff)
            val orphan = path.name.endsWith(".part") || (ImageRules.isValidId(path.name) && !isReferenced(path.name))
            if (stale && orphan && Files.deleteIfExists(path)) removed++
        }
        return removed
    }

    private val random = SecureRandom()

    companion object {
        /** How long an upload no post points at is kept (and readable) before the sweep takes it. */
        val PENDING_GRACE: Duration = Duration.ofHours(24)

        /**
         * `POSTER_UPLOADS_DIR`, or `uploads` beside the working directory —
         * `/data/uploads` in Docker, so one volume holds database and images.
         */
        fun fromEnvironment(): UploadStore = UploadStore(
            Path.of(System.getenv("POSTER_UPLOADS_DIR") ?: System.getProperty("poster.uploads") ?: "uploads"),
        )
    }
}
