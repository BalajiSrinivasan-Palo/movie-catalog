package com.moviecatalog.gateway

import com.moviecatalog.contracts.common.v1.BlockingMovie
import com.moviecatalog.support.GrpcErrors
import io.grpc.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S-004 AC3 and AC7: one case per row of the ADR-005 table. */
class GrpcErrorMapperTest {
    private fun code(t: Throwable) = GrpcErrorMapper.map(t)?.code

    @Test
    fun `status to GraphQL code table`() {
        val table = listOf(
            GrpcErrors.invalidArgument("title" to "movie.title.invalid") to "VALIDATION_FAILED",
            GrpcErrors.notFound("movie.not_found") to "NOT_FOUND",
            GrpcErrors.alreadyExists("movie.duplicate") to "CONFLICT",
            GrpcErrors.failedPrecondition("person.has_credits") to "CONFLICT",
            Status.UNAVAILABLE.asRuntimeException() to "UNAVAILABLE",
            Status.DEADLINE_EXCEEDED.asRuntimeException() to "UNAVAILABLE",
            Status.INTERNAL.asRuntimeException() to "INTERNAL",
            Status.UNKNOWN.asRuntimeException() to "INTERNAL",
            Status.RESOURCE_EXHAUSTED.asRuntimeException() to "INTERNAL",
            IllegalStateException("boom") to "INTERNAL",
        )
        table.forEach { (error, expected) -> assertEquals(expected, code(error), error.toString()) }
    }

    @Test
    fun `cancelled calls are not reported`() {
        assertNull(GrpcErrorMapper.map(Status.CANCELLED.asRuntimeException()))
    }

    @Test
    fun `validation errors carry field errors and the message key`() {
        val mapped = GrpcErrorMapper.map(GrpcErrors.invalidArgument("title" to "movie.title.invalid"))!!
        assertEquals(listOf(mapOf("field" to "title", "messageKey" to "movie.title.invalid")), mapped.fieldErrors)
        assertEquals("validation.failed", mapped.messageKey)
    }

    @Test
    fun `conflicts expose the stable key and blocking movies`() {
        val movie = BlockingMovie.newBuilder().setId("m1").setTitle("Heat").setReleaseYear(1995).build()
        val mapped = GrpcErrorMapper.map(GrpcErrors.failedPrecondition("person.has_credits", listOf(movie)))!!
        assertEquals("person.has_credits", mapped.messageKey)
        assertEquals(mapOf("id" to "m1", "title" to "Heat", "releaseYear" to 1995), mapped.blockingMovies.single())
        assertEquals("movie.duplicate", GrpcErrorMapper.map(GrpcErrors.alreadyExists("movie.duplicate"))!!.messageKey)
    }

    @Test
    fun `gateway generated field errors map to VALIDATION_FAILED`() {
        val mapped = GrpcErrorMapper.map(GatewayValidationException("personId", "credit.person.not_found"))!!
        assertEquals("VALIDATION_FAILED", mapped.code)
        assertEquals(listOf(mapOf("field" to "personId", "messageKey" to "credit.person.not_found")), mapped.fieldErrors)
    }

    @Test
    fun `a malformed id is a validation error with key id_invalid`() {
        val e = assertFailsWith<GatewayValidationException> { parseId("not-a-uuid") }
        assertEquals(listOf("id" to "id.invalid"), e.violations)
        parseId("3f2c1a9e-7b44-4d1e-9a10-0c8d6e5f4a3b")
    }

    @Test
    fun `messages never contain internal text`() {
        val secret = IllegalStateException("jdbc:h2:file:/data/movie-db password=hunter2 at com.moviecatalog.Foo")
        val mapped = GrpcErrorMapper.map(secret)!!
        assertEquals("Something went wrong. Please try again.", mapped.message)
        assertTrue(listOf("jdbc", "hunter2", "moviecatalog").none { mapped.message.contains(it) })
        val status = Status.INTERNAL.withDescription("SQL error near 'DROP'").asRuntimeException()
        assertTrue(!GrpcErrorMapper.map(status)!!.message.contains("SQL"))
    }

    @Test
    fun `an unavailable error from a service passes its message key through, a transport failure has none`() {
        val specific = GrpcErrorMapper.map(GrpcErrors.unavailable("person.delete.check_unavailable"))!!
        assertEquals("UNAVAILABLE", specific.code)
        assertEquals("person.delete.check_unavailable", specific.messageKey)
        assertNull(GrpcErrorMapper.map(Status.UNAVAILABLE.asRuntimeException())!!.messageKey)
    }
}
