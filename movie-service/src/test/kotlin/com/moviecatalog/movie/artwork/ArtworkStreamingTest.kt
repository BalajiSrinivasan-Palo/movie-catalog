package com.moviecatalog.movie.artwork

import com.google.protobuf.ByteString
import com.moviecatalog.support.GrpcErrors
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.Tag
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Runs in a JVM limited to 128 MB (`streamingTest` in build.gradle.kts). If the code ever held a whole
 * upload or a whole decoded picture in memory, these cases would run out of heap (NFR-ARCH-05).
 */
@Tag("streaming")
class ArtworkStreamingTest {
    private val root: Path = Files.createTempDirectory("streaming-test")
    private val storage = ArtworkStorage(root)
    private val ingest = ArtworkIngest(storage, ArtworkLimits())

    private fun send(bytes: ByteArray, chunkSize: Int = 64 * 1024): PreparedArtwork {
        val session = ingest.begin()
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(bytes.size, offset + chunkSize)
            session.write(ByteString.copyFrom(bytes, offset, end - offset))
            offset = end
        }
        return session.finish()
    }

    private fun committedFiles(): Long = Files.walk(root).use { s ->
        s.filter { Files.isRegularFile(it) && !it.startsWith(root.resolve("tmp")) }.count()
    }

    private fun key(e: StatusRuntimeException) = GrpcErrors.detailOf(e)!!.violationsList.single().messageKey

    @Test
    fun `a file of exactly 5 MiB in 64 KiB chunks is accepted`() {
        val bytes = TestImages.padded(TestImages.png(200, 300), 5 * 1024 * 1024)
        val prepared = send(bytes)
        assertEquals(5L * 1024 * 1024, prepared.sizeBytes)
        assertEquals(2, committedFiles())
        assertEquals(0, storage.tempFileCount())
    }

    @Test
    fun `one byte over 5 MiB is rejected while streaming and leaves no files`() {
        val bytes = TestImages.padded(TestImages.png(200, 300), 5 * 1024 * 1024 + 1)
        val e = assertFailsWith<StatusRuntimeException> { send(bytes) }
        assertEquals("artwork.too_large", key(e))
        assertEquals(0, committedFiles())
        assertEquals(0, storage.tempFileCount())
    }

    @Test
    fun `a 6000 by 6000 picture is accepted and its thumbnail made without decoding it all`() {
        val prepared = send(TestImages.solidGrayPng(6000, 6000))
        assertEquals(6000, prepared.info.width)
        assertTrue(storage.exists(prepared.thumbnailPath))
        assertEquals(2, committedFiles())
    }

    @Test
    fun `a picture one pixel past the limit is rejected`() {
        val e = assertFailsWith<StatusRuntimeException> { send(TestImages.solidGrayPng(6001, 1)) }
        assertEquals("artwork.dimensions.too_large", key(e))
        assertEquals(0, committedFiles())
        assertEquals(0, storage.tempFileCount())
    }

    @Test
    fun `cancelling part way leaves nothing behind`() {
        val session = ingest.begin()
        session.write(ByteString.copyFrom(ByteArray(100_000)))
        session.abort()
        session.abort() // repeating is harmless
        assertEquals(0, committedFiles())
        assertEquals(0, storage.tempFileCount())
    }
}
