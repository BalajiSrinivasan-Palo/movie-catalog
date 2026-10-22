package com.moviecatalog.gateway

import com.google.protobuf.ByteString
import com.moviecatalog.contracts.movie.v1.ArtworkFileHeader
import com.moviecatalog.contracts.movie.v1.ArtworkInfo
import com.moviecatalog.contracts.movie.v1.DownloadArtworkRequest
import com.moviecatalog.contracts.movie.v1.DownloadArtworkResponse
import com.moviecatalog.contracts.movie.v1.GetMovieRequest
import com.moviecatalog.contracts.movie.v1.Movie
import com.moviecatalog.contracts.movie.v1.MovieServiceGrpc
import com.moviecatalog.contracts.movie.v1.RemoveArtworkRequest
import com.moviecatalog.contracts.movie.v1.RemoveArtworkResponse
import com.moviecatalog.contracts.movie.v1.UploadArtworkRequest
import com.moviecatalog.contracts.people.v1.PeopleServiceGrpc
import com.moviecatalog.support.GrpcErrors
import io.grpc.ManagedChannel
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val FILM = "3f2c1a9e-7b44-4d1e-9a10-0c8d6e5f4a3b"
private const val MISSING = "cccccccc-0000-4000-8000-000000000003"

/** A movie-service stand-in that keeps one artwork in memory and records how the upload arrived. */
class FakeArtworkMovieService : MovieServiceGrpc.MovieServiceImplBase() {
    @Volatile var stored: ByteArray? = null
    @Volatile var version = "v1"
    @Volatile var rejectWith: String? = null
    val chunkSizes = mutableListOf<Int>()

    private fun movie(id: String): Movie = Movie.newBuilder().setId(id).setTitle("Heat").setReleaseYear(1995)
        .also { b ->
            stored?.let { b.setArtwork(ArtworkInfo.newBuilder().setId("a1").setContentType("image/png").setSizeBytes(it.size.toLong()).setWidth(10).setHeight(20).setVersion(version)) }
        }.build()

    override fun getMovie(r: GetMovieRequest, o: StreamObserver<Movie>) { o.onNext(movie(r.id)); o.onCompleted() }

    override fun uploadArtwork(o: StreamObserver<ArtworkInfo>): StreamObserver<UploadArtworkRequest> = object : StreamObserver<UploadArtworkRequest> {
        val received = ByteArrayOutputStream()
        override fun onNext(m: UploadArtworkRequest) {
            if (m.hasChunk()) { chunkSizes += m.chunk.size(); m.chunk.writeTo(received) }
        }
        override fun onError(t: Throwable) {}
        override fun onCompleted() {
            val reject = rejectWith
            if (reject != null) { o.onError(GrpcErrors.invalidArgument("file" to reject)); return }
            stored = received.toByteArray(); version = "v2"
            o.onNext(ArtworkInfo.newBuilder().setId("a1").setContentType("image/png").setSizeBytes(stored!!.size.toLong()).setWidth(10).setHeight(20).setVersion(version).build())
            o.onCompleted()
        }
    }

    override fun removeArtwork(r: RemoveArtworkRequest, o: StreamObserver<RemoveArtworkResponse>) {
        stored = null
        o.onNext(RemoveArtworkResponse.newBuilder().setMovieId(r.movieId).build()); o.onCompleted()
    }

    override fun downloadArtwork(r: DownloadArtworkRequest, o: StreamObserver<DownloadArtworkResponse>) {
        val bytes = stored
        if (r.movieId == MISSING || bytes == null) { o.onError(GrpcErrors.notFound("artwork.not_found")); return }
        o.onNext(DownloadArtworkResponse.newBuilder().setHeader(ArtworkFileHeader.newBuilder().setContentType("image/png").setSizeBytes(bytes.size.toLong()).setVersion(version)).build())
        bytes.toList().chunked(1000).forEach { o.onNext(DownloadArtworkResponse.newBuilder().setChunk(ByteString.copyFrom(it.toByteArray())).build()) }
        o.onCompleted()
    }
}

@TestConfiguration
class ArtworkFakeConfig {
    private val movie = FakeArtworkMovieService()

    @Bean fun fakeArtworkMovie() = movie

    @Bean @Primary
    fun artworkDownstream(): Downstream {
        fun channel(service: io.grpc.BindableService): ManagedChannel {
            val name = InProcessServerBuilder.generateName()
            InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start()
            return InProcessChannelBuilder.forName(name).directExecutor().build()
        }
        return Downstream(channel(movie), channel(object : PeopleServiceGrpc.PeopleServiceImplBase() {}), Deadlines())
    }
}

