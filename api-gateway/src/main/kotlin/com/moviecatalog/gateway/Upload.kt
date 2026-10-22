package com.moviecatalog.gateway

import jakarta.servlet.http.Part
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.graphql.server.WebGraphQlResponse
import org.springframework.graphql.server.webmvc.GraphQlHttpHandler
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.function.RequestPredicates
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.RouterFunctions
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse
import reactor.core.publisher.Mono
import java.io.InputStream
import org.slf4j.MDC
import com.moviecatalog.support.CORRELATION_MDC_KEY

const val UPLOADS_ATTRIBUTE = "catalog.uploads"
const val UPLOADS_CONTEXT_KEY = "catalog.uploads"

/** A file from a multipart request. The container keeps it in a temp file until the request ends. */
class UploadedFile(val size: Long, private val open: () -> InputStream) {
    fun stream(): InputStream = open()
}

/**
 * GraphQL multipart request spec (S-303), kept small: one `operations` JSON object, a `map`, and the files.
 * The route checks the shape, swaps each file in `variables` for a token, and hands the request to the normal
 * GraphQL handler, so validation, error mapping and limits are the same as for every other request.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET) // the GraphQL HTTP handler only exists in a servlet web app
class MultipartGraphQlRoute {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE) // must be tried before the standard GraphQL route, which would refuse multipart
    fun multipartGraphQl(handler: GraphQlHttpHandler): RouterFunction<ServerResponse> = RouterFunctions.route()
        .POST("/graphql", RequestPredicates.contentType(MediaType.MULTIPART_FORM_DATA)) { handle(handler, it) }
        .build()

    private fun handle(handler: GraphQlHttpHandler, request: ServerRequest): ServerResponse {
        if (request.headers().firstHeader(PREFLIGHT_HEADER) == null) {
            return ServerResponse.status(HttpStatus.FORBIDDEN).build()
        }
        val parts = try {
            request.multipartData()
        } catch (e: IllegalStateException) {
            return failure("artwork.too_large") // the container's size limit was hit
        } catch (e: Exception) {
            return failure("artwork.protocol")
        }
        val forwarded = try {
            rewrite(request, parts)
        } catch (e: BadMultipart) {
            return failure(e.key)
        }
        return handler.handleRequest(forwarded)
    }

    private class BadMultipart(val key: String) : RuntimeException(key)

    private fun rewrite(request: ServerRequest, parts: Map<String, List<Part>>): ServerRequest {
        val operations = parts["operations"]?.singleOrNull() ?: throw BadMultipart("artwork.protocol")
        val map = parts["map"]?.singleOrNull() ?: throw BadMultipart("artwork.protocol")
        val (operationsJson, fileMap) = try {
            LinkedHashMap(Json.parseObject(text(operations))) to Json.parseObject(text(map))
        } catch (e: Exception) {
            throw BadMultipart("artwork.protocol")
        }
        if (fileMap.size != 1) throw BadMultipart("artwork.protocol") // one file per request is all the app needs
        val uploads = LinkedHashMap<String, UploadedFile>()
        val variables = LinkedHashMap<String, Any?>()
        (operationsJson["variables"] as? Map<*, *>)?.forEach { (k, v) -> variables[k.toString()] = v }
        for ((key, targets) in fileMap) {
            val part = parts[key]?.singleOrNull() ?: throw BadMultipart("artwork.protocol")
            val paths = (targets as? List<*>)?.map { it.toString() } ?: throw BadMultipart("artwork.protocol")
            val name = paths.singleOrNull()?.removePrefix("variables.")?.takeIf { paths.single().startsWith("variables.") && '.' !in it && it.isNotEmpty() }
                ?: throw BadMultipart("artwork.protocol")
            val token = "upload:$key"
            uploads[token] = UploadedFile(part.size) { part.inputStream }
            variables[name] = token
        }
        operationsJson["variables"] = variables
        val body = Json.write(operationsJson).toByteArray(Charsets.UTF_8)
        return ServerRequest.from(request)
            .headers { h ->
                h.contentType = MediaType.APPLICATION_JSON
                h.contentLength = body.size.toLong()
            }
            .attribute(UPLOADS_ATTRIBUTE, uploads)
            .body(body)
            .build()
    }

    private fun text(part: Part): String = part.inputStream.use { String(it.readNBytes(MAX_JSON_BYTES), Charsets.UTF_8) }

    /** A GraphQL-shaped validation failure for problems found before GraphQL runs (ADR-005). */
    private fun failure(messageKey: String): ServerResponse {
        val mapped = GrpcErrorMapper.map(GatewayValidationException("file", messageKey))!!
        val extensions = linkedMapOf<String, Any>(
            "code" to mapped.code,
            "fieldErrors" to mapped.fieldErrors,
        )
        MDC.get(CORRELATION_MDC_KEY)?.let { extensions["correlationId"] = it }
        val body = mapOf("data" to null, "errors" to listOf(mapOf("message" to mapped.message, "extensions" to extensions)))
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(body)
    }

    companion object {
        const val MAX_JSON_BYTES = 64 * 1024
    }
}

