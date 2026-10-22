package com.moviecatalog.gateway

import com.moviecatalog.contracts.movie.v1.MovieServiceGrpc
import com.moviecatalog.contracts.people.v1.PeopleServiceGrpc
import com.moviecatalog.support.CorrelationClientInterceptor
import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

/** Deadlines per call kind, in milliseconds (architecture section 6). Every downstream call has one (NFR-SEC-06). */
data class Deadlines(val defaultMs: Long = 3_000, val uploadMs: Long = 30_000, val healthMs: Long = 2_000)

/** The only place that creates stubs, so every call carries a deadline. */
class Downstream(val movieChannel: Channel, val peopleChannel: Channel, val deadlines: Deadlines) {
    fun movie(): MovieServiceGrpc.MovieServiceBlockingStub =
        MovieServiceGrpc.newBlockingStub(movieChannel).withDeadlineAfter(deadlines.defaultMs, TimeUnit.MILLISECONDS)

    /** Upload stub: the longer upload deadline covers the whole streaming call. */
    fun movieUpload(): MovieServiceGrpc.MovieServiceStub =
        MovieServiceGrpc.newStub(movieChannel).withDeadlineAfter(deadlines.uploadMs, TimeUnit.MILLISECONDS)

    /** Download stub: one deadline for the whole stream, which can be an image of several megabytes. */
    fun movieDownload(): MovieServiceGrpc.MovieServiceBlockingStub =
        MovieServiceGrpc.newBlockingStub(movieChannel).withDeadlineAfter(deadlines.uploadMs, TimeUnit.MILLISECONDS)

    fun people(): PeopleServiceGrpc.PeopleServiceBlockingStub =
        PeopleServiceGrpc.newBlockingStub(peopleChannel).withDeadlineAfter(deadlines.defaultMs, TimeUnit.MILLISECONDS)
}

@Configuration(proxyBeanMethods = false)
class DownstreamConfig {
    @Bean(destroyMethod = "shutdownNow")
    fun movieChannel(
        @Value("\${downstream.movie.target}") target: String,
        @Value("\${downstream.max-inbound-message-bytes}") maxBytes: Int,
    ): ManagedChannel = channel(target, maxBytes)

    @Bean(destroyMethod = "shutdownNow")
    fun peopleChannel(
        @Value("\${downstream.people.target}") target: String,
        @Value("\${downstream.max-inbound-message-bytes}") maxBytes: Int,
    ): ManagedChannel = channel(target, maxBytes)

    @Bean
    fun downstream(
        movieChannel: ManagedChannel,
        peopleChannel: ManagedChannel,
        @Value("\${downstream.deadline-ms.default}") defaultMs: Long,
        @Value("\${downstream.deadline-ms.upload}") uploadMs: Long,
        @Value("\${downstream.deadline-ms.health}") healthMs: Long,
    ) = Downstream(movieChannel, peopleChannel, Deadlines(defaultMs, uploadMs, healthMs))

    // Plaintext is acceptable for the local setup only (NFR-SEC-08).
    private fun channel(target: String, maxBytes: Int): ManagedChannel = NettyChannelBuilder.forTarget(target)
        .usePlaintext().maxInboundMessageSize(maxBytes).intercept(CorrelationClientInterceptor()).build()
}
