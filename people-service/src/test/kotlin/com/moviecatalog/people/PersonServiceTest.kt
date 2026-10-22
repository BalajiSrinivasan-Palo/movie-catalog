package com.moviecatalog.people

import com.moviecatalog.contracts.common.v1.BlockingMovie
import com.moviecatalog.support.GrpcErrors
import com.moviecatalog.support.PageSpec
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.time.LocalDate
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A controllable stand-in for movie-service, so deletion rules can be tested without a second process. */
class FakeMovieCredits : MovieCredits {
    var result: () -> CreditCheck = { CreditCheck(0, emptyList()) }
    override fun check(personId: UUID): CreditCheck = result()
}

@TestConfiguration
class FakeMovieCreditsConfig {
    @Bean @Primary
    fun fakeMovieCredits() = FakeMovieCredits()
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@org.springframework.context.annotation.Import(FakeMovieCreditsConfig::class)
class PersonServiceTest {
    @Autowired lateinit var service: PersonService
    @Autowired lateinit var repository: PersonRepository
    @Autowired lateinit var fake: FakeMovieCredits
    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeTest
    fun reset() {
        repository.deleteAll()
        fake.result = { CreditCheck(0, emptyList()) }
    }

    private fun create(name: String) = service.create(name, null, false, "")
    private fun page(size: Int = 20, index: Int = 0) = PageSpec(index, size)

    @Test
    fun `create stores a trimmed person and get returns it`() {
        val created = service.create("  Grace Hopper ", LocalDate.of(1906, 12, 9), false, "Pioneer")
        val loaded = service.get(created.id)
        assertEquals("Grace Hopper", loaded.name)
        assertEquals(LocalDate.of(1906, 12, 9), loaded.birthDate)
        assertEquals("Pioneer", loaded.bio)
        assertNotNull(loaded.createdAt)
    }

    @Test
    fun `birth dates before 1905 are stored and read back unchanged, whatever the JVM time zone`() {
        // Regression 2026-10-22: LocalDate went through java.sql.Date and the JVM zone; Asia/Singapore used
        // local mean time (+06:55:25) before 1905, so 1894-09-20 was stored as 1894-09-19. The test JVM runs
        // in Asia/Singapore (build.gradle.kts) so this fails anywhere the bug is present.
        val dates = listOf(LocalDate.of(1894, 9, 20), LocalDate.of(1883, 4, 1), LocalDate.of(1868, 5, 6), LocalDate.of(1904, 1, 18))
        for (date in dates) {
            val id = service.create("Born $date", date, false, "").id
            assertEquals(date.toString(), jdbc.queryForObject("select cast(birth_date as varchar) from person where id = ?", String::class.java, id))
            assertEquals(date, service.get(id).birthDate)
        }
    }

    @Test
    fun `same name is allowed for different people`() {
        create("John Smith"); create("John Smith")
        assertEquals(2, service.list(null, false, page()).total)
    }

    @Test
    fun `get and update of an unknown id are NOT_FOUND`() {
        val unknown = UUID.randomUUID()
        assertEquals(Status.Code.NOT_FOUND, assertFailsWith<StatusRuntimeException> { service.get(unknown) }.status.code)
        assertEquals(
            Status.Code.NOT_FOUND,
            assertFailsWith<StatusRuntimeException> { service.update(unknown, "X", null, false, "") }.status.code,
        )
    }

    @Test
    fun `update replaces all fields and changes the sort key`() {
        val p = service.create("Zed", LocalDate.of(1980, 1, 1), false, "old")
        val updated = service.update(p.id, "Alan", null, false, "")
        assertEquals("alan", updated.nameKey)
        assertEquals(null, updated.birthDate)
        assertEquals("", updated.bio)
        assertTrue(!updated.updatedAt.isBefore(p.createdAt))
    }

    @Test
    fun `list sorts by name both ways and pages with a correct total`() {
        listOf("Charlie", "alice", "Bob").forEach(::create)
        assertEquals(listOf("alice", "Bob", "Charlie"), service.list(null, false, page()).items.map { it.name })
        assertEquals(listOf("Charlie", "Bob", "alice"), service.list(null, true, page()).items.map { it.name })
        val second = service.list(null, false, page(size = 2, index = 1))
        assertEquals(listOf("Charlie"), second.items.map { it.name })
        assertEquals(3, second.total)
        assertEquals(emptyList(), service.list(null, false, page(size = 2, index = 5)).items.map { it.name })
    }