/** Makes the uploaded files of the current request available to the `Upload` scalar. */
@Component
class UploadContextInterceptor : WebGraphQlInterceptor {
    override fun intercept(request: WebGraphQlRequest, chain: WebGraphQlInterceptor.Chain): Mono<WebGraphQlResponse> {
        val uploads = request.attributes[UPLOADS_ATTRIBUTE]
        if (uploads != null) {
            request.configureExecutionInput { _, builder -> builder.graphQLContext(mapOf<Any, Any>(UPLOADS_CONTEXT_KEY to uploads)).build() }
        }
        return chain.next(request)
    }
}

/**
 * Reads and writes the small JSON documents of the multipart protocol. It is written by hand because the
 * Spring Boot 4 JSON libraries moved (Jackson 3) and `BasicJsonParser` does not decode escapes such as `\n`,
 * which the frontend's multi-line GraphQL text contains. It follows RFC 8259: strings with all escapes
 * including surrogate pairs, numbers, true/false/null, nesting limited to 32 levels, nothing after the value.
 */
internal object Json {
    class JsonException(message: String) : RuntimeException(message)

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> =
        parse(text) as? Map<String, Any?> ?: throw JsonException("expected an object")

    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value(0)
        reader.skipWhitespace()
        if (!reader.atEnd()) throw JsonException("unexpected text after the value")
        return value
    }

    private class Reader(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun skipWhitespace() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++
        }

        private fun peek(): Char = if (i < s.length) s[i] else throw JsonException("unexpected end")

        private fun expect(c: Char) {
            if (peek() != c) throw JsonException("expected '$c' at $i")
            i++
        }

        private fun literal(word: String, result: Any?): Any? {
            if (!s.startsWith(word, i)) throw JsonException("bad literal at $i")
            i += word.length
            return result
        }

        fun value(depth: Int): Any? {
            if (depth > 32) throw JsonException("nested too deeply")
            skipWhitespace()
            return when (peek()) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> num()
            }
        }

        private fun obj(depth: Int): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') { i++; return result }
            while (true) {
                skipWhitespace()
                val key = str()
                skipWhitespace()
                expect(':')
                result[key] = value(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return result }
                    else -> throw JsonException("expected ',' or '}' at $i")
                }
            }
        }

        private fun arr(depth: Int): List<Any?> {
            expect('[')
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') { i++; return result }
            while (true) {
                result += value(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return result }
                    else -> throw JsonException("expected ',' or ']' at $i")
                }
            }
        }

        private fun str(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                val c = peek()
                i++
                when {
                    c == '"' -> return out.toString()
                    c < ' ' -> throw JsonException("control character in string at ${i - 1}")
                    c == '\\' -> {
                        val e = peek()
                        i++
                        when (e) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw JsonException("short \\u escape")
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: throw JsonException("bad \\u escape at $i")
                                out.append(code.toChar())
                                i += 4
                            }
                            else -> throw JsonException("bad escape at ${i - 1}")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val text = s.substring(start, i)
            if (text.isEmpty()) throw JsonException("unexpected character at $start")
            return text.toLongOrNull() ?: text.toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw JsonException("bad number at $start")
        }
    }

    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> string(out, value)
            is Boolean, is Int, is Long -> out.append(value.toString())
            is Number -> out.append(if (value.toDouble().isFinite()) value.toString() else "null")
            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) out.append(',')
                    string(out, k.toString()); out.append(':'); append(out, v)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                value.forEachIndexed { i, v -> if (i > 0) out.append(','); append(out, v) }
                out.append(']')
            }
            else -> string(out, value.toString())
        }
    }

    private fun string(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) when {
            c == '"' -> out.append("\\\"")
            c == '\\' -> out.append("\\\\")
            c == '\n' -> out.append("\\n")
            c == '\r' -> out.append("\\r")
            c == '\t' -> out.append("\\t")
            c < ' ' -> out.append("\\u%04x".format(c.code))
            else -> out.append(c)
        }
        out.append('"')
    }
}
