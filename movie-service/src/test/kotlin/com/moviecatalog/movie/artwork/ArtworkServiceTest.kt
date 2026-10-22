package com.moviecatalog.movie.artwork

import com.google.protobuf.ByteString
import com.moviecatalog.movie.CreditRepository
import com.moviecatalog.movie.Genre
import com.moviecatalog.movie.MovieFields
import com.moviecatalog.movie.MovieRepository
import com.moviecatalog.movie.MovieService
import com.moviecatalog.support.GrpcErrors
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ArtworkServiceTest {
    @Autowired lateinit var movieService: MovieService
    @Autowired lateinit var service: ArtworkService
    @Autowired lateinit var ingest: ArtworkIngest
    @Autowired lateinit var storage: ArtworkStorage
    @Autowired lateinit var movieRepository: MovieRepository
    @Autowired lateinit var creditRepository: CreditRepository
    @Autowired lateinit var artworkRepository: ArtworkRepository

    @BeforeTest
    fun clean() {
        creditRepository.deleteAll(); movieRepository.deleteAll()
        Files.walk(storage.root).use { s ->
            s.filter { Files.isRegularFile(it) && !it.startsWith(storage.root.resolve("tmp")) }.forEach { Files.delete(it) }
        }
    }

    private fun committedFiles(): Int = Files.walk(storage.root).use { s ->
        s.filter { Files.isRegularFile(it) && !it.startsWith(storage.root.resolve("tmp")) }.count().toInt()
    }

    private fun movie(title: String = "Film") =
        movieService.create(MovieFields(title, 2000, null, "", listOf(Genre.DRAMA))).movie.id

    private fun upload(movieId: UUID, bytes: ByteArray = TestImages.png(40, 60)): Artwork {
        val session = ingest.begin()
        session.write(ByteString.copyFrom(bytes))
        return service.replace(movieId, session.finish())
    }

    @Test
    fun `upload stores the original and a thumbnail and shows up on the movie`() {
        val id = movie()
        val art = upload(id)
        assertEquals("image/png", art.contentType)
        assertEquals(40, art.width)
        assertEquals(2, committedFiles())
        assertEquals(art.id, movieService.get(id).artwork!!.id)
        assertEquals("image/jpeg", service.fileFor(id, Variant.THUMBNAIL).contentType)
        assertEquals("image/png", service.fileFor(id, Variant.ORIGINAL).contentType)
    }

    @Test
    fun `replacing artwork switches the version and removes the old files`() {
        val id = movie()
        val first = upload(id)
        val second = upload(id, TestImages.jpeg(80, 80))
        assertNotEquals(first.version, second.version)
        assertEquals("image/jpeg", service.find(id)!!.contentType)
        assertEquals(1, artworkRepository.count())
        assertEquals(2, committedFiles())
    }

    @Test
    fun `two uploads at the same time end with exactly one artwork and no stray files`() {
        val id = movie()
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val results = (1..2).map { n ->
            pool.submit<Artwork> { start.await(); upload(id, TestImages.png(30 + n, 30)) }
        }
        start.countDown()
        val widths = results.map { it.get(30, TimeUnit.SECONDS).width }
        pool.shutdown()
        assertEquals(2, widths.toSet().size)
        assertEquals(1, artworkRepository.count())
        assertEquals(2, committedFiles())
        assertEquals(0, storage.tempFileCount())
    }

    @Test
    fun `twenty uploads at once for one movie end with one row, two files and no temp files (S-301 AC4)`() {
        val id = movie()
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val results = (1..20).map { n -> pool.submit<Artwork> { start.await(); upload(id, TestImages.png(20 + n, 30)) } }
        start.countDown()
        results.forEach { it.get(60, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(1, artworkRepository.count())
        assertEquals(2, committedFiles())
        assertEquals(0, storage.tempFileCount())
        assertEquals(emptyList(), service.findOrphans())
    }

    @Test
    fun `deleting a movie while an upload for it is running never leaves files without a row (S-301 AC7)`() {
        repeat(10) { round ->
            val id = movie("Race $round")
            val pool = Executors.newFixedThreadPool(2)
            val start = CountDownLatch(1)
            val uploading = pool.submit<Throwable?> {
                start.await()
                try { upload(id, TestImages.png(30, 30)); null } catch (e: StatusRuntimeException) { e }
            }
            val deleting = pool.submit { start.await(); movieService.delete(id) }
            start.countDown()
            val failure = uploading.get(30, TimeUnit.SECONDS)
            deleting.get(30, TimeUnit.SECONDS)
            pool.shutdown()
            // The movie is gone either way. The upload either finished first (and its files were then deleted with the movie)
            // or was refused as not found; in both cases nothing is left on disk or in the table.
            if (failure != null) assertEquals(Status.Code.NOT_FOUND, (failure as StatusRuntimeException).status.code)
            assertEquals(0, artworkRepository.count(), "round $round")
            assertEquals(0, committedFiles(), "round $round")
            assertEquals(0, storage.tempFileCount(), "round $round")
        }
    }

    @Test
    fun `files on disk without a row are reported as orphans and left alone`() {
        val id = movie()
        upload(id)
        assertEquals(emptyList(), service.findOrphans())
        val stray = storage.root.resolve("zz").also { Files.createDirectories(it) }.resolve("stray-original.png")
        Files.write(stray, byteArrayOf(1, 2, 3))
        assertEquals(listOf("zz/stray-original.png"), service.findOrphans())
        service.logOrphans() // log only
        assertTrue(Files.exists(stray))
        assertEquals(3, committedFiles()) // nothing was deleted: the two real files and the stray one
    }

    @Test
    fun `uploading for a missing movie fails and removes the prepared files`() {
        val session = ingest.begin()
        session.write(ByteString.copyFrom(TestImages.png(10, 10)))
        val prepared = session.finish()
        val e = assertFailsWith<StatusRuntimeException> { service.replace(UUID.randomUUID(), prepared) }
        assertEquals(Status.Code.NOT_FOUND, e.status.code)
        assertEquals(0, committedFiles())
    }

    @Test
    fun `removing artwork deletes the row and files, and removing again is not found`() {
        val id = movie()
        upload(id)
        service.remove(id)
        assertNull(service.find(id))
        assertEquals(0, committedFiles())
        val e = assertFailsWith<StatusRuntimeException> { service.remove(id) }
        assertEquals("artwork.not_found", GrpcErrors.detailOf(e)!!.code)
        assertFalse(movieService.get(id).artwork != null)
    }

    @Test
    fun `deleting the movie removes its artwork files`() {
        val id = movie()
        upload(id)
        assertTrue(committedFiles() == 2)
        movieService.delete(id)
        assertEquals(0, committedFiles())
        assertEquals(0, artworkRepository.count())
    }

    @Test
    fun `a list page gets artwork for its movies in one lookup`() {
        val withArt = movie("With")
        movie("Without")
        upload(withArt)
        val page = movieService.list(null, null, null, com.moviecatalog.movie.MovieSort.TITLE, false, com.moviecatalog.support.PageSpec.of(null))
        assertNull(page.items.single { it.movie.title == "Without" }.artwork)
        assertNotNull(page.items.single { it.movie.title == "With" }.artwork)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val dir = Files.createTempDirectory("artwork-service-test")
            registry.add("MOVIE_DB_PATH") { dir.resolve("db").toString() }
            registry.add("grpc.server.port") { "0" }
            registry.add("ARTWORK_DIR") { dir.resolve("artwork").toString() }
        }
    }
}
