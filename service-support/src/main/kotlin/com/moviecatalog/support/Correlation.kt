package com.moviecatalog.support

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.ForwardingServerCallListener
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import org.slf4j.MDC

const val CORRELATION_MDC_KEY = "correlationId"
val CORRELATION_METADATA_KEY: Metadata.Key<String> =
    Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER)

private val VALID_ID = Regex("^[A-Za-z0-9-]{8,64}$")

/** Accepts only well-formed ids, so a caller cannot inject log lines or huge values (NFR-OBS-02). */
fun isValidCorrelationId(value: String?): Boolean = value != null && VALID_ID.matches(value)

/** Puts the caller's correlation id into the logging context for the whole call (S-004 AC2). */
class CorrelationServerInterceptor : ServerInterceptor {
    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val id = headers.get(CORRELATION_METADATA_KEY)?.takeIf(::isValidCorrelationId)
        val delegate = next.startCall(call, headers)
        return object : ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(delegate) {
            private fun <T> withMdc(block: () -> T): T {
                if (id == null) return block()
                MDC.put(CORRELATION_MDC_KEY, id)
                try { return block() } finally { MDC.remove(CORRELATION_MDC_KEY) }
            }
            override fun onMessage(message: ReqT) = withMdc { super.onMessage(message) }
            override fun onHalfClose() = withMdc { super.onHalfClose() }
            override fun onCancel() = withMdc { super.onCancel() }
            override fun onComplete() = withMdc { super.onComplete() }
            override fun onReady() = withMdc { super.onReady() }
        }
    }
}

/** Forwards the current request's correlation id as gRPC metadata. */
class CorrelationClientInterceptor : ClientInterceptor {
    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        options: CallOptions,
        next: Channel,
    ): ClientCall<ReqT, RespT> {
        val id = MDC.get(CORRELATION_MDC_KEY)
        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, options)) {
            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                if (id != null) headers.put(CORRELATION_METADATA_KEY, id)
                super.start(responseListener, headers)
            }
        }
    }
}

