package com.moviecatalog.people

import com.moviecatalog.support.GrpcErrors
import io.grpc.StatusRuntimeException
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** BRD section 7: each person rule has a passing and a failing case (NFR-TEST-01). */
class PersonValidatorTest {
    private val today = LocalDate.of(2026, 10, 22)

    private fun keys(block: () -> Unit): List<Pair<String, String>> {
        val e = assertFailsWith<StatusRuntimeException> { block() }
        return GrpcErrors.detailOf(e)!!.violationsList.map { it.field to it.messageKey }
    }

    private fun valid(name: String = "Ada", date: LocalDate? = null, malformed: Boolean = false, bio: String = "") =
        PersonValidator.validate(name, date, malformed, bio, today)

    @Test
    fun `name is trimmed and may be 1 to 200 characters`() {
        assertEquals("Ada", valid(name = "  Ada  ").name)
        assertEquals(200, valid(name = "a".repeat(200)).name.length)
    }

    @Test
    fun `blank or too long names are rejected`() {
        assertEquals(listOf("name" to "person.name.invalid"), keys { valid(name = "") })
        assertEquals(listOf("name" to "person.name.invalid"), keys { valid(name = "   ") })
        assertEquals(listOf("name" to "person.name.invalid"), keys { valid(name = "a".repeat(201)) })
    }

    @Test
    fun `birth date boundaries`() {
        valid(date = LocalDate.of(1850, 1, 1))
        valid(date = today)
        valid(date = null)
        assertEquals(listOf("birthDate" to "person.birthdate.invalid"), keys { valid(date = LocalDate.of(1849, 12, 31)) })
        assertEquals(listOf("birthDate" to "person.birthdate.invalid"), keys { valid(date = today.plusDays(1)) })
        assertEquals(listOf("birthDate" to "person.birthdate.invalid"), keys { valid(malformed = true) })
    }

    @Test
    fun `bio limit is 2000 characters`() {
        valid(bio = "b".repeat(2000))
        assertEquals(listOf("bio" to "person.bio.too_long"), keys { valid(bio = "b".repeat(2001)) })
    }

    @Test
    fun `all violations are reported together`() {
        assertEquals(
            listOf("name" to "person.name.invalid", "birthDate" to "person.birthdate.invalid", "bio" to "person.bio.too_long"),
            keys { valid(name = "", date = today.plusDays(1), bio = "b".repeat(2001)) },
        )
    }
}
