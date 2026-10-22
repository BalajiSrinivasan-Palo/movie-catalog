package com.moviecatalog.support

import io.grpc.BindableService
import io.grpc.Server
import io.grpc.ServerInterceptors
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.protobuf.services.HealthStatusManager
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/**
 * Runs the gRPC server next to the Spring context (ADR-004: plain grpc-java, no framework
 * integration). Bound to 127.0.0.1 by default: gRPC is plaintext in the local setup (NFR-SEC-08). Only the
 * container setup (docker/) changes it, through `grpc.server.bind-address`, because services must reach each other there.
 */
class GrpcServerLifecycle(
    private val port: Int,
    private val maxInboundMessageBytes: Int,
    private val services: List<BindableService>,
    val health: HealthStatusManager,
    private val bindAddress: String = "127.0.0.1",
) : SmartLifecycle {
    private var server: Server? = null

    /** The bound port; differs from the configured one when that is 0 (used by tests). */
    val boundPort: Int get() = server?.port ?: -1

    override fun start() {
        val builder = NettyServerBuilder
            .forAddress(InetSocketAddress(bindAddress, port))
            .maxInboundMessageSize(maxInboundMessageBytes)
            .addService(health.healthService)
        services.forEach { builder.addService(ServerInterceptors.intercept(it, CorrelationServerInterceptor())) }
        server = builder.build().start()
    }

    override fun stop() {
        val s = server ?: return
        health.enterTerminalState()
        s.shutdown()
        if (!s.awaitTermination(5, TimeUnit.SECONDS)) s.shutdownNow()
        server = null
    }

    override fun isRunning(): Boolean = server?.isShutdown == false
}

@Configuration(proxyBeanMethods = false)
class GrpcConfig {
    @Bean
    fun grpcServerLifecycle(
        @Value("\${grpc.server.port}") port: Int,
        @Value("\${grpc.server.max-inbound-message-bytes}") maxBytes: Int,
        @Value("\${grpc.server.bind-address:127.0.0.1}") bindAddress: String,
        services: ObjectProvider<BindableService>,
    ): GrpcServerLifecycle =
        GrpcServerLifecycle(port, maxBytes, services.orderedStream().toList(), HealthStatusManager(), bindAddress)
}
