package com.moviecatalog.gateway

import io.grpc.Channel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse.ServingStatus
import io.grpc.health.v1.HealthGrpc
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.util.concurrent.TimeUnit

data class ServiceHealth(val service: String, val status: String, val detail: String?)

/** Asks each backend's standard `grpc.health.v1` service; a failure is reported as DOWN, never thrown (S-004 AC1). */
@Controller
class HealthController(private val downstream: Downstream) {
    @QueryMapping
    fun health(): List<ServiceHealth> = listOf(
        probe("movie-service", downstream.movieChannel),
        probe("people-service", downstream.peopleChannel),
    )

    private fun probe(name: String, channel: Channel): ServiceHealth = try {
        val response = HealthGrpc.newBlockingStub(channel)
            .withDeadlineAfter(downstream.deadlines.healthMs, TimeUnit.MILLISECONDS)
            .check(HealthCheckRequest.getDefaultInstance())
        if (response.status == ServingStatus.SERVING) ServiceHealth(name, "UP", null)
        else ServiceHealth(name, "DOWN", "not serving")
    } catch (e: StatusRuntimeException) {
        ServiceHealth(name, "DOWN", if (e.status.code == Status.Code.DEADLINE_EXCEEDED) "timeout" else "unreachable")
    }
}
