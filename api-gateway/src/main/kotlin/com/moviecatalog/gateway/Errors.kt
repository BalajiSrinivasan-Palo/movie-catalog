package com.moviecatalog.gateway

import com.moviecatalog.support.CORRELATION_MDC_KEY
import com.moviecatalog.support.GrpcErrors
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component
import java.util.UUID

/** A validation failure found by the gateway itself (for example `id.invalid`, `credit.person.not_found`). */
class GatewayValidationException(val violations: List<Pair<String, String>>) : RuntimeException("validation.failed") {
    constructor(field: String, messageKey: String) : this(listOf(field to messageKey))
}

/** Parses an id argument; a malformed id is a validation error, not a server error (S-004 AC7). */
fun parseId(value: String, field: String = "id"): UUID =
    try { UUID.fromString(value) } catch (e: IllegalArgumentException) {
        throw GatewayValidationException(field, "id.invalid")
    }

/** The GraphQL shape of a failure (ADR-005). `messageKey` is the stable key the frontend maps to text. */
data class MappedError(
    val code: String,
    val message: String,
    val messageKey: String? = null,
    val fieldErrors: List<Map<String, String>> = emptyList(),
    val blockingMovies: List<Map<String, Any>> = emptyList(),
)

object GrpcErrorMapper {
    private val messages = mapOf(
        "VALIDATION_FAILED" to "Some of the information is not valid.",
        "NOT_FOUND" to "The item could not be found.",
        "CONFLICT" to "This conflicts with existing data.",
        "UNAVAILABLE" to "The service is temporarily unavailable. Please try again.",
        "INTERNAL" to "Something went wrong. Please try again.",
    )

    private fun of(code: String, key: String? = null, fields: List<Map<String, String>> = emptyList(), blocking: List<Map<String, Any>> = emptyList()) =
        MappedError(code, messages.getValue(code), key, fields, blocking)

    /** Returns null for a cancelled call, which is not reported (architecture section 6). */
    fun map(t: Throwable): MappedError? = when (t) {
        is GatewayValidationException -> of(
            "VALIDATION_FAILED", fields = t.violations.map { (f, k) -> mapOf("field" to f, "messageKey" to k) },
        )
        is StatusRuntimeException -> mapStatus(t)
        else -> of("INTERNAL")
    }

    private fun mapStatus(e: StatusRuntimeException): MappedError? {
        val detail = GrpcErrors.detailOf(e)
        val key = detail?.code?.takeIf { it.isNotBlank() }
        return when (e.status.code) {
            Status.Code.INVALID_ARGUMENT -> of(
                "VALIDATION_FAILED", key,
                detail?.violationsList.orEmpty().map { mapOf("field" to it.field, "messageKey" to it.messageKey) },
            )
            Status.Code.NOT_FOUND -> of("NOT_FOUND", key)
            Status.Code.ALREADY_EXISTS -> of("CONFLICT", key)
            Status.Code.FAILED_PRECONDITION -> of(
                "CONFLICT", key,
                blocking = detail?.blockingMoviesList.orEmpty().map {
                    mapOf("id" to it.id, "title" to it.title, "releaseYear" to it.releaseYear)
                },
            )
            Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED -> of("UNAVAILABLE", key)
            Status.Code.CANCELLED -> null
            else -> of("INTERNAL")
        }
    }
}

@Component
class GraphQlErrorResolver : DataFetcherExceptionResolverAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? {
        val mapped = GrpcErrorMapper.map(ex) ?: return null
        if (mapped.code == "INTERNAL") log.error("Unexpected failure in {}", env.field.name, ex) // detail stays in the log only
        val extensions = linkedMapOf<String, Any>("code" to mapped.code)
        mapped.messageKey?.let { extensions["messageKey"] = it }
        if (mapped.fieldErrors.isNotEmpty()) extensions["fieldErrors"] = mapped.fieldErrors
        if (mapped.blockingMovies.isNotEmpty()) extensions["blockingMovies"] = mapped.blockingMovies
        MDC.get(CORRELATION_MDC_KEY)?.let { extensions["correlationId"] = it }
        return GraphqlErrorBuilder.newError(env).message(mapped.message)
            .errorType(
                when (mapped.code) {
                    "VALIDATION_FAILED" -> ErrorType.BAD_REQUEST
                    "NOT_FOUND" -> ErrorType.NOT_FOUND
                    "CONFLICT" -> ErrorType.BAD_REQUEST
                    "UNAVAILABLE" -> ErrorType.INTERNAL_ERROR
                    else -> ErrorType.INTERNAL_ERROR
                },
            )
            .extensions(extensions).build()
    }
}
