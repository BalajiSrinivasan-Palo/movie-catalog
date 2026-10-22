package com.moviecatalog.support

import com.google.protobuf.Any
import com.google.rpc.Code
import com.moviecatalog.contracts.common.v1.BlockingMovie
import com.moviecatalog.contracts.common.v1.ErrorDetail
import com.moviecatalog.contracts.common.v1.FieldViolation
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.StatusProto

/**
 * Builds failures the way ADR-005 requires: a standard gRPC status whose details carry one
 * `ErrorDetail`. The status message is a stable key, never internal text (NFR-UX-06).
 */
object GrpcErrors {
    fun invalidArgument(vararg violations: Pair<String, String>): StatusRuntimeException = build(
        Code.INVALID_ARGUMENT, "validation.failed",
        violations.map { (field, key) -> FieldViolation.newBuilder().setField(field).setMessageKey(key).build() },
    )

    fun notFound(code: String): StatusRuntimeException = build(Code.NOT_FOUND, code)

    fun alreadyExists(code: String): StatusRuntimeException = build(Code.ALREADY_EXISTS, code)

    /** A dependency this service needs is down; [code] says which operation could not be completed. */
    fun unavailable(code: String): StatusRuntimeException = build(Code.UNAVAILABLE, code)

    fun failedPrecondition(
        code: String,
        blocking: List<BlockingMovie> = emptyList(),
        metadata: Map<String, String> = emptyMap(),
    ): StatusRuntimeException = build(Code.FAILED_PRECONDITION, code, emptyList(), blocking, metadata)

    private fun build(
        status: Code,
        code: String,
        violations: List<FieldViolation> = emptyList(),
        blocking: List<BlockingMovie> = emptyList(),
        metadata: Map<String, String> = emptyMap(),
    ): StatusRuntimeException {
        val detail = ErrorDetail.newBuilder()
            .setCode(code).addAllViolations(violations).addAllBlockingMovies(blocking).putAllMetadata(metadata).build()
        val rpcStatus = com.google.rpc.Status.newBuilder()
            .setCode(status.number).setMessage(code).addDetails(Any.pack(detail)).build()
        return StatusProto.toStatusRuntimeException(rpcStatus)
    }

    /** Reads the `ErrorDetail` back out of a failed call, or null when the peer sent none. */
    fun detailOf(e: Throwable): ErrorDetail? {
        val status = StatusProto.fromThrowable(e) ?: return null
        return status.detailsList.firstOrNull { it.`is`(ErrorDetail::class.java) }?.unpack(ErrorDetail::class.java)
    }
}
