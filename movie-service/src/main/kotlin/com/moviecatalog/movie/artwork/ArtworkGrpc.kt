package com.moviecatalog.movie.artwork

import com.google.protobuf.ByteString
import com.moviecatalog.contracts.movie.v1.ArtworkFileHeader
import com.moviecatalog.contracts.movie.v1.ArtworkInfo
import com.moviecatalog.contracts.movie.v1.ArtworkVariant
import com.moviecatalog.contracts.movie.v1.DownloadArtworkRequest
import com.moviecatalog.contracts.movie.v1.DownloadArtworkResponse
import com.moviecatalog.contracts.movie.v1.UploadArtworkRequest
import com.moviecatalog.support.GrpcErrors
import com.moviecatalog.support.parseUuid
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.ServerCallStreamObserver
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.util.UUID

fun Artwork.toInfo(): ArtworkInfo = ArtworkInfo.newBuilder()
    .setId(id.toString()).setContentType(contentType).setSizeBytes(sizeBytes)
    .setWidth(width).setHeight(height).setVersion(version).build()

/**
 * Receives one upload call: metadata first, then chunks, then completion. Each chunk goes straight to
 * disk. Any failure, protocol error or cancellation removes what was written (BR-11).
 */
class ArtworkUploadObserver(
    private val reply: StreamObserver<ArtworkInfo>,
    private val ingest: ArtworkIngest,
    private val service: ArtworkService,
) : StreamObserver<UploadArtworkRequest> {
    private val log = LoggerFactory.getLogger(javaClass)
    private var movieId: UUID? = null
    private var session: ArtworkIngest.Session? = null
    private var finished = false

    override fun onNext(message: UploadArtworkRequest) {
        if (finished) return
        try {
            when (message.payloadCase) {
                UploadArtworkRequest.PayloadCase.METADATA -> {
                    if (movieId != null) throw protocol()
                    val id = parseUuid(message.metadata.movieId, "movieId")
                    service.requireMovie(id)
                    movieId = id
                    session = ingest.begin()
                }
                UploadArtworkRequest.PayloadCase.CHUNK -> (session ?: throw protocol()).write(message.chunk)
                else -> throw protocol()
            }
        } catch (e: Throwable) {
            fail(e)
        }
    }

    override fun onCompleted() {
        if (finished) return
        try {
            val id = movieId
            val open = session ?: throw protocol()
            val prepared = open.finish()
            val saved = service.replace(id!!, prepared)
            finished = true
            reply.onNext(saved.toInfo())
            reply.onCompleted()
        } catch (e: Throwable) {
            fail(e)
        }
    }

    /** The client cancelled or the connection broke: nobody is listening, so only clean up. */
    override fun onError(t: Throwable) {
        finished = true
        session?.abort()
    }

    private fun protocol(): StatusRuntimeException = GrpcErrors.invalidArgument("file" to "artwork.protocol")

    private fun fail(e: Throwable) {
        finished = true
        session?.abort()
        if (e is StatusRuntimeException) {
            reply.onError(e)
        } else {
            log.error("Unexpected failure while receiving artwork", e)
            reply.onError(Status.INTERNAL.withDescription("internal").asRuntimeException())
        }
    }
}

/** Streams a stored file as a header followed by chunks, honouring the client's flow control. */
fun streamArtwork(service: ArtworkService, request: DownloadArtworkRequest, observer: StreamObserver<DownloadArtworkResponse>) {
    val log = LoggerFactory.getLogger("ArtworkDownload")
    try {
        val variant = if (request.variant == ArtworkVariant.ARTWORK_VARIANT_THUMBNAIL) Variant.THUMBNAIL else Variant.ORIGINAL
        val file = service.fileFor(parseUuid(request.movieId, "movieId"), variant)
        observer.onNext(
            DownloadArtworkResponse.newBuilder().setHeader(
                ArtworkFileHeader.newBuilder().setContentType(file.contentType).setSizeBytes(file.sizeBytes).setVersion(file.version),
            ).build(),
        )
        Files.newInputStream(file.path).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                if ((observer as? ServerCallStreamObserver<*>)?.isCancelled == true) return
                val n = input.read(buffer)
                if (n < 0) break
                observer.onNext(DownloadArtworkResponse.newBuilder().setChunk(ByteString.copyFrom(buffer, 0, n)).build())
            }
        }
        observer.onCompleted()
    } catch (e: StatusRuntimeException) {
        observer.onError(e)
    } catch (e: java.nio.file.NoSuchFileException) {
        // The artwork was replaced between reading the row and opening the file.
        observer.onError(GrpcErrors.notFound("artwork.not_found"))
    } catch (e: Exception) {
        log.error("Unexpected failure while streaming artwork", e)
        observer.onError(Status.INTERNAL.withDescription("internal").asRuntimeException())
    }
}
