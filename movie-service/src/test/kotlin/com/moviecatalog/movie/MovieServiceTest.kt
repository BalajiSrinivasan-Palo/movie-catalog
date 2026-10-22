package com.moviecatalog.movie

import com.moviecatalog.support.GrpcErrors
import com.moviecatalog.support.PageSpec
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MovieServiceTest {
    @Autowired lateinit var movies: MovieService
    @Autowired lateinit var credits: CreditService
    @Autowired lateinit var movieRepository: MovieRepository
    @Autowired lateinit var creditRepository: CreditRepository

    @BeforeTest
    fun reset() {
        creditRepository.deleteAll()
        movieRepository.deleteAll()
    }

    private fun f(title: String, year: Int = 2000, genres: List<Genre> = emptyList(), runtime: Int? = null) =
        MovieFields(title, year, runtime, "", genres)

    private fun page(size: Int = 20, index: Int = 0) = PageSpec(index, size)
    private fun code(block: () -> Unit) = assertFailsWith<StatusRuntimeException> { block() }.status.code
    private fun key(block: () -> Unit) = GrpcErrors.detailOf(assertFailsWith<StatusRuntimeException> { block() })!!.code

    // ---- search ----

    private fun found(query: String) = movies.search(query, page()).items.map { it.movie.title }

    @Test
    fun `search matches title synopsis and genre names, ignoring case`() {
        movies.create(MovieFields("Inception", 2010, null, "A thief enters dreams", listOf(Genre.SCI_FI)))
        movies.create(MovieFields("Heat", 1995, null, "Cops and robbers", listOf(Genre.CRIME)))
        movies.create(MovieFields("Dreamers", 2003, null, "", listOf(Genre.DRAMA)))
        assertEquals(listOf("Inception"), found("CEPT"))
        assertEquals(listOf("Dreamers", "Inception"), found("dream"))
        assertEquals(listOf("Heat"), found("robbers"))
        assertEquals(listOf("Inception"), found("sci-fi"))
        assertEquals(listOf("Heat"), found("crim"))
    }

    @Test
    fun `an underscore is literal so sci_fi does not match the Sci-Fi genre`() {
        movies.create(MovieFields("Inception", 2010, null, "", listOf(Genre.SCI_FI)))
        assertEquals(emptyList(), found("sci_fi"))
    }

    @Test
    fun `percent underscore and backslash match literally in title and synopsis`() {
        movies.create(MovieFields("100% Wolf", 2001, null, "", emptyList()))
        movies.create(MovieFields("1000 Wolves", 2002, null, "", emptyList()))
        movies.create(MovieFields("Snake_Case", 2003, null, "", emptyList()))
        movies.create(MovieFields("Snakexcase", 2004, null, "", emptyList()))
        movies.create(MovieFields("Back", 2005, null, "a\\b path", emptyList()))
        assertEquals(listOf("100% Wolf"), found("0% w"))
        assertEquals(listOf("Snake_Case"), found("e_c"))
        assertEquals(listOf("Back"), found("a\\b"))
        assertEquals(emptyList(), found("%%"))
    }

    @Test
    fun `search resists injection strings and handles quotes and accents`() {
        movies.create(MovieFields("Amélie", 2001, null, "L'amour", emptyList()))
        listOf("'; DROP TABLE movie; --", "\" OR 1=1 --", "x' OR 'x'='x").forEach { assertEquals(emptyList(), found(it)) }
        assertEquals(listOf("Amélie"), found("amélie"))
        assertEquals(listOf("Amélie"), found("l'am"))
        assertEquals(1, movieRepository.count())
    }

    @Test
    fun `search enforces the query length after trimming`() {
        movies.create(f("Ab"))
        assertEquals(listOf("Ab"), found("  ab  "))
        listOf("", " ", "a", " a ", "q".repeat(101)).forEach {
            assertEquals("search.query.invalid", GrpcErrors.detailOf(assertFailsWith<StatusRuntimeException> { movies.search(it, page()) })!!.violationsList.single().messageKey)
        }
        assertEquals(0, movies.search("q".repeat(100), page()).total)
    }

    @Test
    fun `search results are paged by title with the true total`() {
        listOf("Film C", "Film A", "Film B").forEach { movies.create(f(it, 2000)) }
        val first = movies.search("film", page(size = 2))
        assertEquals(listOf("Film A", "Film B"), first.items.map { it.movie.title })
        assertEquals(3, first.total)
        assertEquals(listOf("Film C"), movies.search("film", page(size = 2, index = 1)).items.map { it.movie.title })
    }

    // ---- movies ----

    @Test
    fun `create then get returns the movie with genres and a zero credit count`() {
        val created = movies.create(MovieFields("  Heat ", 1995, 170, "Cops and robbers", listOf(Genre.CRIME, Genre.THRILLER)))
        val loaded = movies.get(created.movie.id)
        assertEquals("Heat", loaded.movie.title)
        assertEquals(170, loaded.movie.runtimeMinutes)
        assertEquals(setOf(Genre.CRIME, Genre.THRILLER), loaded.movie.genres)
        assertEquals(0, loaded.creditCount)
    }

    @Test
    fun `the same title and year is a duplicate regardless of case and spacing, another year is fine`() {
        movies.create(f("Heat", 1995))
        assertEquals("movie.duplicate", key { movies.create(f("  HEAT ", 1995)) })
        assertEquals(Status.Code.ALREADY_EXISTS, code { movies.create(f("heat", 1995)) })
        movies.create(f("Heat", 1996))
        assertEquals(2, movieRepository.count())
    }

    @Test
    fun `update replaces all fields and may keep its own title and year`() {
        val m = movies.create(f("Old", 1990, listOf(Genre.DRAMA), 100))
        val updated = movies.update(m.movie.id, MovieFields("Old", 1990, null, "new synopsis", listOf(Genre.COMEDY)))
        assertEquals(setOf(Genre.COMEDY), updated.movie.genres)
        assertEquals(null, updated.movie.runtimeMinutes)
        assertEquals("new synopsis", movies.get(m.movie.id).movie.synopsis)
    }

    @Test
    fun `update into another movie's title and year is a conflict`() {
        movies.create(f("A", 2000))
        val b = movies.create(f("B", 2000))
        assertEquals("movie.duplicate", key { movies.update(b.movie.id, f("a", 2000)) })
    }

    @Test
    fun `get update and delete of an unknown id are NOT_FOUND`() {
        val unknown = UUID.randomUUID()
        assertEquals(Status.Code.NOT_FOUND, code { movies.get(unknown) })
        assertEquals(Status.Code.NOT_FOUND, code { movies.update(unknown, f("X")) })
        assertEquals(Status.Code.NOT_FOUND, code { movies.delete(unknown) })
    }

    @Test
    fun `delete removes the movie and its credits`() {
        val m = movies.create(f("Doomed"))
        credits.add(m.movie.id, UUID.randomUUID(), CreditRole.ACTOR, "Hero")
        movies.delete(m.movie.id)
        assertEquals(0, movieRepository.count())
        assertEquals(0, creditRepository.count())
    }

    @Test
    fun `list sorts by title or year, pages, and reports the total`() {
        movies.create(f("Charlie", 2001)); movies.create(f("alpha", 2003)); movies.create(f("Bravo", 2002))
        val byTitle = movies.list(null, null, null, MovieSort.TITLE, false, page())
        assertEquals(listOf("alpha", "Bravo", "Charlie"), byTitle.items.map { it.movie.title })
        assertEquals(listOf(2003, 2002, 2001), movies.list(null, null, null, MovieSort.RELEASE_YEAR, true, page()).items.map { it.movie.releaseYear })
        val second = movies.list(null, null, null, MovieSort.TITLE, false, page(size = 2, index = 1))
        assertEquals(listOf("Charlie"), second.items.map { it.movie.title })
        assertEquals(3, second.total)
        assertEquals(0, movies.list(null, null, null, MovieSort.TITLE, false, page(index = 9)).items.size)
    }

    @Test
    fun `list filters by genre and year range`() {
        movies.create(f("A", 1990, listOf(Genre.ACTION)))
        movies.create(f("B", 2000, listOf(Genre.ACTION, Genre.COMEDY)))
        movies.create(f("C", 2010, listOf(Genre.COMEDY)))
        fun titles(g: Genre?, from: Int?, to: Int?) = movies.list(g, from, to, MovieSort.TITLE, false, page()).items.map { it.movie.title }
        assertEquals(listOf("A", "B"), titles(Genre.ACTION, null, null))
        assertEquals(listOf("B", "C"), titles(Genre.COMEDY, null, null))
        assertEquals(listOf("B"), titles(null, 2000, 2000))
        assertEquals(listOf("B", "C"), titles(null, 1995, null))
        assertEquals(listOf("B"), titles(Genre.ACTION, 1995, 2005))
        assertEquals(emptyList(), titles(Genre.WAR, null, null))
        assertEquals(Status.Code.INVALID_ARGUMENT, code { titles(null, 2005, 2000) })
    }

    @Test
    fun `S-702 AC8 list with several genres matches any of them, once each, with the right total`() {
        movies.create(f("A", 1990, listOf(Genre.ACTION)))
        movies.create(f("B", 2000, listOf(Genre.COMEDY, Genre.CRIME))) // has both selected genres
        movies.create(f("C", 2010, listOf(Genre.CRIME)))
        movies.create(f("D", 2020, listOf(Genre.WAR)))
        fun list(vararg genres: Genre, single: Genre? = null, from: Int? = null, size: Int = 20) =
            movies.list(single, from, null, MovieSort.TITLE, false, page(size = size), genres = genres.toList())

        val any = list(Genre.COMEDY, Genre.CRIME)
        assertEquals(listOf("B", "C"), any.items.map { it.movie.title }) // B once, not twice
        assertEquals(2, any.total)
        assertEquals(listOf("A", "B", "C", "D"), list().items.map { it.movie.title }) // empty list = no filter
        assertEquals(listOf("C"), list(Genre.COMEDY, Genre.CRIME, from = 2005).items.map { it.movie.title })
        assertEquals(listOf("A", "B", "C"), list(Genre.CRIME, single = Genre.ACTION).items.map { it.movie.title })
        val firstPage = list(Genre.ACTION, Genre.COMEDY, Genre.CRIME, size = 2)
        assertEquals(listOf("A", "B"), firstPage.items.map { it.movie.title })
        assertEquals(3, firstPage.total)
    }

    @Test
    fun `S-702 AC9 list rejects duplicate or unknown genres with a keyed field error`() {
        fun violations(genres: List<Genre?>): List<Pair<String, String>> {
            val e = assertFailsWith<StatusRuntimeException> {
                movies.list(null, null, null, MovieSort.TITLE, false, page(), genres = genres)
            }
            assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
            return GrpcErrors.detailOf(e)!!.violationsList.map { it.field to it.messageKey }
        }
        val bad = listOf("genres" to "movie.genres.invalid")
        assertEquals(bad, violations(listOf(Genre.COMEDY, Genre.COMEDY)))
        assertEquals(bad, violations(listOf(null)))
    }

    @Test
    fun `list reports credit counts in one pass`() {
        val a = movies.create(f("A")); val b = movies.create(f("B"))
        credits.add(a.movie.id, UUID.randomUUID(), CreditRole.ACTOR, null)
        credits.add(a.movie.id, UUID.randomUUID(), CreditRole.DIRECTOR, null)
        val counts = movies.list(null, null, null, MovieSort.TITLE, false, page()).items.associate { it.movie.title to it.creditCount }
        assertEquals(mapOf("A" to 2L, "B" to 0L), counts)
        assertEquals(0, movies.get(b.movie.id).creditCount)
    }

    // ---- credits ----

    @Test
    fun `a person may hold several roles in one movie but not the same role twice`() {
        val m = movies.create(f("Film")).movie.id
        val p = UUID.randomUUID()
        credits.add(m, p, CreditRole.ACTOR, "Lead")
        credits.add(m, p, CreditRole.DIRECTOR, null)
        assertEquals("credit.duplicate", key { credits.add(m, p, CreditRole.ACTOR, "Other") })
        assertEquals(2, credits.forMovie(m, 0).total)
    }

    @Test
    fun `adding a credit to an unknown movie is NOT_FOUND and bad input is rejected first`() {
        assertEquals(Status.Code.NOT_FOUND, code { credits.add(UUID.randomUUID(), UUID.randomUUID(), CreditRole.ACTOR, null) })
        val m = movies.create(f("Film")).movie.id
        assertEquals(Status.Code.INVALID_ARGUMENT, code { credits.add(m, UUID.randomUUID(), null, null) })
        assertEquals(Status.Code.INVALID_ARGUMENT, code { credits.add(m, UUID.randomUUID(), CreditRole.WRITER, "Nope") })
    }

    @Test
    fun `update changes role and character and keeps uniqueness`() {
        val m = movies.create(f("Film")).movie.id
        val p = UUID.randomUUID()
        val actor = credits.add(m, p, CreditRole.ACTOR, "Lead")
        credits.add(m, p, CreditRole.WRITER, null)
        assertEquals("credit.duplicate", key { credits.update(actor.id, CreditRole.WRITER, null) })
        val changed = credits.update(actor.id, CreditRole.ACTOR, "Renamed")
        assertEquals("Renamed", changed.characterName)
        assertEquals(Status.Code.NOT_FOUND, code { credits.update(UUID.randomUUID(), CreditRole.ACTOR, null) })
    }

    @Test
    fun `remove deletes one credit and unknown ids are NOT_FOUND`() {
        val m = movies.create(f("Film")).movie.id
        val c = credits.add(m, UUID.randomUUID(), CreditRole.PRODUCER, null)
        credits.remove(c.id)
        assertEquals(0, creditRepository.count())
        assertEquals(Status.Code.NOT_FOUND, code { credits.remove(c.id) })
    }

    @Test
    fun `credits for a movie are capped with a truncated flag`() {
        val m = movies.create(f("Big")).movie.id
        repeat(5) { credits.add(m, UUID.randomUUID(), CreditRole.ACTOR, null) }
        val capped = credits.forMovie(m, 3)
        assertEquals(3, capped.credits.size)
        assertEquals(5, capped.total)
        assertTrue(capped.truncated)
        assertTrue(!credits.forMovie(m, 0).truncated)
        assertEquals(Status.Code.INVALID_ARGUMENT, code { credits.forMovie(m, 201) })
        assertEquals(Status.Code.NOT_FOUND, code { credits.forMovie(UUID.randomUUID(), 10) })
    }

    @Test
    fun `credits for a movie come in the documented role order, not alphabetical (S-402 AC5)`() {
        val m = movies.create(f("Ordered")).movie.id
        credits.add(m, UUID.randomUUID(), CreditRole.PRODUCER, null)
        credits.add(m, UUID.randomUUID(), CreditRole.WRITER, null)
        credits.add(m, UUID.randomUUID(), CreditRole.DIRECTOR, null)
        credits.add(m, UUID.randomUUID(), CreditRole.ACTOR, null)
        assertEquals(
            listOf(CreditRole.ACTOR, CreditRole.DIRECTOR, CreditRole.WRITER, CreditRole.PRODUCER),
            credits.forMovie(m, 0).credits.map { it.role },
        )
    }

    @Test
    fun `filmography lists a person's credits newest movie first and pages`() {
        val p = UUID.randomUUID()
        val old = movies.create(f("Old", 1980)).movie.id
        val new = movies.create(f("New", 2020)).movie.id
        credits.add(old, p, CreditRole.ACTOR, null)
        credits.add(new, p, CreditRole.DIRECTOR, null)
        credits.add(new, UUID.randomUUID(), CreditRole.ACTOR, null) // someone else's credit
        val all = credits.filmography(p, page())
        assertEquals(listOf("New", "Old"), all.items.map { it.movie.title })
        assertEquals(2, all.total)
        assertEquals(listOf("Old"), credits.filmography(p, page(size = 1, index = 1)).items.map { it.movie.title })
        assertEquals(0, credits.filmography(UUID.randomUUID(), page()).total)
    }

    @Test
    fun `check person reports the count and up to the limit of distinct blocking movies`() {
        val p = UUID.randomUUID()
        assertEquals(0, credits.checkPerson(p, 10).count)
        val a = movies.create(f("A")).movie.id
        val b = movies.create(f("B")).movie.id
        credits.add(a, p, CreditRole.ACTOR, null); credits.add(a, p, CreditRole.WRITER, null); credits.add(b, p, CreditRole.ACTOR, null)
        val all = credits.checkPerson(p, 10)
        assertEquals(3, all.count)
        assertEquals(listOf("A", "B"), all.blockingMovies.map { it.title })
        assertEquals(1, credits.checkPerson(p, 1).blockingMovies.size)
        assertEquals(Status.Code.INVALID_ARGUMENT, code { credits.checkPerson(p, 11) })
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val dir = Files.createTempDirectory("movie-service-test")
            registry.add("MOVIE_DB_PATH") { dir.resolve("db").toString() }
            registry.add("grpc.server.port") { "0" }
            registry.add("ARTWORK_DIR") { dir.resolve("artwork").toString() }
        }
    }
}
