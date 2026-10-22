package com.moviecatalog.people

import com.moviecatalog.contracts.common.v1.BlockingMovie
import com.moviecatalog.contracts.movie.v1.CheckPersonCreditsRequest
import com.moviecatalog.contracts.movie.v1.MovieServiceGrpc
import com.moviecatalog.support.CorrelationClientInterceptor
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.UUID
import java.util.concurrent.TimeUnit

/** What people-service needs to know from movie-service before deleting a person (D8). */
data class CreditCheck(val creditCount: Long, val blockingMovies: List<BlockingMovie>)

/** Thrown when the check could not be completed. Deleting then fails closed (BR-05, NFR-REL-02). */
class CreditCheckUnavailable(cause: Throwable) : RuntimeException("credit check unavailable", cause)

fun interface MovieCredits {
    fun check(personId: UUID): CreditCheck
}

/** The only service-to-service call in the system besides the gateway's: always with a deadline. */
class GrpcMovieCredits(private val channel: ManagedChannel, private val deadlineMs: Long) : MovieCredits {
    override fun check(personId: UUID): CreditCheck = try {
        val response = MovieServiceGrpc.newBlockingStub(channel)
            .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
            .checkPersonCredits(CheckPersonCreditsRequest.newBuilder().setPersonId(personId.toString()).setMaxMovies(10).build())
        CreditCheck(
            response.creditCount,
            response.blockingMoviesList.map {
                BlockingMovie.newBuilder().setId(it.id).setTitle(it.title).setReleaseYear(it.releaseYear).build()
            },
        )
    } catch (e: StatusRuntimeException) {
        if (e.status.code == Status.Code.UNAVAILABLE || e.status.code == Status.Code.DEADLINE_EXCEEDED) {
            throw CreditCheckUnavailable(e)
        }
        throw e
    }
}

@Configuration(proxyBeanMethods = false)
class MovieClientConfig {
    @Bean(destroyMethod = "shutdownNow")
    fun movieChannel(@Value("\${movie-service.target}") target: String): ManagedChannel =
        NettyChannelBuilder.forTarget(target).usePlaintext().intercept(CorrelationClientInterceptor()).build()

    @Bean
    fun movieCredits(movieChannel: ManagedChannel, @Value("\${movie-service.deadline-ms}") deadlineMs: Long): MovieCredits =
        GrpcMovieCredits(movieChannel, deadlineMs)
}