/** S-303 and S-305 at the HTTP level: multipart upload, error shapes, cacheable image GETs. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ArtworkFakeConfig::class)
class ArtworkGatewayTest {
    @Value("\${local.server.port}") var port: Int = 0
    @Autowired lateinit var fake: FakeArtworkMovieService
    private val http = HttpClient.newHttpClient()
    private val boundary = "----catalogtest"

    private val upload = "mutation(\$id: ID!, \$file: Upload!) { uploadArtwork(movieId: \$id, file: \$file) { id artwork { url thumbnailUrl contentType width } } }"

    private fun multipart(operations: String, map: String, file: ByteArray?): ByteArray {
        val out = ByteArrayOutputStream()
        fun text(name: String, value: String) = out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
        text("operations", operations)
        text("map", map)
        if (file != null) {
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"0\"; filename=\"ignored.png\"\r\nContent-Type: image/png\r\n\r\n".toByteArray())
            out.write(file); out.write("\r\n".toByteArray())
        }
        out.write("--$boundary--\r\n".toByteArray())
        return out.toByteArray()
    }

    private fun operations(movieId: String = FILM) =
        """{"query":${Json.write(upload)},"variables":{"id":"$movieId","file":null}}"""

    private fun postMultipart(body: ByteArray, preflight: Boolean = true): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/graphql"))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
        if (preflight) builder.header("Apollo-Require-Preflight", "true")
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun get(path: String, vararg headers: Pair<String, String>): HttpResponse<ByteArray> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).GET()
        headers.forEach { (k, v) -> builder.header(k, v) }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
    }

    private val fileBytes = ByteArray(200_000) { (it % 251).toByte() }

    private fun reset() { fake.stored = null; fake.version = "v1"; fake.rejectWith = null; fake.chunkSizes.clear() }

    @Test
    fun `the exact JSON the browser sends, with escaped newline and tab, non-ASCII text and a null, is understood`() {
        reset()
        // JSON.stringify of the frontend's multi-line mutation (frontend/src/lib/api/upload.ts): \n and \t are escapes.
        val query = "mutation UploadArtwork(@@movieId: ID!, @@file: Upload!) {\\n\\tuploadArtwork(movieId: @@movieId, file: @@file) " +
            "{ id artwork { thumbnailUrl url width height contentType sizeBytes } }\\n}"
        val operations = """{"query":"$query","variables":{"movieId":"$FILM","file":null,"note":"caf\u00e9 \"quoted\" é"}}""".replace("@@", "$")
        val response = postMultipart(multipart(operations, """{"0":["variables.file"]}""", fileBytes))
        assertEquals(200, response.statusCode())
        assertTrue(!response.body().contains("errors"), response.body())
        assertTrue(response.body().contains("uploadArtwork"), response.body())
    }

    @Test
    fun `a multipart upload is streamed to movie-service in chunks and returns the movie with its artwork`() {
        reset()
        val response = postMultipart(multipart(operations(), """{"0":["variables.file"]}""", fileBytes))
        assertEquals(200, response.statusCode())
        assertTrue(!response.body().contains("errors"), response.body())
        assertTrue(response.body().contains("/artwork/$FILM/original?v=v2"), response.body())
        assertTrue(response.body().contains("/artwork/$FILM/thumbnail?v=v2"), response.body())
        assertContentEquals(fileBytes, fake.stored)
        assertTrue(fake.chunkSizes.size >= 4 && fake.chunkSizes.all { it <= 64 * 1024 }, fake.chunkSizes.toString())
    }

    @Test
    fun `a multipart request without the preflight header is refused`() {
        reset()
        val response = postMultipart(multipart(operations(), """{"0":["variables.file"]}""", fileBytes), preflight = false)
        assertEquals(403, response.statusCode())
        assertEquals(null, fake.stored)
    }

    @Test
    fun `a rejection from movie-service comes back as a keyed validation error`() {
        reset()
        fake.rejectWith = "artwork.type.unsupported"
        val response = postMultipart(multipart(operations(), """{"0":["variables.file"]}""", fileBytes))
        assertTrue(response.body().contains("VALIDATION_FAILED"), response.body())
        assertTrue(response.body().contains("artwork.type.unsupported"), response.body())
        assertTrue(response.body().contains("correlationId"), response.body())
    }

    @Test
    fun `a map that does not point at a variable is a protocol error`() {
        reset()
        val response = postMultipart(multipart(operations(), """{"0":["query"]}""", fileBytes))
        assertTrue(response.body().contains("artwork.protocol"), response.body())
        assertEquals(null, fake.stored)
        val missingFile = postMultipart(multipart(operations(), """{"0":["variables.file"]}""", null))
        assertTrue(missingFile.body().contains("artwork.protocol"), missingFile.body())
    }

    @Test
    fun `a plain JSON request cannot name an upload`() {
        reset()
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/graphql")).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"query":${Json.write(upload)},"variables":{"id":"$FILM","file":"upload:0"}}""")).build()
        val body = http.send(request, HttpResponse.BodyHandlers.ofString()).body()
        assertTrue(body.contains("errors"), body)
        assertEquals(null, fake.stored)
    }

    @Test
    fun `removeArtwork returns the movie without artwork`() {
        reset(); fake.stored = byteArrayOf(1)
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/graphql")).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"query":"mutation { removeArtwork(movieId: \"$FILM\") { id artwork { url } } }"}""")).build()
        val body = http.send(request, HttpResponse.BodyHandlers.ofString()).body()
        assertTrue(body.contains("\"artwork\":null"), body)
        assertEquals(null, fake.stored)
    }

    @Test
    fun `images are served with type, length, ETag and a year of caching`() {
        reset(); fake.stored = fileBytes
        val response = get("/artwork/$FILM/thumbnail?v=v1")
        assertEquals(200, response.statusCode())
        assertContentEquals(fileBytes, response.body())
        val h = response.headers()
        assertEquals("image/png", h.firstValue("Content-Type").get())
        assertEquals(fileBytes.size.toString(), h.firstValue("Content-Length").get())
        assertEquals("\"v1-thumbnail\"", h.firstValue("ETag").get())
        assertTrue(h.firstValue("Cache-Control").get().contains("immutable"))
        assertEquals("nosniff", h.firstValue("X-Content-Type-Options").get())
    }

    @Test
    fun `a plain JSON body over 64 KiB is refused with 413 and a GraphQL-shaped error`() {
        val big = """{"query":"{ movies { items { id } } }","variables":{"pad":"${"x".repeat(70_000)}"}}"""
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/graphql"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(big)).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(413, response.statusCode())
        assertTrue(response.body().contains("The request is too large."), response.body())
    }

    @Test
    fun `a multipart request over the 7 MB limit never reaches movie-service (S-303 AC4)`() {
        reset()
        val eightMb = ByteArray(8 * 1024 * 1024) { 7 }
        val outcome = try {
            postMultipart(multipart(operations(), """{"0":["variables.file"]}""", eightMb))
        } catch (e: java.io.IOException) {
            null // the container may close the connection while the browser is still sending
        }
        // Whatever the client sees, no part of the file was forwarded and nothing was stored.
        assertEquals(null, fake.stored)
        assertEquals(0, fake.chunkSizes.size)
        // When an answer does arrive it is the standard error shape (ADR-005), not a stack trace or a bare 500.
        if (outcome != null) {
            assertTrue(outcome.statusCode() == 413 || outcome.body().contains("artwork.too_large"), "${outcome.statusCode()} ${outcome.body().take(200)}")
            assertTrue(!outcome.body().contains("Exception"), outcome.body().take(200))
        }
    }

    @Test
    fun `a browser preflight for a multipart upload from the app origin is allowed, from any other origin it is not (NFR-SEC)`() {
        fun preflight(origin: String): HttpResponse<String> {
            val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/graphql"))
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "apollo-require-preflight")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build()
            return http.send(request, HttpResponse.BodyHandlers.ofString())
        }
        val allowed = preflight("http://localhost:5173")
        assertEquals(200, allowed.statusCode())
        assertEquals("http://localhost:5173", allowed.headers().firstValue("Access-Control-Allow-Origin").orElse(null))
        assertTrue(allowed.headers().firstValue("Access-Control-Allow-Headers").orElse("").lowercase().contains("apollo-require-preflight"))
        val refused = preflight("https://evil.example")
        assertEquals(403, refused.statusCode())
        assertEquals(null, refused.headers().firstValue("Access-Control-Allow-Origin").orElse(null))
    }

    @Test
    fun `a URL without the current version must revalidate instead of being cached for a year`() {
        reset(); fake.stored = fileBytes
        assertEquals("no-cache", get("/artwork/$FILM/thumbnail").headers().firstValue("Cache-Control").get())
        assertEquals("no-cache", get("/artwork/$FILM/thumbnail?v=old").headers().firstValue("Cache-Control").get())
    }

    @Test
    fun `a matching If-None-Match gets 304 with no body and a changed version does not`() {
        reset(); fake.stored = fileBytes
        val notModified = get("/artwork/$FILM/original", "If-None-Match" to "\"v1-original\"")
        assertEquals(304, notModified.statusCode())
        assertEquals(0, notModified.body().size)
        val changed = get("/artwork/$FILM/original", "If-None-Match" to "\"v0-original\"")
        assertEquals(200, changed.statusCode())
    }

    @Test
    fun `unknown variant, bad id and missing artwork are all 404`() {
        reset(); fake.stored = fileBytes
        assertEquals(404, get("/artwork/$FILM/huge").statusCode())
        assertEquals(404, get("/artwork/not-a-uuid/thumbnail").statusCode())
        assertEquals(404, get("/artwork/$MISSING/thumbnail").statusCode())
    }
}
