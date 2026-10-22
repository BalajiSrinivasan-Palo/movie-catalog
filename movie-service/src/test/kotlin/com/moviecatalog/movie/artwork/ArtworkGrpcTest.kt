package com.moviecatalog.movie.artwork

import com.google.protobuf.ByteString
import com.moviecatalog.contracts.movie.v1.ArtworkInfo
import com.moviecatalog.contracts.movie.v1.ArtworkMetadata
import com.moviecatalog.contracts.movie.v1.ArtworkVariant
import com.moviecatalog.contracts.movie.v1.CreateMovieRequest
import com.moviecatalog.contracts.movie.v1.DownloadArtworkRequest
import com.moviecatalog.contracts.movie.v1.DownloadArtworkResponse
import com.moviecatalog.contracts.movie.v1.Genre
import com.moviecatalog.contracts.movie.v1.GetMovieRequest
import com.moviecatalog.contracts.movie.v1.ListMoviesRequest
import com.moviecatalog.contracts.movie.v1.MovieInput
import com.moviecatalog.contracts.movie.v1.MovieServiceGrpc
import com.moviecatalog.contracts.movie.v1.RemoveArtworkRequest
import com.moviecatalog.contracts.movie.v1.UploadArtworkRequest
import com.moviecatalog.movie.CreditRepository
import com.moviecatalog.movie.MovieGrpcService
import com.moviecatalog.movie.MovieRepository
import com.moviecatalog.support.GrpcErrors
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.ClientCallStreamObserver
import io.grpc.stub.StreamObserver
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Upload, download and removal through a real gRPC channel, including the failure and cancel paths (BR-11). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ArtworkGrpcTest {
    @Autowired lateinit var impl: MovieGrpcService
    @Autowired lateinit var movieRepository: MovieRepository
    @Autowired lateinit var creditRepository: CreditRepository
    @Autowired lateinit var storage: ArtworkStorage
    private lateinit var stub: MovieServiceGrpc.MovieServiceBlockingStub
    private lateinit var async: MovieServiceGrpc.MovieServiceStub
    private val closers = mutableListOf<() -> Unit>()

    @BeforeTest
    fun start() {
        creditRepository.deleteAll(); movieRepository.deleteAll()
        val name = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder.forName(name).directExecutor().addService(impl).build().start()
        val channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        closers += { channel.shutdownNow(); server.shutdownNow() }
        stub = MovieServiceGrpc.newBlockingStub(channel)
        async = MovieServiceGrpc.newStub(channel)
    }

    @AfterTest
    fun stop() = closers.forEach { it() }

    private fun newMovie(title: String = "Film"): String = stub.createMovie(
        CreateMovieRequest.newBuilder().setInput(
            MovieInput.newBuilder().setTitle(title).setReleaseYear(2001).addGenres(Genre.GENRE_DRAMA),
        ).build(),
    ).id

    private fun metadata(movieId: String) = UploadArtworkRequest.newBuilder()
        .setMetadata(ArtworkMetadata.newBuilder().setMovieId(movieId).setDeclaredContentType("text/plain").setDeclaredSizeBytes(1)).build()

    private fun chunk(bytes: ByteArray) = UploadArtworkRequest.newBuilder().setChunk(ByteString.copyFrom(bytes)).build()

    /** Sends [messages] and waits for the answer; errors are rethrown as the original StatusRuntimeException. */
    private fun upload(vararg messages: UploadArtworkRequest): ArtworkInfo {
        val result = CompletableFuture<ArtworkInfo>()
        val request = async.uploadArtwork(object : StreamObserver<ArtworkInfo> {
            override fun onNext(value: ArtworkInfo) { result.complete(value) }
            override fun onError(t: Throwable) { result.completeExceptionally(t) }
            override fun onCompleted() {}
        })
        messages.forEach { request.onNext(it) }
        request.onCompleted()
        try {
            return result.get(30, TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.ExecutionException) {
            throw e.cause!!
        }
    }

    private fun violation(e: StatusRuntimeException) = GrpcErrors.detailOf(e)!!.violationsList.single().messageKey

    /** Cancel cleanup runs on gRPC's thread and may finish just after the client sees the error. */
    private fun awaitNoTempFiles() {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (storage.tempFileCount() > 0 && System.nanoTime() < until) Thread.sleep(10)
        assertEquals(0, storage.tempFileCount())
    }

    @Test
    fun `upload then download of both variants`() {
        val movieId = newMovie()
        val png = TestImages.png(400, 600)
        val info = upload(metadata(movieId), chunk(png.copyOfRange(0, 1000)), chunk(png.copyOfRange(1000, png.size)))
        assertEquals("image/png", info.contentType)
        assertEquals(400, info.width)
        assertEquals(png.size.toLong(), info.sizeBytes)

        val movie = stub.getMovie(GetMovieRequest.newBuilder().setId(movieId).build())
        assertEquals(info.version, movie.artwork.version)

        fun download(variant: ArtworkVariant): Pair<DownloadArtworkResponse, ByteArray> {
            val parts = stub.downloadArtwork(DownloadArtworkRequest.newBuilder().setMovieId(movieId).setVariant(variant).build()).asSequence().toList()
            assertTrue(parts.first().hasHeader())
            assertTrue(parts.drop(1).all { it.hasChunk() })
            return parts.first() to parts.drop(1).fold(ByteArray(0)) { acc, p -> acc + p.chunk.toByteArray() }
        }
        val (originalHeader, original) = download(ArtworkVariant.ARTWORK_VARIANT_ORIGINAL)
        assertEquals("image/png", originalHeader.header.contentType)
        assertEquals(png.toList(), original.toList())
        val (thumbHeader, thumb) = download(ArtworkVariant.ARTWORK_VARIANT_THUMBNAIL)
        assertEquals("image/jpeg", thumbHeader.header.contentType)
        assertEquals(thumb.size.toLong(), thumbHeader.header.sizeBytes)
        assertEquals(320, ImageIO.read(ByteArrayInputStream(thumb)).width)
    }

    @Test
    fun `list responses say which movies have artwork`() {
        val with = newMovie("With")
        newMovie("Without")
        upload(metadata(with), chunk(TestImages.png(20, 20)))
        val movies = stub.listMovies(ListMoviesRequest.getDefaultInstance()).moviesList.associateBy { it.title }
        assertTrue(movies.getValue("With").hasArtwork())
        assertTrue(!movies.getValue("Without").hasArtwork())
    }

    @Test
    fun `declared type and size are ignored and the content decides`() {
        val movieId = newMovie()
        val e = assertFailsWith<StatusRuntimeException> { upload(metadata(movieId), chunk("plain text".toByteArray())) }
        assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
        assertEquals("artwork.type.unsupported", violation(e))
        assertEquals(0, storage.tempFileCount())
    }

    @Test
    fun `an unknown movie is not found and nothing is written`() {
        val e = assertFailsWith<StatusRuntimeException> { upload(metadata(UUID.randomUUID().toString()), chunk(TestImages.png(5, 5))) }
        assertEquals(Status.Code.NOT_FOUND, e.status.code)
        assertEquals(0, storage.tempFileCount())
    }

    @Test
    fun `protocol violations are invalid arguments`() {
        val movieId = newMovie()
        val noMetadata = assertFailsWith<StatusRuntimeException> { upload(chunk(TestImages.png(5, 5))) }
        assertEquals("artwork.protocol", violation(noMetadata))
        val twice = assertFailsWith<StatusRuntimeException> { upload(metadata(movieId), metadata(movieId)) }
        assertEquals("artwork.protocol", violation(twice))
        val empty = assertFailsWith<StatusRuntimeException> { upload() }
        assertEquals("artwork.protocol", violation(empty))
        assertEquals(0, storage.tempFileCount())
    }

    @Test
    fun `a cancelled upload leaves no temp files and no artwork`() {
        val movieId = newMovie()
        val request = async.uploadArtwork(object : StreamObserver<ArtworkInfo> {
            override fun onNext(value: ArtworkInfo) {}
            override fun onError(t: Throwable) {}
            override fun onCompleted() {}
        })
        request.onNext(metadata(movieId))
        request.onNext(chunk(ByteArray(50_000)))
        assertEquals(1, storage.tempFileCount())
        (request as ClientCallStreamObserver<*>).cancel("user left", null)
        assertEquals(0, storage.tempFileCount())
        assertTrue(!stub.getMovie(GetMovieRequest.newBuilder().setId(movieId).build()).hasArtwork())
    }

    @Test
    fun `more than 5 MiB sent through the gRPC call is refused as too large and leaves nothing (S-301 AC1)`() {
        val movieId = newMovie()
        val oneChunk = ByteArray(64 * 1024)
        val chunks = Array(81) { chunk(oneChunk) } + chunk(ByteArray(1)) // 81 * 64 KiB = 5.06 MiB, over the limit
        val e = assertFailsWith<StatusRuntimeException> { upload(metadata(movieId), *chunks) }
        assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
        assertEquals("artwork.too_large", violation(e))
        assertEquals(0, storage.tempFileCount())
        assertTrue(!stub.getMovie(GetMovieRequest.newBuilder().setId(movieId).build()).hasArtwork())
    }

    @Test
    fun `an upload that runs past its deadline leaves no file and no artwork (S-301 AC3)`() {
        val movieId = newMovie()
        val failure = CompletableFuture<Throwable>()
        val request = async.withDeadlineAfter(300, TimeUnit.MILLISECONDS).uploadArtwork(object : StreamObserver<ArtworkInfo> {
            override fun onNext(value: ArtworkInfo) {}
            override fun onError(t: Throwable) { failure.complete(t) }
            override fun onCompleted() {}
        })
        request.onNext(metadata(movieId))
        request.onNext(chunk(ByteArray(10_000)))
        // never completes: the deadline must end the call
        val error = failure.get(10, TimeUnit.SECONDS) as StatusRuntimeException
        assertEquals(Status.Code.DEADLINE_EXCEEDED, error.status.code)
        awaitNoTempFiles()
        assertTrue(!stub.getMovie(GetMovieRequest.newBuilder().setId(movieId).build()).hasArtwork())
    }

    @Test
    fun `a movie deleted in the middle of an upload ends not found and leaves no files (S-301 AC7)`() {
        val movieId = newMovie()
        val before = storage.listStoredFiles().toSet() // other tests in this class share the artwork directory
        val result = CompletableFuture<ArtworkInfo>()
        val request = async.uploadArtwork(object : StreamObserver<ArtworkInfo> {
            override fun onNext(value: ArtworkInfo) { result.complete(value) }
            override fun onError(t: Throwable) { result.completeExceptionally(t) }
            override fun onCompleted() {}
        })
        request.onNext(metadata(movieId))
        request.onNext(chunk(TestImages.png(30, 30)))
        stub.deleteMovie(com.moviecatalog.contracts.movie.v1.DeleteMovieRequest.newBuilder().setId(movieId).build())
        request.onCompleted()
        val e = assertFailsWith<java.util.concurrent.ExecutionException> { result.get(30, TimeUnit.SECONDS) }
        assertEquals(Status.Code.NOT_FOUND, (e.cause as StatusRuntimeException).status.code)
        assertEquals(0, storage.tempFileCount())
        assertEquals(before, storage.listStoredFiles().toSet())
    }

    @Test
    fun `remove clears artwork and download then says not found`() {
        val movieId = newMovie()
        upload(metadata(movieId), chunk(TestImages.png(20, 20)))
        stub.removeArtwork(RemoveArtworkRequest.newBuilder().setMovieId(movieId).build())
        assertTrue(!stub.getMovie(GetMovieRequest.newBuilder().setId(movieId).build()).hasArtwork())
        val gone = assertFailsWith<StatusRuntimeException> {
            stub.downloadArtwork(DownloadArtworkRequest.newBuilder().setMovieId(movieId).build()).asSequence().toList()
        }
        assertEquals(Status.Code.NOT_FOUND, gone.status.code)
        assertEquals("artwork.not_found", GrpcErrors.detailOf(gone)!!.code)
        val again = assertFailsWith<StatusRuntimeException> { stub.removeArtwork(RemoveArtworkRequest.newBuilder().setMovieId(movieId).build()) }
        assertEquals(Status.Code.NOT_FOUND, again.status.code)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val dir = Files.createTempDirectory("artwork-grpc-test")
            registry.add("MOVIE_DB_PATH") { dir.resolve("db").toString() }
            registry.add("grpc.server.port") { "0" }
            registry.add("ARTWORK_DIR") { dir.resolve("artwork").toString() }
        }
    }
}
