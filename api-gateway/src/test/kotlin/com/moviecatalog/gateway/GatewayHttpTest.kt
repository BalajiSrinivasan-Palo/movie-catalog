package com.moviecatalog.gateway

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S-004 AC1, AC2, AC5, AC8 over real HTTP: nothing listens on the gRPC ports, so both services report DOWN. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "catalog.frontend-origin=http://localhost:5173",
        "downstream.movie.target=127.0.0.1:1",
        "downstream.people.target=127.0.0.1:1",
    ],
)
class GatewayHttpTest {
    @Value("\${local.server.port}") var port: Int = 0
    private val http = HttpClient.newHttpClient()

    private fun post(query: String, vararg headers: Pair<String, String>): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/graphql"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"query":"$query"}"""))
        headers.forEach { (k, v) -> builder.header(k, v) }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun preflight(origin: String): HttpResponse<String> = http.send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/graphql"))
            .header("Origin", origin).header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    @Test
    fun `health query reports both backends as DOWN when they are stopped`() {
        val response = post("{ health { service status } }")
        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("\"movie-service\""), response.body())
        assertTrue(response.body().contains("\"DOWN\""), response.body())
        assertTrue(!response.body().contains("errors"), response.body())
    }

    @Test
    fun `correlation id is echoed when valid and replaced when not`() {
        val good = post("{ health { service } }", "X-Correlation-Id" to "11111111-2222-3333-4444-555555555555")
        assertEquals("11111111-2222-3333-4444-555555555555", good.headers().firstValue("X-Correlation-Id").get())
        val bad = post("{ health { service } }", "X-Correlation-Id" to "bad id")
        val replaced = bad.headers().firstValue("X-Correlation-Id").get()
        assertTrue(replaced != "bad id" && replaced.length >= 8)
    }

    @Test
    fun `security headers are set`() {
        val headers = post("{ health { service } }").headers()
        assertEquals("nosniff", headers.firstValue("X-Content-Type-Options").get())
        assertEquals("DENY", headers.firstValue("X-Frame-Options").get())
    }

    @Test
    fun `CORS allows only the configured origin`() {
        val allowed = preflight("http://localhost:5173")
        assertEquals("http://localhost:5173", allowed.headers().firstValue("Access-Control-Allow-Origin").get())
        val denied = preflight("http://evil.example")
        assertEquals(403, denied.statusCode())
        assertTrue(denied.headers().firstValue("Access-Control-Allow-Origin").isEmpty)
    }

    @Test
    fun `overly deep queries are rejected`() {
        val deep = "{ __schema { types { fields { type { ofType { ofType { ofType { ofType { ofType { ofType { name } } } } } } } } } } }"
        val response = post(deep)
        assertTrue(response.body().contains("errors"), response.body())
        assertTrue(response.body().lowercase().contains("depth"), response.body())
    }

    @Test
    fun `introspection works in the dev profile`() {
        assertTrue(post("{ __schema { queryType { name } } }").body().contains("Query"))
    }
}

/** S-004 AC5: outside the dev profile introspection is off. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.profiles.active=prod", "downstream.movie.target=127.0.0.1:1", "downstream.people.target=127.0.0.1:1"],
)
class IntrospectionOffTest {
    @Value("\${local.server.port}") var port: Int = 0

    @Test
    fun `introspection is rejected`() {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/graphql"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"query":"{ __schema { queryType { name } } }"}""")).build()
        val body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body()
        assertTrue(!body.contains("\"queryType\""), body)
    }
}
