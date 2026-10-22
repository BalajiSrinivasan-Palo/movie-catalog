package com.moviecatalog.gateway

import com.moviecatalog.contracts.movie.v1.MovieServiceGrpc
import com.moviecatalog.contracts.movie.v1.GetMovieRequest
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.health.v1.HealthCheckResponse.ServingStatus
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.protobuf.services.HealthStatusManager
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** S-004 AC1 and AC4: health probing and deadlines against real (in-process or closed-port) channels. */
class HealthTest {
    private val closers = mutableListOf<() -> Unit>()

    @AfterTest
    fun cleanup() = closers.reversed().forEach { it() }

    private fun upChannel(status: ServingStatus = ServingStatus.SERVING): ManagedChannel {
        val health = HealthStatusManager().also { it.setStatus("", status) }
        val name = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder.forName(name).directExecutor().addService(health.healthService).build().start()
        val channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        closers += { channel.shutdownNow(); server.shutdownNow() }
        return channel
    }

    private fun deadChannel(): ManagedChannel {
        val port = ServerSocket(0).use { it.localPort } // nothing listens here once the socket is closed
        val channel = NettyChannelBuilder.forAddress("127.0.0.1", port).usePlaintext().build()
        closers += { channel.shutdownNow() }
        return channel
    }

    private fun controller(movie: ManagedChannel, people: ManagedChannel) =
        HealthController(Downstream(movie, people, Deadlines(defaultMs = 1_000, healthMs = 1_000)))

    @Test
    fun `both services up`() {
        val result = controller(upChannel(), upChannel()).health()
        assertEquals(listOf("movie-service" to "UP", "people-service" to "UP"), result.map { it.service to it.status })
    }

    @Test
    fun `one service down is reported without failing the query`() {
        val result = controller(upChannel(), deadChannel()).health()
        assertEquals("UP", result[0].status)
        assertEquals("DOWN", result[1].status)
        assertEquals("unreachable", result[1].detail)
    }

    @Test
    fun `both down and a not-serving service`() {
        val down = controller(deadChannel(), deadChannel()).health()
        assertTrue(down.all { it.status == "DOWN" })
        val notServing = controller(upChannel(ServingStatus.NOT_SERVING), upChannel()).health()
        assertEquals("DOWN", notServing[0].status)
        assertEquals("not serving", notServing[0].detail)
    }

    @Test
    fun `a stopped service yields UNAVAILABLE quickly, not a hang`() {
        val downstream = Downstream(deadChannel(), deadChannel(), Deadlines(defaultMs = 1_000))
        val elapsed = measureTimeMillis {
            val e = assertFailsWith<StatusRuntimeException> {
                downstream.movie().getMovie(GetMovieRequest.newBuilder().setId("3f2c1a9e-7b44-4d1e-9a10-0c8d6e5f4a3b").build())
            }
            assertTrue(e.status.code in setOf(Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED), e.status.toString())
        }
        assertTrue(elapsed < 2_500, "took ${elapsed}ms")
    }

    @Test
    fun `stub carries a deadline`() {
        val downstream = Downstream(upChannel(), upChannel(), Deadlines(defaultMs = 5_000))
        val remaining = downstream.movie().callOptions.deadline!!.timeRemaining(TimeUnit.MILLISECONDS)
        assertTrue(remaining in 1..5_000)
        assertTrue(MovieServiceGrpc.newBlockingStub(upChannel()).callOptions.deadline == null) // proves the helper is what adds it
    }
}