    @Test
    fun `name filter is a case-insensitive substring and wildcards are literal`() {
        listOf("Ann Lee", "100% Real", "a_b", "axb").forEach(::create)
        assertEquals(listOf("Ann Lee"), service.list("LEE", false, page()).items.map { it.name })
        assertEquals(listOf("100% Real"), service.list("%", false, page()).items.map { it.name })
        assertEquals(listOf("a_b"), service.list("_", false, page()).items.map { it.name })
        assertEquals(emptyList(), service.list("\\", false, page()).items.map { it.name })
    }

    @Test
    fun `search enforces the query length and handles quotes`() {
        create("O'Brien")
        assertEquals(1, service.search("o'b", page()).total)
        listOf("x", " x ", "", "   ", "q".repeat(101)).forEach {
            val e = assertFailsWith<StatusRuntimeException> { service.search(it, page()) }
            assertEquals("search.query.invalid", GrpcErrors.detailOf(e)!!.violationsList.single().messageKey)
        }
        assertEquals(0, service.search("q".repeat(100), page()).total)
    }

    @Test
    fun `search matches the name or the bio, ignoring case, but the list filter stays name-only`() {
        service.create("Christopher Nolan", null, false, "British-American director")
        service.create("Ada", null, false, "Worked with Nolan on notes")
        service.create("Zed", null, false, "")
        assertEquals(listOf("Ada", "Christopher Nolan"), service.search("NOL", page()).items.map { it.name })
        assertEquals(listOf("Christopher Nolan"), service.search("director", page()).items.map { it.name })
        assertEquals(listOf("Christopher Nolan"), service.list("nol", false, page()).items.map { it.name })
    }

    @Test
    fun `search treats percent underscore and backslash literally in name and bio`() {
        service.create("Plain", null, false, "gets 100% credit")
        service.create("Other", null, false, "gets 1000 credit")
        service.create("Under", null, false, "snake_case")
        service.create("Slash", null, false, "path a\\b")
        assertEquals(listOf("Plain"), service.search("0% c", page()).items.map { it.name })
        assertEquals(listOf("Under"), service.search("e_c", page()).items.map { it.name })
        assertEquals(listOf("Slash"), service.search("a\\b", page()).items.map { it.name })
        assertEquals(0, service.search("%%", page()).total)
    }

    @Test
    fun `search resists injection strings and matches accents`() {
        service.create("Amélie", null, false, "")
        listOf("'; DROP TABLE person; --", "\" OR 1=1 --", "a' OR 'a'='a").forEach {
            assertEquals(0, service.search(it, page()).total)
        }
        assertEquals(1, service.search("amélie", page()).total)
        assertEquals(1, service.search("AMÉL", page()).total)
    }

    @Test
    fun `batch get skips unknown ids`() {
        val a = create("A One"); val b = create("B Two")
        val result = service.batchGet(listOf(a.id, UUID.randomUUID(), b.id))
        assertEquals(setOf(a.id, b.id), result.map { it.id }.toSet())
        assertEquals(emptyList(), service.batchGet(emptyList()))
    }

    @Test
    fun `delete removes a person without credits`() {
        val p = create("Gone")
        service.delete(p.id)
        assertEquals(0, repository.count())
    }

    @Test
    fun `delete is blocked when the person has credits and lists the movies`() {
        val p = create("Busy")
        val movie = BlockingMovie.newBuilder().setId(UUID.randomUUID().toString()).setTitle("Heat").setReleaseYear(1995).build()
        fake.result = { CreditCheck(2, listOf(movie)) }
        val e = assertFailsWith<StatusRuntimeException> { service.delete(p.id) }
        assertEquals(Status.Code.FAILED_PRECONDITION, e.status.code)
        val detail = GrpcErrors.detailOf(e)!!
        assertEquals("person.has_credits", detail.code)
        assertEquals("2", detail.metadataMap["creditCount"])
        assertEquals("Heat", detail.blockingMoviesList.single().title)
        assertEquals(1, repository.count(), "person must still exist")
    }

    @Test
    fun `delete fails closed when the credit check is unavailable`() {
        val p = create("Safe")
        fake.result = { throw CreditCheckUnavailable(RuntimeException("down")) }
        val e = assertFailsWith<StatusRuntimeException> { service.delete(p.id) }
        assertEquals(Status.Code.UNAVAILABLE, e.status.code)
        assertEquals(1, repository.count())
    }

    @Test
    fun `delete of an unknown id is NOT_FOUND`() {
        assertEquals(Status.Code.NOT_FOUND, assertFailsWith<StatusRuntimeException> { service.delete(UUID.randomUUID()) }.status.code)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val dir = Files.createTempDirectory("people-service-test")
            registry.add("PEOPLE_DB_PATH") { dir.resolve("db").toString() }
            registry.add("grpc.server.port") { "0" }
        }
    }
}
