package com.moviecatalog.support

import com.google.protobuf.Timestamp
import com.moviecatalog.contracts.common.v1.Date
import com.moviecatalog.contracts.common.v1.PageInfo
import com.moviecatalog.contracts.common.v1.PageRequest
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val log = LoggerFactory.getLogger("com.moviecatalog.support.Common")
private val UUID_SHAPE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/** A well-formed UUID or an `id.invalid` validation error (BRD section 7, "Identifier"). */
fun parseUuid(value: String, field: String = "id"): UUID {
    if (!UUID_SHAPE.matches(value)) throw GrpcErrors.invalidArgument(field to "id.invalid")
    return UUID.fromString(value)
}

/** Makes `%`, `_` and `\` literal in a SQL LIKE pattern that uses `ESCAPE '\'` (NFR-SEC-04). */
fun escapeLike(raw: String): String = raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

fun Instant.toProto(): Timestamp = Timestamp.newBuilder().setSeconds(epochSecond).setNanos(nano).build()

fun LocalDate.toProto(): Date = Date.newBuilder().setYear(year).setMonth(monthValue).setDay(dayOfMonth).build()

/** Null when the message is not a real calendar date. */
fun Date.toLocalDateOrNull(): LocalDate? = try { LocalDate.of(year, month, day) } catch (e: DateTimeException) { null }

/** A validated page request: zero-based index and a size of 1..100 (default 20), BRD section 7 "Paging". */
data class PageSpec(val index: Int, val size: Int) {
    val offset: Int get() = index * size

    fun info(total: Long): PageInfo = PageInfo.newBuilder().setPage(index).setSize(size).setTotalElements(total)
        .setTotalPages(if (total == 0L) 0 else ((total + size - 1) / size).toInt()).build()

    companion object {
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 100

        fun of(request: PageRequest?): PageSpec {
            val index = request?.page ?: 0
            val size = request?.size?.takeIf { it != 0 } ?: DEFAULT_SIZE
            if (index < 0 || size !in 1..MAX_SIZE) throw GrpcErrors.invalidArgument("page" to "paging.invalid")
            return PageSpec(index, size)
        }
    }
}

/**
 * Runs one unary call and always answers it. A [StatusRuntimeException] is passed through as is;
 * anything else is logged in full and returned as a bare INTERNAL, so no internals leave the service (NFR-UX-06).
 */
fun <T> StreamObserver<T>.reply(block: () -> T) {
    val result = try {
        block()
    } catch (e: StatusRuntimeException) {
        onError(e)
        return
    } catch (e: Exception) {
        log.error("Unexpected failure while handling a call", e)
        onError(Status.INTERNAL.withDescription("internal").asRuntimeException())
        return
    }
    onNext(result)
    onCompleted()
}
