package com.moviecatalog.gateway

import com.moviecatalog.support.CORRELATION_MDC_KEY
import com.moviecatalog.support.isValidCorrelationId
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.UUID

const val CORRELATION_HEADER = "X-Correlation-Id"

/** Required on multipart uploads. A browser must preflight a request with a custom header, which keeps other sites from posting files (CSRF). */
const val PREFLIGHT_HEADER = "Apollo-Require-Preflight"

/** Accepts a well-formed `X-Correlation-Id` or creates one, and exposes it to logs and gRPC calls (S-004 AC2). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val supplied = request.getHeader(CORRELATION_HEADER)
        val id = if (isValidCorrelationId(supplied)) supplied else UUID.randomUUID().toString()
        MDC.put(CORRELATION_MDC_KEY, id)
        response.setHeader(CORRELATION_HEADER, id)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(CORRELATION_MDC_KEY)
        }
    }
}

/** Conservative response headers for an API that serves JSON and image bytes (S-004 AC5). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class SecurityHeadersFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("X-Frame-Options", "DENY")
        response.setHeader("Referrer-Policy", "no-referrer")
        chain.doFilter(request, response)
    }
}

/** CORS is limited to the one configured frontend origin (NFR-SEC-05). */
@Configuration(proxyBeanMethods = false)
class CorsConfig(@Value("\${catalog.frontend-origin}") private val origin: String) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/graphql")
            .allowedOrigins(origin).allowedMethods("POST", "OPTIONS")
            .allowedHeaders("Content-Type", CORRELATION_HEADER, PREFLIGHT_HEADER).exposedHeaders(CORRELATION_HEADER).maxAge(600)
        registry.addMapping("/artwork/**")
            .allowedOrigins(origin).allowedMethods("GET", "OPTIONS").maxAge(600)
    }
}

/**
 * Limits the size of plain JSON request bodies (NFR-SEC-05). Without it one huge `/graphql` POST would be read into
 * memory. Multipart uploads have their own, larger limits (see the multipart settings) and are not touched here.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class JsonBodyLimitFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val type = request.contentType?.lowercase().orEmpty()
        if (request.method != "POST" || !type.startsWith("application/json")) return chain.doFilter(request, response)
        if (request.contentLengthLong > MAX_JSON_BODY_BYTES) return tooLarge(response)
        // A chunked body has no declared length: count what is actually read.
        chain.doFilter(if (request.contentLengthLong < 0) CountingRequest(request) else request, response)
    }

    private fun tooLarge(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE
        response.contentType = "application/json"
        response.writer.write("""{"data":null,"errors":[{"message":"The request is too large.","extensions":{"code":"VALIDATION_FAILED"}}]}""")
    }

    private class CountingRequest(request: HttpServletRequest) : jakarta.servlet.http.HttpServletRequestWrapper(request) {
        override fun getInputStream(): jakarta.servlet.ServletInputStream {
            val inner = super.getInputStream()
            return object : jakarta.servlet.ServletInputStream() {
                private var count = 0L
                private fun add(n: Int): Int {
                    if (n > 0) count += n
                    if (count > MAX_JSON_BODY_BYTES) throw java.io.IOException("request body too large")
                    return n
                }
                override fun read(): Int = inner.read().also { if (it >= 0) add(1) }
                override fun read(b: ByteArray, off: Int, len: Int): Int = add(inner.read(b, off, len))
                override fun isFinished() = inner.isFinished
                override fun isReady() = inner.isReady
                override fun setReadListener(listener: jakarta.servlet.ReadListener?) = inner.setReadListener(listener)
            }
        }
    }

    companion object {
        const val MAX_JSON_BODY_BYTES = 64L * 1024
    }
}
