package com.moviecatalog.gateway

import com.moviecatalog.contracts.movie.v1.ArtworkInfo
import com.moviecatalog.contracts.movie.v1.ArtworkMetadata
import com.moviecatalog.contracts.movie.v1.ArtworkVariant
import com.moviecatalog.contracts.movie.v1.DownloadArtworkRequest
import com.moviecatalog.contracts.movie.v1.RemoveArtworkRequest
import com.moviecatalog.contracts.movie.v1.UploadArtworkRequest
import com.google.protobuf.ByteString
import io.grpc.Context
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.ClientCallStreamObserver
import io.grpc.stub.StreamObserver
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Artwork mutations. The file goes to movie-service in 64 KiB chunks, so the gateway never holds the whole image. */
@Controller
class ArtworkMutations(private val downstream: Downstream, private val movies: MovieController) {
    private val log = LoggerFactory.getLogger(javaClass)

    @MutationMapping
    fun uploadArtwork(@Argument movieId: String, @Argument file: UploadedFile): MovieView {
        val id = parseId(movieId, "movieId").toString()
        send(id, file)
        return movieView(id)
    }

    @MutationMapping
    fun removeArtwork(@Argument movieId: String): MovieView {
        val id = parseId(movieId, "movieId").toString()
        downstream.movie().removeArtwork(RemoveArtworkRequest.newBuilder().setMovieId(id).build())
        return movieView(id)
    }

    private fun movieView(id: String): MovieView {
        return movies.movie(id)
    }

    private fun send(movieId: String, file: UploadedFile) {
        val result = CompletableFuture<ArtworkInfo>()
        val stub = downstream.movieUpload()
        val request = stub.uploadArtwork(object : StreamObserver<ArtworkInfo> {
            override fun onNext(value: ArtworkInfo) { result.complete(value) }
            override fun onError(t: Throwable) { result.completeExceptionally(t) }
            override fun onCompleted() {}
        }) as ClientCallStreamObserver<UploadArtworkRequest>
        try {
            request.onNext(
                UploadArtworkRequest.newBuilder().setMetadata(ArtworkMetadata.newBuilder().setMovieId(movieId).setDeclaredSizeBytes(file.size)).build(),
            )
            file.stream().use { input ->
                val buffer = ByteArray(CHUNK_BYTES)
                while (!result.isDone) { // the server may already have rejected the upload (for example: too large)
                    val n = input.read(buffer)
                    if (n < 0) break
                    waitUntilReady(request, result)
                    if (result.isDone) break
                    request.onNext(UploadArtworkRequest.newBuilder().setChunk(ByteString.copyFrom(buffer, 0, n)).build())
                }
            }
            if (!result.isDone) request.onCompleted()
            result.get(60, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } catch (e: TimeoutException) {
            request.cancel("gateway timeout", e)
            throw Status.DEADLINE_EXCEEDED.asRuntimeException()
        } catch (e: Exception) {
            // Reading the file failed or the thread was interrupted: tell movie-service to discard the upload.
            if (e is InterruptedException) Thread.currentThread().interrupt() // keep the interrupt visible to callers
            request.cancel("gateway failed", e)
            log.error("Artwork upload failed in the gateway", e)
            throw Status.INTERNAL.asRuntimeException()
        }
    }

    /** Honours gRPC flow control, so a slow movie-service slows the read instead of filling memory. */
    private fun waitUntilReady(request: ClientCallStreamObserver<*>, result: CompletableFuture<*>) {
        val limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!request.isReady && !result.isDone) {
            if (System.nanoTime() > limit) throw TimeoutException()
            Thread.sleep(2)
        }
    }

    companion object {
        const val CHUNK_BYTES = 64 * 1024
    }
}

/**
 * Serves artwork bytes (ADR-007). URLs carry a version (`?v=`), so responses are cached for a year and
 * a replaced image gets a new URL. A matching `If-None-Match` is answered with 304 before any byte is read.
 */
@RestController
class ArtworkController(private val downstream: Downstream) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/artwork/{movieId}/{variant}")
    fun artwork(
        @PathVariable movieId: String,
        @PathVariable variant: String,
        @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?,
        @RequestParam(name = "v", required = false) v: String?,
        response: HttpServletResponse,
    ) {
        val kind = when (variant) {
            "thumbnail" -> ArtworkVariant.ARTWORK_VARIANT_THUMBNAIL
            "original" -> ArtworkVariant.ARTWORK_VARIANT_ORIGINAL
            else -> return notFound(response)
        }
        val id = try { UUID.fromString(movieId).toString() } catch (e: IllegalArgumentException) { return notFound(response) }

        // Cancelling the context stops the downstream stream when we leave early (304, error, client gone).
        val context = Context.current().withCancellation()
        val previous = context.attach()
        try {
            val stream = downstream.movieDownload().downloadArtwork(DownloadArtworkRequest.newBuilder().setMovieId(id).setVariant(kind).build())
            val header = try {
                stream.next().takeIf { it.hasHeader() }?.header ?: throw Status.INTERNAL.asRuntimeException()
            } catch (e: StatusRuntimeException) {
                return fail(response, e)
            }
            val etag = "\"${header.version}-$variant\""
            response.setHeader(HttpHeaders.ETAG, etag)
            // Only a URL that carries the current version may be cached for a year; a bare or old URL must revalidate,
            // otherwise a replaced picture would not show (S-303.AC6).
            val versioned = v == header.version
            response.setHeader(HttpHeaders.CACHE_CONTROL, if (versioned) "public, max-age=31536000, immutable" else "no-cache")
            if (ifNoneMatch != null && ifNoneMatch.split(',').any { it.trim() == etag || it.trim() == "*" }) {
                response.status = HttpStatus.NOT_MODIFIED.value()
                return
            }
            response.contentType = header.contentType
            response.setContentLengthLong(header.sizeBytes)
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline")
            val out = response.outputStream
            try {
                while (stream.hasNext()) stream.next().takeIf { it.hasChunk() }?.chunk?.writeTo(out)
            } catch (e: Exception) {
                // The status line is already sent; the client sees a short body and retries.
                log.warn("Artwork stream ended early: {}", e.javaClass.simpleName)
            }
        } finally {
            context.detach(previous)
            context.cancel(null)
        }
    }

    private fun notFound(response: HttpServletResponse) { response.status = HttpStatus.NOT_FOUND.value() }

    private fun fail(response: HttpServletResponse, e: StatusRuntimeException) {
        response.status = when (e.status.code) {
            Status.Code.NOT_FOUND, Status.Code.INVALID_ARGUMENT -> HttpStatus.NOT_FOUND.value()
            Status.Code.UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE.value()
            Status.Code.DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT.value()
            else -> HttpStatus.INTERNAL_SERVER_ERROR.value()
        }
    }
}
