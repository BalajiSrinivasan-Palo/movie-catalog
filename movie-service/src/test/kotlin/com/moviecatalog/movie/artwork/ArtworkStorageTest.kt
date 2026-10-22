package com.moviecatalog.movie.artwork

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtworkStorageTest {
    private val root: Path = Files.createTempDirectory("storage-test")

    @Test
    fun `starting up removes leftover upload files`() {
        Files.createDirectories(root.resolve("tmp"))
        Files.write(root.resolve("tmp/stale.part"), byteArrayOf(1))
        val storage = ArtworkStorage(root)
        assertEquals(0, storage.tempFileCount())
    }

    @Test
    fun `commit moves a temp file to its final name and delete is repeatable`() {
        val storage = ArtworkStorage(root)
        val temp = storage.newTemp().also { Files.write(it, byteArrayOf(1, 2, 3)) }
        val name = ArtworkStorage.originalName(UUID.fromString("ab000000-0000-0000-0000-000000000000"), "png")
        storage.commit(temp, name)
        assertTrue(storage.exists(name))
        assertTrue(name.startsWith("ab/"))
        assertFalse(Files.exists(temp))
        storage.delete(name)
        storage.delete(name)
        assertFalse(storage.exists(name))
        storage.delete(null)
    }

    @Test
    fun `paths that leave the artwork directory are refused`() {
        val storage = ArtworkStorage(root)
        assertFailsWith<IllegalArgumentException> { storage.resolve("../outside.txt") }
        assertFailsWith<IllegalArgumentException> { storage.resolve("ab/../../outside.txt") }
        assertFailsWith<IllegalArgumentException> { storage.resolve("") }
    }

    @Test
    fun `file names come from the id only`() {
        val id = UUID.randomUUID()
        assertEquals("${id.toString().take(2)}/$id-thumb.jpg", ArtworkStorage.thumbnailName(id))
    }
}
