package com.moviecatalog.support

import com.moviecatalog.contracts.common.v1.BlockingMovie
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.ServerInterceptors
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse
import io.grpc.health.v1.HealthGrpc
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.protobuf.services.HealthStatusManager
import io.grpc.stub.MetadataUtils
import io.grpc.stub.StreamObserver
import org.slf4j.MDC
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupportTest {
    private val closers = mutableListOf<() -> Unit>()

    @AfterTest
    fun cleanup() = closers.reversed().forEach { it() }

    @Test
    fun `correlation id syntax is validated`() {
        assertTrue(isValidCorrelationId("3f2c1a9e-7b44-4d1e-9a10-0c8d6e5f4a3b"))
        assertFalse(isValidCorrelationId(null))
        assertFalse(isValidCorrelationId("short"))
        assertFalse(isValidCorrelationId("has space and newline\n1234"))
        assertFalse(isValidCorrelationId("a".repeat(65)))
    }

    @Test
    fun `server interceptor puts the caller id into MDC during the call`() {
        val seen = AtomicReference<String?>()
        val impl = object : HealthGrpc.HealthImplBase() {
            override fun check(request: HealthCheckRequest, observer: StreamObserver<HealthCheckResponse>) {
                seen.set(MDC.get(CORRELATION_MDC_KEY))
                observer.onNext(HealthCheckResponse.newBuilder().setStatus(HealthCheckResponse.ServingStatus.SERVING).build())
                observer.onCompleted()
            }
        }
        val channel = inProcess(ServerInterceptors.intercept(impl, CorrelationServerInterceptor()))
        val headers = Metadata().apply { put(CORRELATION_METADATA_KEY, "11111111-2222-3333-4444-555555555555") }
        val stub = HealthGrpc.newBlockingStub(channel).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
        stub.check(HealthCheckRequest.getDefaultInstance())
        assertEquals("11111111-2222-3333-4444-555555555555", seen.get())
        assertNull(MDC.get(CORRELATION_MDC_KEY), "MDC must be cleared after the call")
    }

    @Test
    fun `server interceptor ignores a malformed id`() {
        val seen = AtomicReference<String?>("unset")
        val impl = object : HealthGrpc.HealthImplBase() {
            override fun check(request: HealthCheckRequest, observer: StreamObserver<HealthCheckResponse>) {
                seen.set(MDC.get(CORRELATION_MDC_KEY))
                observer.onNext(HealthCheckResponse.getDefaultInstance())
                observer.onCompleted()
            }
        }
        val channel = inProcess(ServerInterceptors.intercept(impl, CorrelationServerInterceptor()))
        val headers = Metadata().apply { put(CORRELATION_METADATA_KEY, "bad id") }
        HealthGrpc.newBlockingStub(channel).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
            .check(HealthCheckRequest.getDefaultInstance())
        assertNull(seen.get())
    }

    @Test
    fun `error helper round-trips ErrorDetail through the status`() {
        val e = GrpcErrors.invalidArgument("title" to "movie.title.invalid", "releaseYear" to "movie.year.invalid")
        assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
        val detail = assertNotNull(GrpcErrors.detailOf(e))
        assertEquals("validation.failed", detail.code)
        assertEquals(listOf("title", "releaseYear"), detail.violationsList.map { it.field })
        assertEquals("movie.title.invalid", detail.violationsList[0].messageKey)
    }

    @Test
    fun `failed precondition carries blocking movies and metadata`() {
        val movie = BlockingMovie.newBuilder().setId("m1").setTitle("Heat").setReleaseYear(1995).build()
        val e = GrpcErrors.failedPrecondition("person.has_credits", listOf(movie), mapOf("creditCount" to "1"))
        assertEquals(Status.Code.FAILED_PRECONDITION, e.status.code)
        val detail = assertNotNull(GrpcErrors.detailOf(e))
        assertEquals("Heat", detail.blockingMoviesList.single().title)
        assertEquals("1", detail.metadataMap["creditCount"])
    }

    @Test
    fun `not found and already exists use the standard codes`() {
        assertEquals(Status.Code.NOT_FOUND, GrpcErrors.notFound("movie.not_found").status.code)
        assertEquals(Status.Code.ALREADY_EXISTS, GrpcErrors.alreadyExists("movie.duplicate").status.code)
    }

    @Test
    fun `detailOf returns null for an error without details`() {
        assertNull(GrpcErrors.detailOf(Status.INTERNAL.asRuntimeException()))
        assertNull(GrpcErrors.detailOf(IllegalStateException("x")))
    }

    @Test
    fun `lifecycle serves health on a loopback port and enforces the inbound message limit`() {
        val health = HealthStatusManager()
        val lifecycle = GrpcServerLifecycle(0, 1024, emptyList(), health)
        lifecycle.start()
        closers += { lifecycle.stop() }
        assertTrue(lifecycle.isRunning)
        val channel = NettyChannelBuilder.forAddress("127.0.0.1", lifecycle.boundPort).usePlaintext().build()
        closers += { channel.shutdownNow() }
        val stub = HealthGrpc.newBlockingStub(channel)
        assertEquals(HealthCheckResponse.ServingStatus.SERVING, stub.check(HealthCheckRequest.getDefaultInstance()).status)
        val tooBig = HealthCheckRequest.newBuilder().setService("x".repeat(4096)).build()
        val failure = assertFailsWith<StatusRuntimeException> { stub.check(tooBig) }
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, failure.status.code)
        lifecycle.stop()
        assertFalse(lifecycle.isRunning)
    }

    private fun inProcess(service: io.grpc.ServerServiceDefinition): ManagedChannel {
        val name = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start()
        val channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        closers += { channel.shutdownNow(); server.shutdownNow() }
        return channel
    }
}
